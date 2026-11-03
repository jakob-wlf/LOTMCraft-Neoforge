package de.jakob.lotm.gui.custom.concealed_bundle;

import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.gui.ModMenuTypes;
import de.jakob.lotm.item.custom.ConcealedBundleItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import javax.annotation.Nullable;

public class ConcealedBundleMenu extends AbstractContainerMenu {

    public static final int COLS = 9;
    public static final int PAGE_ROWS = 6;
    public static final int PAGE_SIZE = COLS * PAGE_ROWS;

    private static final int OFFHAND_INDEX = 40;

    private final int capacity;
    private final int lockedIndex;
    private final DataSlot page = DataSlot.standalone();
    private final Inventory playerInventory;

    @Nullable private final InteractionHand hand;
    @Nullable private final ItemStack bundle;
    @Nullable private final NonNullList<ItemStack> storage;

    public ConcealedBundleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readVarInt(), buf.readEnum(InteractionHand.class), null);
    }

    public ConcealedBundleMenu(int containerId, Inventory inventory, InteractionHand hand, ItemStack bundle) {
        this(containerId, inventory, bundle.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_SIZE, 0), hand, bundle);
    }

    private ConcealedBundleMenu(int containerId, Inventory inventory, int capacity, InteractionHand hand, @Nullable ItemStack bundle) {
        super(ModMenuTypes.CONCEALED_BUNDLE_MENU.get(), containerId);
        this.capacity = capacity;
        this.playerInventory = inventory;
        this.lockedIndex = hand == InteractionHand.MAIN_HAND ? inventory.selected : OFFHAND_INDEX;
        this.bundle = bundle;
        this.hand = bundle == null ? null : hand;

        Container view;
        if (bundle != null) {
            storage = NonNullList.withSize(capacity, ItemStack.EMPTY);
            bundle.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(storage);
            view = new PageView();
        } else {
            storage = null;
            view = new SimpleContainer(PAGE_SIZE);
        }

        addDataSlot(page);

        for (int row = 0; row < PAGE_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new BundleSlot(view, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }

        int offset = (rows() - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new InventorySlot(inventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new InventorySlot(inventory, col, 8 + col * 18, 161 + offset));
        }
    }

    public int rows() {
        return Mth.clamp(Mth.positiveCeilDiv(capacity, COLS), 1, PAGE_ROWS);
    }

    public int pages() {
        return Math.max(1, Mth.positiveCeilDiv(capacity, PAGE_SIZE));
    }

    public int page() {
        return page.get();
    }

    private int activeSlots() {
        return Mth.clamp(capacity - page.get() * PAGE_SIZE, 0, PAGE_SIZE);
    }

    public static boolean allowed(ItemStack stack) {
        return stack.getItem().canFitInsideContainerItems();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int next = page.get() + (id == 0 ? -1 : 1);
        if (next < 0 || next >= pages()) return false;
        page.set(next);
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.SWAP && button == lockedIndex) return;
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (slot.container == playerInventory && slot.getContainerSlot() == lockedIndex) return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PAGE_SIZE) {
            if (!moveItemStackTo(stack, PAGE_SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!allowed(stack) || !moveItemStackTo(stack, 0, activeSlots(), false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else                 slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return bundle == null || (player.isAlive() && player.getItemInHand(hand) == bundle);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        save();
        if (bundle == null || !ConcealedBundleItem.wornOut(bundle)) return;
        bundle.setCount(0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1, 0.8f);
    }

    private void save() {
        if (bundle == null || storage == null) return;
        bundle.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(storage));
    }

    private class BundleSlot extends Slot {
        BundleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() < activeSlots();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && allowed(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
        }
    }

    private class InventorySlot extends Slot {
        InventorySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return getContainerSlot() != lockedIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return getContainerSlot() != lockedIndex;
        }
    }

    private class PageView implements Container {
        private int index(int slot) {
            return page.get() * PAGE_SIZE + slot;
        }

        @Override
        public int getContainerSize() {
            return PAGE_SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < PAGE_SIZE; i++) {
                if (!getItem(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            int index = index(slot);
            return index < capacity ? storage.get(index) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            int index = index(slot);
            if (index >= capacity) return ItemStack.EMPTY;
            ItemStack removed = ContainerHelper.removeItem(storage, index, amount);
            if (!removed.isEmpty()) setChanged();
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            int index = index(slot);
            if (index >= capacity) return ItemStack.EMPTY;
            return ContainerHelper.takeItem(storage, index);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int index = index(slot);
            if (index >= capacity) return;
            storage.set(index, stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            save();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < PAGE_SIZE; i++) {
                int index = index(i);
                if (index < capacity) storage.set(index, ItemStack.EMPTY);
            }
            setChanged();
        }
    }
}
