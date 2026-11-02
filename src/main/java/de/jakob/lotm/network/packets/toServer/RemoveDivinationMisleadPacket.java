package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAuthorityAbility;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RemoveDivinationMisleadPacket(int index) implements CustomPacketPayload {
    public static final Type<RemoveDivinationMisleadPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("lotmcraft", "remove_divination_mislead"));

    public static final StreamCodec<ByteBuf, RemoveDivinationMisleadPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, RemoveDivinationMisleadPacket::index,
            RemoveDivinationMisleadPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoveDivinationMisleadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ConcealmentAuthorityAbility.releaseRule(player, packet.index());
            }
        });
    }
}
