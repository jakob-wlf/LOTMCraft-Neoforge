package de.jakob.lotm.gui.custom.error;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class DeceitInventoryEditorScreen extends Screen {

    private static final int SLOT = 18;

    private final DeceitPrepareScreen parent;
    private final CompoundTag config;
    private final ItemStack[] inventory = new ItemStack[36];
    private final List<ItemStack> palette = new ArrayList<>();
    private ItemStack brush = ItemStack.EMPTY;

    public DeceitInventoryEditorScreen(DeceitPrepareScreen parent, CompoundTag config) {
        super(Component.translatable("gui.lotmcraft.deceit_inventory.title"));
        this.parent = parent;
        this.config = config;
    }

    private HolderLookup.Provider registries() {
        return minecraft != null && minecraft.level != null ? minecraft.level.registryAccess() : null;
    }

    @Override
    protected void init() {
        HolderLookup.Provider provider = registries();
        ListTag list = config.getList("inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < 36; i++) {
            inventory[i] = provider != null && i < list.size() ? ItemStack.parseOptional(provider, list.getCompound(i)) : ItemStack.EMPTY;
        }

        palette.clear();
        if (minecraft != null && minecraft.player != null) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (!stack.isEmpty() && palette.stream().noneMatch(p -> ItemStack.isSameItemSameComponents(p, stack))) palette.add(stack.copy());
            }
        }
        for (var item : new net.minecraft.world.item.Item[]{Items.CARROT, Items.DIRT, Items.COBBLESTONE, Items.ROTTEN_FLESH, Items.STICK, Items.BREAD,
                Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.TOTEM_OF_UNDYING, Items.ENDER_PEARL, Items.POISONOUS_POTATO}) {
            if (palette.size() >= 45) break;
            if (palette.stream().noneMatch(p -> p.is(item))) palette.add(new ItemStack(item, item.getDefaultMaxStackSize()));
        }

        int left = width / 2 - 9 * SLOT / 2;
        int bottom = height / 2 + 100;
        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_inventory.fill"), b -> {
            for (int i = 0; i < 36; i++) inventory[i] = brush.copy();
        }).bounds(left - 4, bottom, 54, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_inventory.copy_mine"), b -> {
            if (minecraft == null || minecraft.player == null) return;
            for (int i = 0; i < 36; i++) inventory[i] = minecraft.player.getInventory().items.get(i).copy();
        }).bounds(left + 54, bottom, 54, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_inventory.clear"), b -> {
            for (int i = 0; i < 36; i++) inventory[i] = ItemStack.EMPTY;
        }).bounds(left + 112, bottom, 54, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_inventory.done"), b -> onClose())
                .bounds(left - 4, bottom + 22, 9 * SLOT + 8, 18).build());
    }

    private int[] inventorySlot(int index) {
        int left = width / 2 - 9 * SLOT / 2;
        int top = height / 2 - 96;
        int row = index < 9 ? 3 : index / 9 - 1;
        int y = top + row * SLOT + (index < 9 ? 4 : 0);
        return new int[]{left + (index % 9) * SLOT, y};
    }

    private int[] paletteSlot(int index) {
        int left = width / 2 - 9 * SLOT / 2;
        int top = height / 2 - 96 + 4 * SLOT + 20;
        return new int[]{left + (index % 9) * SLOT, top + (index / 9) * SLOT};
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 112, 0xFFC9B6FF);

        ItemStack tooltip = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            int[] pos = inventorySlot(i);
            tooltip = drawSlot(graphics, pos, inventory[i], mouseX, mouseY, false, tooltip);
        }

        int[] labelPos = paletteSlot(0);
        graphics.drawString(font, Component.translatable("gui.lotmcraft.deceit_inventory.palette"), labelPos[0], labelPos[1] - 11, 0xFF9A8CC0, false);
        for (int i = 0; i < Math.min(45, palette.size()); i++) {
            tooltip = drawSlot(graphics, paletteSlot(i), palette.get(i), mouseX, mouseY, ItemStack.isSameItemSameComponents(palette.get(i), brush), tooltip);
        }

        if (!tooltip.isEmpty()) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private ItemStack drawSlot(GuiGraphics graphics, int[] pos, ItemStack stack, int mouseX, int mouseY, boolean selected, ItemStack tooltip) {
        boolean hover = mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16;
        graphics.fill(pos[0] - 1, pos[1] - 1, pos[0] + 17, pos[1] + 17, selected ? 0xFFB088FF : hover ? 0xFF5A52A8 : 0xFF2E2848);
        graphics.fill(pos[0], pos[1], pos[0] + 16, pos[1] + 16, 0xFF1A1628);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, pos[0], pos[1]);
            graphics.renderItemDecorations(font, stack, pos[0], pos[1]);
            if (hover) return stack;
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < Math.min(45, palette.size()); i++) {
            int[] pos = paletteSlot(i);
            if (mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16) {
                brush = palette.get(i).copy();
                return true;
            }
        }
        for (int i = 0; i < 36; i++) {
            int[] pos = inventorySlot(i);
            if (mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16) {
                inventory[i] = button == 1 ? ItemStack.EMPTY : brush.copy();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        HolderLookup.Provider provider = registries();
        if (provider != null) {
            ListTag list = new ListTag();
            for (ItemStack stack : inventory) list.add(stack.saveOptional(provider));
            config.put("inventory", list);
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
