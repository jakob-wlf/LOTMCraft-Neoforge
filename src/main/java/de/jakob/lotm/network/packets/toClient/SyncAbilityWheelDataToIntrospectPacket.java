package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.network.packets.handlers.ClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

public record SyncAbilityWheelDataToIntrospectPacket(ArrayList<String> abilityIds, int activeWheel, int wheelCount) implements CustomPacketPayload {
    
    public static final Type<SyncAbilityWheelDataToIntrospectPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("lotm", "sync_ability_wheel_data"));

    public static final StreamCodec<ByteBuf, SyncAbilityWheelDataToIntrospectPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8),
            SyncAbilityWheelDataToIntrospectPacket::abilityIds,
            ByteBufCodecs.VAR_INT,
            SyncAbilityWheelDataToIntrospectPacket::activeWheel,
            ByteBufCodecs.VAR_INT,
            SyncAbilityWheelDataToIntrospectPacket::wheelCount,
            SyncAbilityWheelDataToIntrospectPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAbilityWheelDataToIntrospectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler.handleSyncAbilityWheelDataPacket(packet);
        });
    }
}