package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SyncConcealmentVeilsPacket(List<Double> packed) implements CustomPacketPayload {
    public static final Type<SyncConcealmentVeilsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "sync_concealment_veils"));

    public static final StreamCodec<ByteBuf, SyncConcealmentVeilsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.packed().size());
                for (double value : packet.packed()) buf.writeDouble(value);
            },
            buf -> {
                int count = buf.readInt();
                ArrayList<Double> packed = new ArrayList<>(count);
                for (int i = 0; i < count; i++) packed.add(buf.readDouble());
                return new SyncConcealmentVeilsPacket(packed);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncConcealmentVeilsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) ClientHandler.setConcealmentSpheres(packet.packed());
        });
    }
}
