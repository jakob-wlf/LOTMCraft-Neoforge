package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.door.DimensionSightAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DimensionSightLocationPacket(int x, int y, int z) implements CustomPacketPayload {
    public static final Type<DimensionSightLocationPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "dimension_sight_location"));

    public static final StreamCodec<FriendlyByteBuf, DimensionSightLocationPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, DimensionSightLocationPacket::x,
            ByteBufCodecs.INT, DimensionSightLocationPacket::y,
            ByteBufCodecs.INT, DimensionSightLocationPacket::z,
            DimensionSightLocationPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DimensionSightLocationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DimensionSightAbility.watchLocation(player, new BlockPos(packet.x(), packet.y(), packet.z()));
            }
        });
    }
}
