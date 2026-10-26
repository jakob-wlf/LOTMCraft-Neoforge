package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record SyncAbilityCooldownsPacket(Map<String, Integer> cooldowns) implements CustomPacketPayload {

    public static final Type<SyncAbilityCooldownsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "sync_ability_cooldowns"));

    public static final StreamCodec<ByteBuf, SyncAbilityCooldownsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
            SyncAbilityCooldownsPacket::cooldowns,
            SyncAbilityCooldownsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAbilityCooldownsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = ClientHandler.getPlayer();
            if (player == null) return;
            player.getData(ModAttachments.COOLDOWN_COMPONENT).setCooldowns(packet.cooldowns());
        });
    }
}
