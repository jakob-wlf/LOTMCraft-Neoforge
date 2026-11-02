package de.jakob.lotm.network.packets.handlers;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks selection screens the server opened for a player, so the packet answering the screen
 * is only accepted once, shortly after the ability that opened it was actually used.
 */
public final class PendingSelectionRequests {
    private record Pending(long expiresAtGameTime, @Nullable Set<String> allowedChoices) {}

    private static final Map<UUID, Map<String, Pending>> PENDING = new ConcurrentHashMap<>();

    private PendingSelectionRequests() {}

    public static void arm(ServerPlayer player, String kind, int validForTicks) {
        arm(player, kind, validForTicks, null);
    }

    /** @param allowedChoices the options the screen offered; null accepts any choice */
    public static void arm(ServerPlayer player, String kind, int validForTicks, @Nullable Collection<String> allowedChoices) {
        long expires = player.serverLevel().getGameTime() + validForTicks;
        Set<String> allowed = allowedChoices == null ? null : Set.copyOf(allowedChoices);
        PENDING.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).put(kind, new Pending(expires, allowed));
    }

    public static boolean consume(ServerPlayer player, String kind) {
        return consume(player, kind, null);
    }

    public static boolean consume(ServerPlayer player, String kind, @Nullable String choice) {
        Map<String, Pending> byKind = PENDING.get(player.getUUID());
        if (byKind == null) return false;
        Pending pending = byKind.remove(kind);
        if (pending == null) return false;
        if (player.serverLevel().getGameTime() > pending.expiresAtGameTime()) return false;
        return pending.allowedChoices() == null || (choice != null && pending.allowedChoices().contains(choice));
    }

    public static void clear(UUID playerId) {
        PENDING.remove(playerId);
    }
}
