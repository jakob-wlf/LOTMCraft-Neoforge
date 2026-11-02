package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.common.DivinationAbility;
import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAuthorityAbility;
import de.jakob.lotm.network.packets.handlers.PendingSelectionRequests;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AsyncSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.concurrent.CompletableFuture;

public record StructureDivinationSelectedPacket(String structureId) implements CustomPacketPayload {

    public static final Type<StructureDivinationSelectedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "structure_divination_selected"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StructureDivinationSelectedPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    StructureDivinationSelectedPacket::structureId,
                    StructureDivinationSelectedPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StructureDivinationSelectedPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        // Only answer a screen the divination ability actually opened, and only with one of the offered structures
        if (!PendingSelectionRequests.consume(player, DivinationAbility.STRUCTURE_DIVINATION, packet.structureId())) return;

        ServerLevel level = player.serverLevel();
        ResourceLocation structureKey = ResourceLocation.tryParse(packet.structureId());

        if (structureKey == null) {
            player.sendSystemMessage(Component.literal("§cInvalid structure id"));
            return;
        }

        int maxDistance = DivinationAbility.getDivinationRange(BeyonderData.getSequence(player), level.getWorldBorder().getSize());
        // findNearestMapStructure takes its search radius in chunks
        int searchRadiusChunks = Math.max(1, maxDistance >> 4);
        BlockPos origin = player.blockPosition();

        CompletableFuture.runAsync(() -> {
            var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var structureHolder = structureRegistry.getHolder(structureKey).orElse(null);

            if (structureHolder == null) return;

            var result = level.getChunkSource()
                    .getGenerator()
                    .findNearestMapStructure(
                            level,
                            HolderSet.direct(structureHolder),
                            origin,
                            searchRadiusChunks,
                            false
                    );

            context.enqueueWork(() -> {
                if (result == null) {
                    player.sendSystemMessage(Component.literal(String.format("§cNo structure nearby in %d blocks", maxDistance)));
                    return;
                }

                BlockPos structurePos = result.getFirst();
                BlockPos playerPos = player.blockPosition();

                int dx = structurePos.getX() - playerPos.getX();
                int dz = structurePos.getZ() - playerPos.getZ();
                int distance = (int) Math.sqrt(dx * dx + dz * dz);

                String mislead = ConcealmentAuthorityAbility.misleadPlace(player, structurePos);
                if (mislead != null) {
                    player.sendSystemMessage(Component.literal(mislead));
                    return;
                }

                String direction = getDirection(dx, dz);
                String structureName = packet.structureId();

                int colonIndex = structureName.indexOf(':');
                if (colonIndex != -1) {
                    structureName = structureName.substring(colonIndex + 1);
                }
                structureName = structureName.replace('_', ' ');

                player.sendSystemMessage(Component.literal(String.format(
                        "§5You sense §d%s§5 to the §d%s§5, about §d%d blocks §5away...",
                        structureName,
                        direction,
                        distance
                )));
            });
        }, AsyncSearch.EXECUTOR);
    }

    private static String getDirection(int dx, int dz) {
        if (dx == 0 && dz == 0) return "directly below/above";

        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;

        String[] directions = {
                "East", "South-East", "South", "South-West",
                "West", "North-West", "North", "North-East", "East"
        };

        int index = (int) Math.floor((angle + 22.5) / 45);
        return directions[index];
    }
}
