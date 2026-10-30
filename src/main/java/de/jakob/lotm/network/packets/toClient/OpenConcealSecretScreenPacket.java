package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.gui.custom.conceal_secret.ConcealSecretScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenConcealSecretScreenPacket(List<String> words) implements CustomPacketPayload {
    public static final Type<OpenConcealSecretScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "open_conceal_secret_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenConcealSecretScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.words().size());
                for (String word : packet.words()) buf.writeUtf(word);
            },
            buf -> {
                int count = buf.readVarInt();
                ArrayList<String> words = new ArrayList<>(count);
                for (int i = 0; i < count; i++) words.add(buf.readUtf());
                return new OpenConcealSecretScreenPacket(words);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenConcealSecretScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().getReceptionSide().isClient()) {
                Minecraft.getInstance().setScreen(new ConcealSecretScreen(packet.words()));
            }
        });
    }
}
