package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncDimensionSightPacket(int veil, BlockPos center, boolean terrain, int[] blocks, List<Sighted> sighted) implements CustomPacketPayload {

    public record Sighted(int id, int type, UUID player, String name, float x, float y, float z,
                          float body, float head, float pitch, CompoundTag data) {}

    public static final Type<SyncDimensionSightPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "sync_dimension_sight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDimensionSightPacket> STREAM_CODEC =
            StreamCodec.of(SyncDimensionSightPacket::write, SyncDimensionSightPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, SyncDimensionSightPacket packet) {
        buf.writeVarInt(packet.veil);
        buf.writeBlockPos(packet.center);
        buf.writeBoolean(packet.terrain);
        buf.writeVarIntArray(packet.blocks);
        buf.writeVarInt(packet.sighted.size());
        for (Sighted s : packet.sighted) {
            buf.writeVarInt(s.id);
            buf.writeVarInt(s.type);
            buf.writeBoolean(s.player != null);
            if (s.player != null) {
                buf.writeUUID(s.player);
                buf.writeUtf(s.name);
            }
            buf.writeFloat(s.x);
            buf.writeFloat(s.y);
            buf.writeFloat(s.z);
            buf.writeFloat(s.body);
            buf.writeFloat(s.head);
            buf.writeFloat(s.pitch);
            buf.writeNbt(s.data);
        }
    }

    private static SyncDimensionSightPacket read(RegistryFriendlyByteBuf buf) {
        int veil = buf.readVarInt();
        BlockPos center = buf.readBlockPos();
        boolean terrain = buf.readBoolean();
        int[] blocks = buf.readVarIntArray();
        int count = buf.readVarInt();
        List<Sighted> sighted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readVarInt();
            int type = buf.readVarInt();
            UUID player = null;
            String name = "";
            if (buf.readBoolean()) {
                player = buf.readUUID();
                name = buf.readUtf();
            }
            float x = buf.readFloat();
            float y = buf.readFloat();
            float z = buf.readFloat();
            float body = buf.readFloat();
            float head = buf.readFloat();
            float pitch = buf.readFloat();
            CompoundTag data = buf.readNbt();
            sighted.add(new Sighted(id, type, player, name, x, y, z, body, head, pitch, data));
        }
        return new SyncDimensionSightPacket(veil, center, terrain, blocks, sighted);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncDimensionSightPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ClientHandler.syncDimensionSight(packet);
            }
        });
    }
}
