package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.door.SpatialCageAbility;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RemoveSpatialCagePacket(UUID cage) implements CustomPacketPayload {
    public static final Type<RemoveSpatialCagePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "remove_spatial_cage"));

    public static final StreamCodec<FriendlyByteBuf, RemoveSpatialCagePacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RemoveSpatialCagePacket::cage,
            RemoveSpatialCagePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveSpatialCagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SpatialCageAbility.dismantle(player, packet.cage());
            }
        });
    }
}
