package de.jakob.lotm.beyonders.abilities.justiciar.balancing;

import net.minecraft.world.entity.LivingEntity;

/** Carries caster attribution into effects and scheduled work, restoring nested casts correctly. */
public final class BalanceContext {
    private static final ThreadLocal<LivingEntity> SOURCE = new ThreadLocal<>();
    private BalanceContext() {}
    public static LivingEntity source() { return SOURCE.get(); }
    public static void run(LivingEntity source, Runnable task) {
        LivingEntity previous = SOURCE.get();
        SOURCE.set(source);
        try { task.run(); }
        finally { if (previous == null) SOURCE.remove(); else SOURCE.set(previous); }
    }
    public static Runnable capture(Runnable task) {
        LivingEntity source = SOURCE.get();
        return source == null ? task : () -> run(source, task);
    }
}
