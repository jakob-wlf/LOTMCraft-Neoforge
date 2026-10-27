package de.jakob.lotm.gui.custom.justice_selection;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticePrinciple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class JusticePrincipleScreen extends AbstractContainerScreen<JusticePrincipleMenu> {
    private static final String DIR = "textures/gui/justice_principles/";
    private static final ResourceLocation BACKGROUND = tex("background.png");
    private static final ResourceLocation PLATES = tex("plates.png");
    private static final ResourceLocation ICONS = tex("icons.png");
    private static final ResourceLocation BUTTON = tex("button.png");
    private static final ResourceLocation PIPS = tex("pips.png");

    private static final int PLATE_W = 108;
    private static final int PLATE_H = 38;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 60;
    private static final int COLUMN_STEP = 112;
    private static final int ROW_STEP = 42;
    private static final int BUTTON_X = 58;
    private static final int BUTTON_Y = 232;
    private static final int BUTTON_W = 120;
    private static final int BUTTON_H = 20;

    private static final int CREAM = 0xF7ECC8;
    private static final int GOLD = 0xE0A93A;
    private static final int UMBER = 0x2B1A0C;
    private static final int ASH = 0x8C8077;

    public JusticePrincipleScreen(JusticePrincipleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 236;
        imageHeight = 256;
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, DIR + name);
    }

    private int plateX(int index) {
        return leftPos + GRID_X + (index % 2) * COLUMN_STEP;
    }

    private int plateY(int index) {
        return topPos + GRID_Y + (index / 2) * ROW_STEP;
    }

    private int plateAt(double mouseX, double mouseY) {
        for (int i = 0; i < JusticePrinciple.values().length; i++) {
            int x = plateX(i);
            int y = plateY(i);
            if (mouseX >= x && mouseX < x + PLATE_W && mouseY >= y && mouseY < y + PLATE_H) return i;
        }
        return -1;
    }

    private boolean overButton(double mouseX, double mouseY) {
        int x = leftPos + BUTTON_X;
        int y = topPos + BUTTON_Y;
        return mouseX >= x && mouseX < x + BUTTON_W && mouseY >= y && mouseY < y + BUTTON_H;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        for (int i = 0; i < JusticePrinciple.values().length; i++) {
            JusticePrinciple principle = JusticePrinciple.values()[i];
            int row;
            if (menu.isSelected(principle)) row = 40;
            else if (menu.isUnavailable(principle)) row = 80;
            else row = 0;
            int x = plateX(i);
            int y = plateY(i);
            graphics.blit(PLATES, x, y, 0, row, PLATE_W, PLATE_H, 128, 128);
            graphics.blit(ICONS, x + 4, y + 3, i * 32, 0, 32, 32, 256, 32);
        }

        for (int i = 0; i < 4; i++) {
            graphics.blit(PIPS, leftPos + 12 + i * 12, topPos + 12, i < menu.fails() ? 12 : 0, 0, 10, 10, 32, 16);
        }

        int buttonRow = !menu.canConfirm() ? 40 : (overButton(mouseX, mouseY) ? 20 : 0);
        graphics.blit(BUTTON, leftPos + BUTTON_X, topPos + BUTTON_Y, 0, buttonRow, BUTTON_W, BUTTON_H, 128, 64);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 28, GOLD, false);
        graphics.drawString(font, Component.translatable("justice.lotmcraft.menu.hint"), 12, 39, ASH, false);

        Component counter = Component.translatable("justice.lotmcraft.menu.count", menu.selectedCount(), JusticePrinciple.values().length);
        graphics.drawString(font, counter, imageWidth - 12 - font.width(counter), 28, CREAM, false);
        if (menu.lockSeconds() > 0) {
            Component lock = Component.translatable("justice.lotmcraft.menu.locked", formatTime(menu.lockSeconds()));
            graphics.drawString(font, lock, imageWidth - 12 - font.width(lock), 39, ASH, false);
        }

        for (int i = 0; i < JusticePrinciple.values().length; i++) {
            JusticePrinciple principle = JusticePrinciple.values()[i];
            int x = GRID_X + (i % 2) * COLUMN_STEP + 42;
            int y = GRID_Y + (i / 2) * ROW_STEP;
            boolean selected = menu.isSelected(principle);
            boolean blocked = menu.isUnavailable(principle) && !selected;
            int nameColor = selected ? UMBER : (blocked ? ASH : CREAM);
            graphics.drawString(font, principle.shortName(), x, y + 13, nameColor, false);
            if (selected) {
                graphics.drawString(font, Component.translatable("justice.lotmcraft.menu.sworn_tag"), x, y + 24, 0x6B4515, false);
            } else if (blocked) {
                graphics.drawString(font, Component.translatable("justice.lotmcraft.menu.unavailable"), x, y + 24, 0x6A5F56, false);
            }
        }

        Component label = menu.lockSeconds() > 0
                ? Component.translatable("justice.lotmcraft.menu.button_locked")
                : Component.translatable("justice.lotmcraft.menu.button");
        int color = menu.canConfirm() ? UMBER : 0x8C8077;
        graphics.drawString(font, label, BUTTON_X + (BUTTON_W - font.width(label)) / 2, BUTTON_Y + 6, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltipFor(graphics, mouseX, mouseY);
    }

    private void renderTooltipFor(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = plateAt(mouseX, mouseY);
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (index >= 0) {
            JusticePrinciple principle = JusticePrinciple.values()[index];
            lines.addAll(font.split(principle.title().copy().withStyle(s -> s.withColor(GOLD).withBold(true)), 190));
            lines.addAll(font.split(Component.translatable("justice.lotmcraft.menu.tip.vow", principle.vow()).withStyle(s -> s.withColor(0xD9826A)), 190));
            lines.addAll(font.split(Component.translatable("justice.lotmcraft.menu.tip.boon", principle.boon()).withStyle(s -> s.withColor(0xC8E0A0)), 190));
            if (menu.isUnavailable(principle) && !menu.isSelected(principle)) {
                lines.addAll(font.split(Component.translatable("justice.lotmcraft.principle." + principle.id() + ".requirement").withStyle(s -> s.withColor(ASH)), 190));
            }
        } else if (mouseX >= leftPos + 10 && mouseX < leftPos + 58 && mouseY >= topPos + 10 && mouseY < topPos + 24) {
            lines.addAll(font.split(Component.translatable("justice.lotmcraft.menu.tip.fails", menu.fails(), 4), 190));
        }
        if (!lines.isEmpty()) graphics.renderTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            int index = plateAt(mouseX, mouseY);
            if (index >= 0 && menu.lockSeconds() <= 0) {
                JusticePrinciple principle = JusticePrinciple.values()[index];
                if (!menu.isUnavailable(principle) || menu.isSelected(principle)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, menu.isSelected(principle) ? 0.8f : 1.2f));
                    return true;
                }
            }
            if (overButton(mouseX, mouseY) && menu.canConfirm()) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, JusticePrincipleMenu.CONFIRM_BUTTON);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1f));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}