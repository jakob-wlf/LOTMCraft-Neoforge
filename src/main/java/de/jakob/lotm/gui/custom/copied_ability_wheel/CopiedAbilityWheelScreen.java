package de.jakob.lotm.gui.custom.copied_ability_wheel;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.gui.custom.ability_wheel.BaseAbilityWheelScreen;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import de.jakob.lotm.network.packets.toServer.UseCopiedAbilityPacket;
import de.jakob.lotm.util.data.AbilityWheelClientData;
import de.jakob.lotm.util.helper.AbilityId;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class CopiedAbilityWheelScreen extends BaseAbilityWheelScreen<CopiedAbilityWheelMenu> {

    private static final int MAX_ABILITIES = 24;

    private int page;

    public CopiedAbilityWheelScreen(CopiedAbilityWheelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private List<Integer> visible() {
        List<String> all = AbilityWheelClientData.getCopiedAbilityIds();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Ability ability = LOTMCraft.abilityHandler.getById(AbilityId.parse(all.get(i)).baseId());
            if (ability != null && !ability.hasHold()) indices.add(i);
        }
        return indices;
    }

    private int pages() {
        return Math.max(1, (visible().size() + MAX_ABILITIES - 1) / MAX_ABILITIES);
    }

    private int original(int slot) {
        List<Integer> indices = visible();
        int index = page * MAX_ABILITIES + slot;
        return index < indices.size() ? indices.get(index) : -1;
    }

    @Override
    protected List<String> getAbilities() {
        List<String> all = AbilityWheelClientData.getCopiedAbilityIds();
        List<Integer> indices = visible();
        page = Math.min(page, pages() - 1);
        int from = page * MAX_ABILITIES;
        return indices.subList(Math.min(from, indices.size()), Math.min(indices.size(), from + MAX_ABILITIES))
                .stream().map(all::get).toList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pages() > 1 && scrollY != 0) {
            page = Math.floorMod(page + (scrollY < 0 ? 1 : -1), pages());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (pages() <= 1) return;
        String label = (page + 1) + "/" + pages();
        var font = net.minecraft.client.Minecraft.getInstance().font;
        guiGraphics.drawString(font, label, leftPos + imageWidth / 2 - font.width(label) / 2, topPos + imageHeight + 24, 0xFFFFFF, true);
    }

    @Override
    protected int getMaxAbilities() {
        return MAX_ABILITIES;
    }

    @Override
    protected int getLineColor() {
        return 0x4Da8c4e3;
    }

    @Override
    protected int getGlowColor() {
        return 0xa8c4e3;
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, SlotPosition pos, String abilityId, boolean isHovered, int index) {
        AbilityId parsed = AbilityId.parse(abilityId);
        String baseId = parsed.baseId();

        int size = isHovered ? SLOT_HOVER_SIZE : SLOT_SIZE;
        int x = pos.x() - size / 2;
        int y = pos.y() - size / 2;

        guiGraphics.fill(x, y, x + size, y + size, 0xCC000000);

        int borderColor = isHovered ? 0xFFa8c4e3 : 0xFF8999ab;
        int borderWidth = 1;

        if (isHovered) {
            int glowSize = 1;
            int glowColor = 0x40a8c4e3;
            guiGraphics.fill(x - glowSize, y - glowSize, x + size + glowSize, y + size + glowSize, glowColor);
        }

        guiGraphics.fill(x, y, x + size, y + borderWidth, borderColor);
        guiGraphics.fill(x, y + size - borderWidth, x + size, y + size, borderColor);
        guiGraphics.fill(x, y, x + borderWidth, y + size, borderColor);
        guiGraphics.fill(x + size - borderWidth, y, x + size, y + size, borderColor);

        try {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/abilities/" + baseId + ".png");
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);

            int iconPadding = 3;
            int iconSize = size - iconPadding * 2;
            guiGraphics.blit(texture,
                    x + iconPadding,
                    y + iconPadding,
                    0, 0,
                    iconSize,
                    iconSize,
                    iconSize,
                    iconSize);
        } catch (Exception e) {
            int padding = size / 5;
            guiGraphics.fill(x + padding, y + padding, x + size - padding, y + size - padding, 0xFF146B8B);
        }

        index = original(index);
        if (index >= 0 && index < AbilityWheelClientData.getCopiedAbilityRemainingUses().size()) {
            int uses = AbilityWheelClientData.getCopiedAbilityRemainingUses().get(index);
            if (uses > 0) {
                String usesText = String.valueOf(uses);
                guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, usesText,
                        x + size - 8, y + size - 10, 0xFFFFFF, true);
            } else if (uses == -1) {
                guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "∞",
                        x + size - 8, y + size - 10, 0x90EE90, true);
            }
        }

        if (isHovered) {
            Ability ability = LOTMCraft.abilityHandler.getById(baseId);
            if (ability != null) {
                Component name = ability.getNameFormatted(ClientHandler.getPlayer());
                int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(name);
                guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, name,
                        pos.x() - textWidth / 2, pos.y() - size / 2 - 12, 0xFFFFFF, true);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredSlot != -1 && original(hoveredSlot) >= 0) {
            PacketHandler.sendToServer(new UseCopiedAbilityPacket(original(hoveredSlot)));
            this.onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}