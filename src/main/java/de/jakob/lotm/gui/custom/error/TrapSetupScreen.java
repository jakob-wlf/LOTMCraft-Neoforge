package de.jakob.lotm.gui.custom.error;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public class TrapSetupScreen extends Screen {

    private final String[][] values = new String[2][3];
    private int tab = 0;
    private EditBox xBox, yBox, zBox;
    private Button inTab, outTab;

    public TrapSetupScreen(CompoundTag config) {
        super(Component.translatable("gui.lotmcraft.deceive_connection.title"));
        load(config, "in", 0);
        load(config, "out", 1);
    }

    private void load(CompoundTag config, String prefix, int index) {
        if (config.contains(prefix + "X")) {
            values[index][0] = String.valueOf((int) Math.floor(config.getDouble(prefix + "X")));
            values[index][1] = String.valueOf((int) Math.floor(config.getDouble(prefix + "Y")));
            values[index][2] = String.valueOf((int) Math.floor(config.getDouble(prefix + "Z")));
        } else {
            values[index][0] = values[index][1] = values[index][2] = "";
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        inTab = addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceive_connection.in"), b -> switchTab(0))
                .bounds(cx - 102, cy - 70, 100, 20).build());
        outTab = addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceive_connection.out"), b -> switchTab(1))
                .bounds(cx + 2, cy - 70, 100, 20).build());

        xBox = addRenderableWidget(new EditBox(font, cx - 60, cy - 36, 120, 18, Component.literal("X")));
        yBox = addRenderableWidget(new EditBox(font, cx - 60, cy - 12, 120, 18, Component.literal("Y")));
        zBox = addRenderableWidget(new EditBox(font, cx - 60, cy + 12, 120, 18, Component.literal("Z")));
        for (EditBox box : new EditBox[]{xBox, yBox, zBox}) {
            box.setMaxLength(9);
            box.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceive_connection.here"), b -> {
            if (minecraft != null && minecraft.player != null) {
                xBox.setValue(String.valueOf(minecraft.player.getBlockX()));
                yBox.setValue(String.valueOf(minecraft.player.getBlockY()));
                zBox.setValue(String.valueOf(minecraft.player.getBlockZ()));
            }
        }).bounds(cx - 102, cy + 40, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceive_connection.clear"), b -> {
            xBox.setValue("");
            yBox.setValue("");
            zBox.setValue("");
        }).bounds(cx + 2, cy + 40, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceive_connection.save"), b -> save())
                .bounds(cx - 102, cy + 64, 204, 20).build());

        applyTab();
    }

    private void switchTab(int newTab) {
        storeTab();
        tab = newTab;
        applyTab();
    }

    private void storeTab() {
        values[tab][0] = xBox.getValue();
        values[tab][1] = yBox.getValue();
        values[tab][2] = zBox.getValue();
    }

    private void applyTab() {
        xBox.setValue(values[tab][0]);
        yBox.setValue(values[tab][1]);
        zBox.setValue(values[tab][2]);
        inTab.active = tab != 0;
        outTab.active = tab != 1;
    }

    private CompoundTag toTag(int index) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", Integer.parseInt(values[index][0]));
            tag.putInt("y", Integer.parseInt(values[index][1]));
            tag.putInt("z", Integer.parseInt(values[index][2]));
            return tag;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void save() {
        storeTab();
        CompoundTag data = new CompoundTag();
        CompoundTag in = toTag(0);
        CompoundTag out = toTag(1);
        if (in != null) data.put("in", in);
        if (out != null) data.put("out", out);
        PacketHandler.sendToServer(new ErrorActionPacket("trap_config", data));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        graphics.drawCenteredString(font, title, cx, cy - 92, 0xFFC9B6FF);
        graphics.drawCenteredString(font, Component.translatable(tab == 0
                ? "gui.lotmcraft.deceive_connection.in_hint" : "gui.lotmcraft.deceive_connection.out_hint"), cx, cy - 47, 0xFF9A8CC0);
        graphics.drawString(font, "X", cx - 74, cy - 31, 0xFFFFFFFF);
        graphics.drawString(font, "Y", cx - 74, cy - 7, 0xFFFFFFFF);
        graphics.drawString(font, "Z", cx - 74, cy + 17, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
