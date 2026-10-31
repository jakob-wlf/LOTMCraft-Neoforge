package de.jakob.lotm.gui.custom.error;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class DeceitWheelScreen extends Screen {

    public static final String[] DECEITS = {"parasite", "intuition", "sanity", "health", "spirituality", "item", "hunger", "inventory", "divination", "ally", "ability"};

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/abilities/ability_wheel_background.png");
    private static final int SIZE = 200;
    private static final int RADIUS = SIZE / 2;
    private static final int SLOT = 26;

    private final List<Integer> choices = new ArrayList<>();
    private final boolean area;
    private final int alloyBits;
    private int hovered = -1;

    public DeceitWheelScreen(CompoundTag config, boolean area) {
        super(Component.translatable("gui.lotmcraft.deceit_wheel.title"));
        this.area = area;
        this.alloyBits = config.getInt("alloy");
        choices.add(-1);
        for (int i = 0; i < DECEITS.length; i++) choices.add(i);
    }

    public static ResourceLocation icon(int choice) {
        String name = choice < 0 ? "alloy" : DECEITS[choice];
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/deceit/" + name + ".png");
    }

    public static Component name(int choice) {
        return Component.translatable("gui.lotmcraft.deceit." + (choice < 0 ? "alloy" : DECEITS[choice]));
    }

    private int[] slotPosition(int index, int cx, int cy) {
        double angle = Math.toRadians(index * (360.0 / choices.size()) - 90);
        int r = (int) (RADIUS * 0.68);
        return new int[]{cx + (int) (Math.cos(angle) * r), cy + (int) (Math.sin(angle) * r)};
    }

    private int slotAt(double mouseX, double mouseY) {
        int cx = width / 2, cy = height / 2;
        double dx = mouseX - cx, dy = mouseY - cy;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 22 || distance > RADIUS) return -1;
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        if (angle < 0) angle += 360;
        double per = 360.0 / choices.size();
        return (int) (((angle + per / 2) % 360) / per);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2, cy = height / 2;
        hovered = slotAt(mouseX, mouseY);

        RenderSystem.enableBlend();
        graphics.blit(BACKGROUND, cx - RADIUS, cy - RADIUS, 0, 0, SIZE, SIZE, SIZE, SIZE);

        for (int i = 0; i < choices.size(); i++) {
            int choice = choices.get(i);
            int[] pos = slotPosition(i, cx, cy);
            boolean isHovered = i == hovered;
            int size = isHovered ? SLOT + 4 : SLOT;
            int x = pos[0] - size / 2, y = pos[1] - size / 2;

            boolean inAlloy = choice >= 0 && (alloyBits & (1 << choice)) != 0;
            int border = choice < 0 ? 0xFFE0B0FF : inAlloy ? 0xFFB088FF : 0xFF6A5A96;
            if (isHovered) graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0x608A4FD6);
            graphics.fill(x, y, x + size, y + size, 0xD0120E1E);
            graphics.fill(x, y, x + size, y + 1, border);
            graphics.fill(x, y + size - 1, x + size, y + size, border);
            graphics.fill(x, y, x + 1, y + size, border);
            graphics.fill(x + size - 1, y, x + size, y + size, border);
            graphics.blit(icon(choice), x + 3, y + 3, 0, 0, size - 6, size - 6, size - 6, size - 6);
        }
        RenderSystem.disableBlend();

        Component center = area ? Component.translatable("gui.lotmcraft.deceit_wheel.area") : Component.translatable("gui.lotmcraft.deceit_wheel.single");
        graphics.drawCenteredString(font, center, cx, cy - 4, 0xFFC9B6FF);

        if (hovered >= 0) {
            int choice = choices.get(hovered);
            graphics.drawCenteredString(font, name(choice), cx, cy + RADIUS + 8, 0xFFFFFFFF);
            graphics.drawCenteredString(font, Component.translatable("gui.lotmcraft.deceit." + (choice < 0 ? "alloy" : DECEITS[choice]) + ".hint"),
                    cx, cy + RADIUS + 20, 0xFF9A8CC0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("choice", choices.get(slot));
                PacketHandler.sendToServer(new ErrorActionPacket("deceit_apply", tag));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
