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

public record SyncConcealmentVeilsPacket(List<Double> packed, List<Integer> bare, List<Integer> concealed) implements CustomPacketPayload {
    public static final Type<SyncConcealmentVeilsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "sync_concealment_veils"));

    public static final StreamCodec<ByteBuf, SyncConcealmentVeilsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.packed().size());
                for (double value : packet.packed()) buf.writeDouble(value);
                buf.writeInt(packet.bare().size());
                for (int id : packet.bare()) buf.writeInt(id);
                buf.writeInt(packet.concealed().size());
                for (int id : packet.concealed()) buf.writeInt(id);
            },
            buf -> {
                int count = buf.readInt();
                ArrayList<Double> packed = new ArrayList<>(count);
                for (int i = 0; i < count; i++) packed.add(buf.readDouble());
                int bareCount = buf.readInt();
                ArrayList<Integer> bare = new ArrayList<>(bareCount);
                for (int i = 0; i < bareCount; i++) bare.add(buf.readInt());
                int concealedCount = buf.readInt();
                ArrayList<Integer> concealed = new ArrayList<>(concealedCount);
                for (int i = 0; i < concealedCount; i++) concealed.add(buf.readInt());
                return new SyncConcealmentVeilsPacket(packed, bare, concealed);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncConcealmentVeilsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) ClientHandler.setConcealmentSpheres(packet.packed(), packet.bare(), packet.concealed());
        });
    }
}
