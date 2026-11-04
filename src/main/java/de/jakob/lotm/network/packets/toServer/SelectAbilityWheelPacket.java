package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.AbilityWheelComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.util.helper.AbilityWheelHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectAbilityWheelPacket(int index) implements CustomPacketPayload {
    public static final Type<SelectAbilityWheelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "select_ability_wheel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectAbilityWheelPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SelectAbilityWheelPacket::index,
            SelectAbilityWheelPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectAbilityWheelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AbilityWheelComponent component = player.getData(ModAttachments.ABILITY_WHEEL_COMPONENT);
            if (packet.index() < 0 || packet.index() >= component.getWheelCount()) {
                AbilityWheelHelper.syncToClient(player);
                return;
            }
            if (packet.index() != component.getActiveWheel()) {
                component.setActiveWheel(packet.index());
                AbilityWheelHelper.syncToClient(player);
            }
        });
    }
}
