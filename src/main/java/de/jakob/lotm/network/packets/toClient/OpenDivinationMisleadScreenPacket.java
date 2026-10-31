package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenDivinationMisleadScreenPacket(List<String> lines) implements CustomPacketPayload {
    public static final Type<OpenDivinationMisleadScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "open_divination_mislead_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenDivinationMisleadScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.lines().size());
                for (String line : packet.lines()) buf.writeUtf(line);
            },
            buf -> {
                int count = buf.readVarInt();
                ArrayList<String> lines = new ArrayList<>(count);
                for (int i = 0; i < count; i++) lines.add(buf.readUtf());
                return new OpenDivinationMisleadScreenPacket(lines);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDivinationMisleadScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().getReceptionSide().isClient()) {
                ClientHandler.openDivinationMisleadScreen(packet.lines());
            }
        });
    }
}
