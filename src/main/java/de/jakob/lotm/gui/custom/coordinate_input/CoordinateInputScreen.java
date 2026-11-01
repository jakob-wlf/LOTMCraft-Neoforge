package de.jakob.lotm.gui.custom.coordinate_input;

import de.jakob.lotm.beyonders.abilities.common.DivinationAbility;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.handlers.TeleportUse;
import de.jakob.lotm.network.packets.toServer.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CoordinateInputScreen extends Screen {
    private EditBox xBox, yBox, zBox;
    private CycleButton<ResourceKey<Level>> dimension;

    private final LivingEntity entity;
    private final String use;
    
    public CoordinateInputScreen(LivingEntity entity, String use) {
        super(Component.literal("Enter Coordinates"));

        this.entity = entity;
        this.use = use;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // X coordinate input
        this.xBox = new EditBox(this.font, centerX - 60, centerY - 30, 120, 20, Component.literal("X - Coordinate"));
        this.xBox.setMaxLength(10);
        this.addRenderableWidget(this.xBox);

        // Y coordinate input
        this.yBox = new EditBox(this.font, centerX - 60, centerY - 5, 120, 20, Component.literal("Y - Coordinate"));
        this.yBox.setMaxLength(10);
        this.addRenderableWidget(this.yBox);

        // Z coordinate input
        this.zBox = new EditBox(this.font, centerX - 60, centerY + 20, 120, 20, Component.literal("Z - Coordinate"));
        this.zBox.setMaxLength(10);
        this.addRenderableWidget(this.zBox);

        if (use.equals("dimension_sight") && this.minecraft != null && this.minecraft.getConnection() != null) {
            List<ResourceKey<Level>> levels = new ArrayList<>(this.minecraft.getConnection().levels());
            levels.sort(Comparator.comparing(key -> key.location().toString()));
            this.dimension = CycleButton.<ResourceKey<Level>>builder(key -> Component.literal(key.location().toString()))
                    .withValues(levels)
                    .withInitialValue(entity.level().dimension())
                    .displayOnlyValue()
                    .create(centerX - 60, centerY - 55, 120, 20, Component.literal("Dimension"));
            this.addRenderableWidget(this.dimension);
        }

        // Confirm button
        Button confirmButton = Button.builder(Component.literal("Confirm"), this::onConfirm)
                .bounds(centerX - 50, centerY + 50, 100, 20)
                .build();
        this.addRenderableWidget(confirmButton);

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Draw labels next to the input boxes
        graphics.drawString(this.font, "X:", centerX - 80, centerY - 25, 0xFFFFFF);
        graphics.drawString(this.font, "Y:", centerX - 80, centerY, 0xFFFFFF);
        graphics.drawString(this.font, "Z:", centerX - 80, centerY + 25, 0xFFFFFF);
    }
    
    private void onConfirm(Button button) {
        try {
            int x = Integer.parseInt(this.xBox.getValue());
            int y = Integer.parseInt(this.yBox.getValue());
            int z = Integer.parseInt(this.zBox.getValue());

            Level level = entity.level();
            WorldBorder border = level.getWorldBorder();

            if (this.dimension != null) {
                PacketHandler.sendToServer(new DimensionSightLocationPacket(this.dimension.getValue().location(), x, y, z));
                this.onClose();
                return;
            }

            if (!border.isWithinBounds(x, z) || y < -64 || y > 320) {
                this.onClose();
                return;
            }

            switch (use) {
                case "travelers_door" -> PacketHandler.sendToServer(new SyncTravelersDoorCoordinatesPacket(x, y, z, entity.getId()));
                case "dream_divination" -> {
                    if(!(entity instanceof Player player))
                        return;
                    PacketHandler.sendToServer(new SyncDreamDivinationCoordinatesPacket(x, y, z, entity.getId()));
                    DivinationAbility.performDreamDivination(player.level(), player, new BlockPos(x, y, z));
                }
                case "teleportation" -> PacketHandler.sendToServer(new TeleportToCoordinatesPacket(TeleportUse.TELEPORTATION, x, y, z));
                case "envision_location" -> PacketHandler.sendToServer(new TeleportToCoordinatesPacket(TeleportUse.ENVISION_LOCATION, x, y, z));
                case "teleportation_authority_self", "teleportation_authority_self_and_nearby", "teleportation_authority_targets" -> PacketHandler.sendToServer(new UseTeleportationAuthorityPacket(use, x, y, z, entity.getId()));
            }

            this.onClose();
            
        } catch (NumberFormatException ignored) {
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}