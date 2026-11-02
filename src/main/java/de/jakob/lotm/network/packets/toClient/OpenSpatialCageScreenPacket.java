package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record OpenSpatialCageScreenPacket(UUID cage, int cost, int seconds) implements CustomPacketPayload {
    public static final Type<OpenSpatialCageScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "open_spatial_cage_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenSpatialCageScreenPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, OpenSpatialCageScreenPacket::cage,
            ByteBufCodecs.INT, OpenSpatialCageScreenPacket::cost,
            ByteBufCodecs.INT, OpenSpatialCageScreenPacket::seconds,
            OpenSpatialCageScreenPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSpatialCageScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().getReceptionSide().isClient()) {
                ClientHandler.openSpatialCageScreen(packet.cage(), packet.cost(), packet.seconds());
            }
        });
    }
}
