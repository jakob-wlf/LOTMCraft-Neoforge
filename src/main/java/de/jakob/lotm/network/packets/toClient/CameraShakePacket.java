package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CameraShakePacket(float intensity, int duration) implements CustomPacketPayload {

    public static final Type<CameraShakePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "camera_shake"));

    public static final StreamCodec<ByteBuf, CameraShakePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CameraShakePacket::intensity,
            ByteBufCodecs.INT, CameraShakePacket::duration,
            CameraShakePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraShakePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ClientHandler.applyCameraShake(packet.intensity(), packet.duration());
            }
        });
    }
}
