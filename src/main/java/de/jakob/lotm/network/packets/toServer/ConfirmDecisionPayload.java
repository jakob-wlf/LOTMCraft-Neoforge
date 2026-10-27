package de.jakob.lotm.network.packets.toServer;

import com.llamalad7.mixinextras.expression.Definition;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractManager;
import de.jakob.lotm.gui.custom.notarization.ConfirmMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfirmDecisionPayload(int containerId, boolean accept) implements CustomPacketPayload {
    public static final Type<ConfirmDecisionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "confirm_decision"));
    public static final StreamCodec<ByteBuf, ConfirmDecisionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfirmDecisionPayload::containerId,
            ByteBufCodecs.BOOL, ConfirmDecisionPayload::accept,
            ConfirmDecisionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfirmDecisionPayload packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sp
                && sp.containerMenu instanceof ConfirmMenu m && m.containerId == packet.containerId()) {
            sp.closeContainer();
            ContractManager.onChat(sp, packet.accept() ? "yes" : "no");
        }
    }
}