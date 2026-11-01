package de.jakob.lotm.gui.custom.error;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public class FakeDeathScreen extends Screen {

    private int delay = 0;
    private Button respawn;
    private Button title;

    public FakeDeathScreen() {
        super(Component.translatable("deathScreen.title"));
    }

    @Override
    protected void init() {
        delay = 0;
        respawn = addRenderableWidget(Button.builder(Component.translatable("deathScreen.respawn"), button -> {
            PacketHandler.sendToServer(new ErrorActionPacket("fake_death_respawn", new CompoundTag()));
            onClose();
        }).bounds(width / 2 - 100, height / 4 + 72, 200, 20).build());
        title = addRenderableWidget(Button.builder(Component.translatable("deathScreen.titleScreen"), button -> onClose())
                .bounds(width / 2 - 100, height / 4 + 96, 200, 20).build());
        respawn.active = false;
        title.active = false;
    }

    @Override
    public void tick() {
        if (++delay == 20) {
            respawn.active = true;
            title.active = true;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0x60500000, 0xA0803030);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().scale(2.0f, 2.0f, 2.0f);
        graphics.drawCenteredString(font, getTitle(), width / 2 / 2, 30, 0xFFFFFF);
        graphics.pose().popPose();
        int score = minecraft != null && minecraft.player != null ? minecraft.player.getScore() : 0;
        graphics.drawCenteredString(font, Component.translatable("deathScreen.score.value",
                Component.literal(Integer.toString(score)).withStyle(ChatFormatting.YELLOW)), width / 2, 100, 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
