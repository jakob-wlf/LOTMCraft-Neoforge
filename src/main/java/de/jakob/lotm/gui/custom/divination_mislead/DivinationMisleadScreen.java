package de.jakob.lotm.gui.custom.divination_mislead;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.RemoveDivinationMisleadPacket;
import de.jakob.lotm.network.packets.toServer.SubmitDivinationMisleadPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DivinationMisleadScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int LIST_TOP = 36;
    private static final int ROW = 14;
    private static final String REMOVE = "Remove";

    private final List<String> lines;
    private EditBox target;
    private EditBox outcome;
    private int scroll;

    public DivinationMisleadScreen(List<String> lines) {
        super(Component.literal("Divination mislead"));
        this.lines = new ArrayList<>(lines);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int inputY = this.height - 78;

        this.target = new EditBox(this.font, centerX - 100, inputY, 200, 20, Component.literal("Target"));
        this.target.setMaxLength(64);
        this.addRenderableWidget(this.target);

        this.outcome = new EditBox(this.font, centerX - 100, inputY + 24, 200, 20, Component.literal("Outcome"));
        this.outcome.setMaxLength(80);
        this.addRenderableWidget(this.outcome);

        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), this::onConfirm)
                .bounds(centerX - 50, inputY + 48, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int listBottom = this.height - 104;
        int rows = Math.max(1, (listBottom - LIST_TOP) / ROW);
        int removeWidth = this.font.width(REMOVE);
        graphics.drawString(this.font, "Divination mislead", left, 16, 0xFFC8B4FF, false);
        if (lines.isEmpty()) {
            graphics.drawString(this.font, "Nothing is misled.", left, LIST_TOP, 0xFFAAAAAA, false);
        } else {
            int shown = Math.min(rows, lines.size() - scroll);
            for (int i = 0; i < shown; i++) {
                int y = LIST_TOP + i * ROW;
                String line = this.font.plainSubstrByWidth(lines.get(scroll + i), PANEL_WIDTH - removeWidth - 12);
                graphics.drawString(this.font, line, left, y, 0xFFE6D6FF, false);
                graphics.drawString(this.font, REMOVE, left + PANEL_WIDTH - removeWidth, y, 0xFFFF6666, false);
            }
        }
        graphics.drawString(this.font, "Player name, or x z. Outcome, or spirit.", left, this.height - 92, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !lines.isEmpty()) {
            int left = this.width / 2 - PANEL_WIDTH / 2;
            int listBottom = this.height - 104;
            int rows = Math.max(1, (listBottom - LIST_TOP) / ROW);
            int removeWidth = this.font.width(REMOVE);
            int removeX = left + PANEL_WIDTH - removeWidth;
            int shown = Math.min(rows, lines.size() - scroll);
            if (mouseX >= removeX && mouseX <= left + PANEL_WIDTH && mouseY >= LIST_TOP && mouseY < LIST_TOP + shown * ROW) {
                int row = (int) ((mouseY - LIST_TOP) / ROW);
                if (row >= 0 && row < shown) {
                    int index = scroll + row;
                    PacketHandler.sendToServer(new RemoveDivinationMisleadPacket(index));
                    lines.remove(index);
                    int max = Math.max(0, lines.size() - rows);
                    if (scroll > max) scroll = max;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = Math.max(1, (this.height - 104 - LIST_TOP) / ROW);
        int max = Math.max(0, lines.size() - rows);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private void onConfirm(Button button) {
        if (this.target.getValue().isBlank() || this.outcome.getValue().isBlank()) return;
        PacketHandler.sendToServer(new SubmitDivinationMisleadPacket(this.target.getValue(), this.outcome.getValue()));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
