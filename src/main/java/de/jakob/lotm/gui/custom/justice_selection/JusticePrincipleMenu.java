package de.jakob.lotm.gui.custom.justice_selection;

import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeData;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeManager;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticePrinciple;
import de.jakob.lotm.gui.ModMenuTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public class JusticePrincipleMenu extends AbstractContainerMenu {
    public static final int CONFIRM_BUTTON = 100;

    private final DataSlot selected = DataSlot.standalone();
    private final DataSlot unavailable = DataSlot.standalone();
    private final DataSlot lockSeconds = DataSlot.standalone();
    private final DataSlot fails = DataSlot.standalone();

    public JusticePrincipleMenu(int containerId, Inventory inventory) {
        super(ModMenuTypes.JUSTICE_PRINCIPLES.get(), containerId);
        addDataSlot(selected);
        addDataSlot(unavailable);
        addDataSlot(lockSeconds);
        addDataSlot(fails);
    }

    public JusticePrincipleMenu(int containerId, Inventory inventory, ServerPlayer player) {
        this(containerId, inventory);
        selected.set(JusticeData.mask(player));
        int blocked = 0;
        for (JusticePrinciple principle : JusticePrinciple.values()) {
            if (!JusticeManager.isAvailable(player, principle)) blocked |= 1 << principle.ordinal();
        }
        unavailable.set(blocked);
        long remaining = JusticeData.of(player).getLong(JusticeData.LOCK) - player.level().getGameTime();
        lockSeconds.set((int) Math.max(0, remaining / 20));
        fails.set(JusticeData.fails(player));
    }

    public int selectedMask() {
        return selected.get();
    }

    public boolean isSelected(JusticePrinciple principle) {
        return (selected.get() & (1 << principle.ordinal())) != 0;
    }

    public boolean isUnavailable(JusticePrinciple principle) {
        return (unavailable.get() & (1 << principle.ordinal())) != 0;
    }

    public int lockSeconds() {
        return lockSeconds.get();
    }

    public int fails() {
        return fails.get();
    }

    public int selectedCount() {
        return Integer.bitCount(selected.get());
    }

    public boolean canConfirm() {
        return lockSeconds.get() <= 0 && selectedCount() >= JusticeManager.MIN_PRINCIPLES;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (lockSeconds.get() > 0) return false;

        if (id >= 0 && id < JusticePrinciple.values().length) {
            JusticePrinciple principle = JusticePrinciple.values()[id];
            if (isUnavailable(principle) && !isSelected(principle)) return false;
            selected.set(selected.get() ^ (1 << id));
            return true;
        }

        if (id == CONFIRM_BUTTON) {
            if (!JusticeManager.swear(serverPlayer, selected.get())) return false;
            serverPlayer.sendSystemMessage(Component.translatable("justice.lotmcraft.menu.sworn").withStyle(ChatFormatting.GOLD));
            serverPlayer.closeContainer();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return JusticeManager.isMentor(player);
    }
}
