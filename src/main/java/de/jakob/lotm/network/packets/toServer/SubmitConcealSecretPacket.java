package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAbility;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SubmitConcealSecretPacket(String word) implements CustomPacketPayload {
    public static final Type<SubmitConcealSecretPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("lotmcraft", "submit_conceal_secret"));

    public static final StreamCodec<ByteBuf, SubmitConcealSecretPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SubmitConcealSecretPacket::word,
            SubmitConcealSecretPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubmitConcealSecretPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ConcealmentAbility.sealSecret(serverPlayer, packet.word());
            }
        });
    }
}
