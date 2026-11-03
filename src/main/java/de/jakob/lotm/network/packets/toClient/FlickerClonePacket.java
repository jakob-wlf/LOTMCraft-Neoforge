package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.door.PositionAuthority.PositionFlickerAbility;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record FlickerClonePacket(int entityId, UUID owner) implements CustomPacketPayload {

    public static final Type<FlickerClonePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "flicker_clone"));

    public static final StreamCodec<FriendlyByteBuf, FlickerClonePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    FlickerClonePacket::entityId,
                    UUIDUtil.STREAM_CODEC,
                    FlickerClonePacket::owner,
                    FlickerClonePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlickerClonePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(packet.entityId());
            if (entity != null) PositionFlickerAbility.mark(entity, packet.owner());
        });
    }
}
