package de.jakob.lotm.gui.custom.error;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ThoroughTheftScreen extends Screen {

    private static final int SLOT = 18;

    private static class Entry {
        final int index;
        ItemStack stack;
        final boolean hidden;
        boolean taken;
        int x, y;

        Entry(int index, ItemStack stack, boolean hidden) {
            this.index = index;
            this.stack = stack;
            this.hidden = hidden;
        }
    }

    private final String targetName;
    private final boolean playerLayout;
    private final int totalTicks;
    private int ticksLeft;
    private int picks;
    private final List<Entry> entries = new ArrayList<>();
    private int panelX, panelY, panelWidth, panelHeight;
    private boolean closedByServer = false;

    private ThoroughTheftScreen(String targetName, boolean playerLayout, int ticks, int picks) {
        super(Component.translatable("gui.lotmcraft.thorough_theft.title"));
        this.targetName = targetName;
        this.playerLayout = playerLayout;
        this.totalTicks = Math.max(1, ticks);
        this.ticksLeft = ticks;
        this.picks = picks;
    }

    public static ThoroughTheftScreen fromTag(CompoundTag data, HolderLookup.Provider provider) {
        ThoroughTheftScreen screen = new ThoroughTheftScreen(data.getString("name"), data.getBoolean("player"), data.getInt("ticks"), data.getInt("picks"));
        ListTag slots = data.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag tag = slots.getCompound(i);
            ItemStack stack = provider != null && tag.contains("item") ? ItemStack.parseOptional(provider, tag.getCompound("item")) : ItemStack.EMPTY;
            screen.entries.add(new Entry(tag.getInt("i"), stack, tag.getBoolean("h")));
        }
        return screen;
    }

    public void onUpdate(CompoundTag data, HolderLookup.Provider provider) {
        int index = data.getInt("i");
        picks = data.getInt("picks");
        for (Entry entry : entries) {
            if (entry.index != index) continue;
            entry.taken = true;
            if (provider != null && data.contains("item")) entry.stack = ItemStack.parseOptional(provider, data.getCompound("item"));
        }
        if (picks <= 0) {
            closedByServer = true;
            onClose();
        }
    }

    @Override
    protected void init() {
        if (playerLayout) {
            panelWidth = 9 * SLOT + 16 + 26;
            panelHeight = 4 * SLOT + 46;
        } else {
            int columns = Math.max(1, Math.min(9, entries.size()));
            panelWidth = Math.max(140, columns * SLOT + 16);
            panelHeight = ((entries.size() + 8) / 9) * SLOT + 46;
        }
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int gridX = panelX + 8;
        int gridY = panelY + 30;
        int mobCounter = 0;
        for (Entry entry : entries) {
            if (playerLayout) {
                if (entry.index < 9) {
                    entry.x = gridX + entry.index * SLOT;
                    entry.y = gridY + 3 * SLOT + 4;
                } else if (entry.index < 36) {
                    entry.x = gridX + (entry.index % 9) * SLOT;
                    entry.y = gridY + (entry.index / 9 - 1) * SLOT;
                } else if (entry.index < 40) {
                    entry.x = gridX + 9 * SLOT + 8;
                    entry.y = gridY + (39 - entry.index) * SLOT;
                } else {
                    entry.x = gridX + 9 * SLOT + 8;
                    entry.y = gridY + 4 * SLOT + 4;
                }
            } else {
                entry.x = gridX + (mobCounter % 9) * SLOT;
                entry.y = gridY + (mobCounter / 9) * SLOT;
                mobCounter++;
            }
        }
    }

    @Override
    public void tick() {
        if (--ticksLeft <= 0) onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int extraBottom = playerLayout ? SLOT + 8 : 0;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight + extraBottom, 0xE0141020);
        int border = 0xFF4742C9;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, border);
        graphics.fill(panelX, panelY + panelHeight + extraBottom - 1, panelX + panelWidth, panelY + panelHeight + extraBottom, border);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight + extraBottom, border);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight + extraBottom, border);

        graphics.drawString(font, Component.translatable("gui.lotmcraft.thorough_theft.pockets", targetName), panelX + 8, panelY + 6, 0xFFE6E0FF, false);
        graphics.drawString(font, Component.translatable("gui.lotmcraft.thorough_theft.picks", picks), panelX + panelWidth - 8 - font.width(Component.translatable("gui.lotmcraft.thorough_theft.picks", picks)), panelY + 6, 0xFFB8A6FF, false);

        // time left
        float fraction = Math.max(0, (ticksLeft - partialTick) / totalTicks);
        int barWidth = panelWidth - 16;
        graphics.fill(panelX + 8, panelY + 18, panelX + 8 + barWidth, panelY + 22, 0xFF2A2440);
        int color = fraction > 0.5f ? 0xFF7A6CF0 : fraction > 0.2f ? 0xFFE0B040 : 0xFFE04040;
        graphics.fill(panelX + 8, panelY + 18, panelX + 8 + (int) (barWidth * fraction), panelY + 22, color);

        Entry hovered = null;
        for (Entry entry : entries) {
            boolean hover = mouseX >= entry.x && mouseX < entry.x + 16 && mouseY >= entry.y && mouseY < entry.y + 16;
            graphics.fill(entry.x - 1, entry.y - 1, entry.x + 17, entry.y + 17, hover && !entry.taken ? 0xFF5A52A8 : 0xFF2E2848);
            graphics.fill(entry.x, entry.y, entry.x + 16, entry.y + 16, 0xFF1A1628);

            if (entry.hidden && !entry.taken) {
                graphics.drawCenteredString(font, "?", entry.x + 8, entry.y + 4, 0xFFC8B8FF);
            } else if (!entry.stack.isEmpty()) {
                graphics.renderItem(entry.stack, entry.x, entry.y);
                graphics.renderItemDecorations(font, entry.stack, entry.x, entry.y);
            }
            if (entry.taken) {
                graphics.fill(entry.x, entry.y, entry.x + 16, entry.y + 16, 0xA0000000);
            }
            if (hover) hovered = entry;
        }

        if (hovered != null && !hovered.hidden && !hovered.stack.isEmpty()) {
            graphics.renderTooltip(font, hovered.stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && picks > 0) {
            for (Entry entry : entries) {
                if (entry.taken) continue;
                if (mouseX >= entry.x && mouseX < entry.x + 16 && mouseY >= entry.y && mouseY < entry.y + 16) {
                    if (!entry.hidden && entry.stack.isEmpty()) return true;
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("slot", entry.index);
                    PacketHandler.sendToServer(new ErrorActionPacket("thorough_pick", tag));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (!closedByServer) {
            PacketHandler.sendToServer(new ErrorActionPacket("thorough_close", new CompoundTag()));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
