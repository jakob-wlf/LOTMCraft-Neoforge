package de.jakob.lotm.gui.custom.conceal_secret;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.RemoveConcealSecretPacket;
import de.jakob.lotm.network.packets.toServer.SubmitConcealSecretPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ConcealSecretScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int LIST_TOP = 36;
    private static final int ROW = 14;
    private static final String REMOVE = "Remove";

    private final List<String> secrets;
    private EditBox box;
    private int scroll;

    public ConcealSecretScreen(List<String> secrets) {
        super(Component.literal("Concealed secrets"));
        this.secrets = new ArrayList<>(secrets);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int inputY = this.height - 52;

        this.box = new EditBox(this.font, centerX - 90, inputY, 180, 20, Component.literal("Secret"));
        this.box.setMaxLength(64);
        this.addRenderableWidget(this.box);

        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), this::onConfirm)
                .bounds(centerX - 50, inputY + 26, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - PANEL_WIDTH / 2;
        int listBottom = this.height - 78;
        int rows = Math.max(1, (listBottom - LIST_TOP) / ROW);
        int removeWidth = this.font.width(REMOVE);

        graphics.drawString(this.font, "Concealed secrets", left, 16, 0xFFC8B4FF, false);
        if (secrets.isEmpty()) {
            graphics.drawString(this.font, "Nothing is concealed.", left, LIST_TOP, 0xFFAAAAAA, false);
        } else {
            int shown = Math.min(rows, secrets.size() - scroll);
            for (int i = 0; i < shown; i++) {
                int y = LIST_TOP + i * ROW;
                String word = this.font.plainSubstrByWidth(secrets.get(scroll + i), PANEL_WIDTH - removeWidth - 12);
                graphics.drawString(this.font, word, left, y, 0xFFE6D6FF, false);
                graphics.drawString(this.font, REMOVE, left + PANEL_WIDTH - removeWidth, y, 0xFFFF6666, false);
            }
        }
        graphics.drawString(this.font, "Secret", left, this.height - 64, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !secrets.isEmpty()) {
            int left = this.width / 2 - PANEL_WIDTH / 2;
            int listBottom = this.height - 78;
            int rows = Math.max(1, (listBottom - LIST_TOP) / ROW);
            int removeWidth = this.font.width(REMOVE);
            int removeX = left + PANEL_WIDTH - removeWidth;
            int shown = Math.min(rows, secrets.size() - scroll);
            if (mouseX >= removeX && mouseX <= left + PANEL_WIDTH && mouseY >= LIST_TOP && mouseY < LIST_TOP + shown * ROW) {
                int row = (int) ((mouseY - LIST_TOP) / ROW);
                if (row >= 0 && row < shown) {
                    int index = scroll + row;
                    PacketHandler.sendToServer(new RemoveConcealSecretPacket(index));
                    secrets.remove(index);
                    int max = Math.max(0, secrets.size() - rows);
                    if (scroll > max) scroll = max;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = Math.max(1, (this.height - 78 - LIST_TOP) / ROW);
        int max = Math.max(0, secrets.size() - rows);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private void onConfirm(Button button) {
        String value = this.box.getValue();
        if (value.isEmpty()) return;
        PacketHandler.sendToServer(new SubmitConcealSecretPacket(value));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
