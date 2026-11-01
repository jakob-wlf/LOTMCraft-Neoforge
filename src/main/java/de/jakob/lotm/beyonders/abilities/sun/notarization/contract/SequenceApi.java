package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

public final class SequenceApi {
    private SequenceApi() {}

    public static ToIntFunction<ServerPlayer> sequenceGetter =
            p -> ContractSavedData.get(p.server).getSequence(p.getUUID());

    public static BiPredicate<ServerPlayer, ServerPlayer> allyCheck = (a, b) ->
            a == b || (a.getTeam() != null && b.getTeam() != null && a.getTeam().isAlliedTo(b.getTeam()));

    public static int sequenceOf(ServerPlayer p) { return sequenceGetter.applyAsInt(p); }

    public static int sequenceOf(MinecraftServer server, UUID id, int fallback) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null ? sequenceOf(p) : fallback;
    }
}
