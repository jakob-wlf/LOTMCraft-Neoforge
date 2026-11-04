package de.jakob.lotm.network.packets.toClient;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ReplicationAnalysisPacket(String abilityId, int uses, int needed, int state) implements CustomPacketPayload {

    public static final int ANALYZING = 0;
    public static final int REPLICATED = 1;
    public static final int SEALED = 2;

    public static final Type<ReplicationAnalysisPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "replication_analysis"));

    public static final StreamCodec<FriendlyByteBuf, ReplicationAnalysisPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ReplicationAnalysisPacket::abilityId,
                    ByteBufCodecs.VAR_INT, ReplicationAnalysisPacket::uses,
                    ByteBufCodecs.VAR_INT, ReplicationAnalysisPacket::needed,
                    ByteBufCodecs.VAR_INT, ReplicationAnalysisPacket::state,
                    ReplicationAnalysisPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReplicationAnalysisPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.handleReplicationAnalysis(packet));
    }
}
