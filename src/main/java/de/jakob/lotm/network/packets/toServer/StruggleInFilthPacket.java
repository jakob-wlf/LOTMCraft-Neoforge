package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.DivineKingdomAbyssAbility;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StruggleInFilthPacket() implements CustomPacketPayload {
    public static final Type<StruggleInFilthPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "struggle_in_filth"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StruggleInFilthPacket> STREAM_CODEC = StreamCodec.unit(new StruggleInFilthPacket());
    @Override
    public Type<? extends CustomPacketPayload> type() {return TYPE;}
    public static void handle(StruggleInFilthPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) DivineKingdomAbyssAbility.markJump(player);;
        });
    }
}