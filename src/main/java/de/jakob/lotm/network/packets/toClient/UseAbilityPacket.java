package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** @param subAbility the sub-ability the server cast for selectable abilities, or -1 to use the client's own selection */
public record UseAbilityPacket(String abilityId, int entityId, int subAbility) implements CustomPacketPayload {

    public static final Type<UseAbilityPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "use_ability"));

    public static final StreamCodec<ByteBuf, UseAbilityPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        UseAbilityPacket::abilityId,
        ByteBufCodecs.INT,
        UseAbilityPacket::entityId,
        ByteBufCodecs.VAR_INT,
        UseAbilityPacket::subAbility,
        UseAbilityPacket::new
    );

    public UseAbilityPacket(String abilityId, int entityId) {
        this(abilityId, entityId, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UseAbilityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                ClientHandler.useAbility(packet);
            }
        });
    }
}
