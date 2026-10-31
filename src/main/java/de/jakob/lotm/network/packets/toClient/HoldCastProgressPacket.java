package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HoldCastProgressPacket(float charge, int needed) implements CustomPacketPayload {

    public static final Type<HoldCastProgressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "hold_cast_progress"));

    public static final StreamCodec<ByteBuf, HoldCastProgressPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, HoldCastProgressPacket::charge,
            ByteBufCodecs.VAR_INT, HoldCastProgressPacket::needed,
            HoldCastProgressPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoldCastProgressPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ClientHandler.setHoldCharge(packet.charge(), packet.needed());
            }
        });
    }
}
