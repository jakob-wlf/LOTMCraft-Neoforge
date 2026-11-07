package de.jakob.lotm.beyonders.abilities.justiciar.balancing;

/** Gameplay limits kept independent of the game runtime for regression tests. */
public final class BalanceRules {
    private BalanceRules() {}
    public static int individualDurationTicks(int casterSequence, int targetSequence) {
        if (targetSequence == 0 && casterSequence > 0) return 20 * 3;
        int difference = Math.max(0, casterSequence - targetSequence);
        return Math.max(20 * 3, 20 * 60 / (1 + 2 * difference));
    }
}
