package de.jakob.lotm.beyonders.abilities.sun.justice;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JusticeProvocation {
    public static final long WINDOW_TICKS = 20L * 60;
    private static final long SWEEP_INTERVAL_TICKS = 20L * 60;

    private record Link(UUID provoked, UUID aggressor) {}

    private static final Map<Link, Long> links = new ConcurrentHashMap<>();
    private static long lastSweep;

    private JusticeProvocation() {}

    public static boolean registerHit(LivingEntity attacker, LivingEntity victim) {
        long now = attacker.level().getGameTime();
        sweep(now);

        Link retaliation = new Link(attacker.getUUID(), victim.getUUID());
        Long when = links.get(retaliation);
        if (when != null) {
            if (now - when <= WINDOW_TICKS) return true;
            links.remove(retaliation);
        }

        links.put(new Link(victim.getUUID(), attacker.getUUID()), now);
        return false;
    }

    private static void sweep(long now) {
        if (now - lastSweep < SWEEP_INTERVAL_TICKS && now >= lastSweep) return;
        lastSweep = now;
        links.values().removeIf(t -> now - t > WINDOW_TICKS);
    }
}
