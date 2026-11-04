package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.data.AbilityWheelClientData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SyncAbilityWheelPacket(List<List<String>> wheels, int activeWheel, List<Integer> selected) implements CustomPacketPayload {

    public static final Type<SyncAbilityWheelPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "sync_ability_wheel"));

    public static final StreamCodec<ByteBuf, SyncAbilityWheelPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()),
            SyncAbilityWheelPacket::wheels,
            ByteBufCodecs.VAR_INT,
            SyncAbilityWheelPacket::activeWheel,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
            SyncAbilityWheelPacket::selected,
            SyncAbilityWheelPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAbilityWheelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AbilityWheelClientData.setWheels(packet.wheels(), packet.activeWheel(), packet.selected()));
    }
}
