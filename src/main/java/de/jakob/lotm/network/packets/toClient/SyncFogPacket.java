package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncFogPacket(int entityId, boolean active, int index, float red, float green, float blue, long durationMillis) implements CustomPacketPayload {
    
    public static final Type<SyncFogPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "sync_fog"));
    
    public static final StreamCodec<ByteBuf, SyncFogPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            ByteBufCodecs.INT.encode(buf, packet.entityId());
            ByteBufCodecs.BOOL.encode(buf, packet.active());
            ByteBufCodecs.INT.encode(buf, packet.index());
            ByteBufCodecs.FLOAT.encode(buf, packet.red());
            ByteBufCodecs.FLOAT.encode(buf, packet.green());
            ByteBufCodecs.FLOAT.encode(buf, packet.blue());
            ByteBufCodecs.VAR_LONG.encode(buf, packet.durationMillis());
        },
        buf -> new SyncFogPacket(
            ByteBufCodecs.INT.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.INT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf)
        )
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(SyncFogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ClientHandler.handleFogPacket(packet);
            }
        });
    }
}