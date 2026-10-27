package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record CreateContractPayload(UUID partyA, UUID partyB, int actionId, int seconds, String item) implements CustomPacketPayload {
    public static final Type<CreateContractPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "create_contract"));
    public static final StreamCodec<ByteBuf, CreateContractPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CreateContractPayload::partyA,
            UUIDUtil.STREAM_CODEC, CreateContractPayload::partyB,
            ByteBufCodecs.VAR_INT, CreateContractPayload::actionId,
            ByteBufCodecs.VAR_INT, CreateContractPayload::seconds,
            ByteBufCodecs.STRING_UTF8, CreateContractPayload::item,
            CreateContractPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CreateContractPayload packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sp) ContractManager.handleCreateRequest(sp, packet);
    }
}
