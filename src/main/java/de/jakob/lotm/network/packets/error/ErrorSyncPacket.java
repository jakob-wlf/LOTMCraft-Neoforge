package de.jakob.lotm.network.packets.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.rendering.error.ErrorClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ErrorSyncPacket(String key, CompoundTag data) implements CustomPacketPayload {

    public static final Type<ErrorSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "error_sync"));

    public static final StreamCodec<ByteBuf, ErrorSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ErrorSyncPacket::key,
            ByteBufCodecs.COMPOUND_TAG, ErrorSyncPacket::data,
            ErrorSyncPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ErrorSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ErrorClientHandler.handle(packet.key(), packet.data());
            }
        });
    }
}
