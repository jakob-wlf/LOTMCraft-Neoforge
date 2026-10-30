package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAuthorityAbility;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SubmitDivinationMisleadPacket(String target, String outcome) implements CustomPacketPayload {
    public static final Type<SubmitDivinationMisleadPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("lotmcraft", "submit_divination_mislead"));

    public static final StreamCodec<ByteBuf, SubmitDivinationMisleadPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SubmitDivinationMisleadPacket::target,
            ByteBufCodecs.STRING_UTF8, SubmitDivinationMisleadPacket::outcome,
            SubmitDivinationMisleadPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubmitDivinationMisleadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ConcealmentAuthorityAbility.submitRule(player, packet.target(), packet.outcome());
            }
        });
    }
}
