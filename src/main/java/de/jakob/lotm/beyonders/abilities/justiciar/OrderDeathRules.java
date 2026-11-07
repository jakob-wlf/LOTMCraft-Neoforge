package de.jakob.lotm.beyonders.abilities.justiciar;

/** Includes genericKill's Float.MAX_VALUE damage; never turns a prevented hit into negative damage. */
public final class OrderDeathRules {
    private OrderDeathRules() {}
    public static float protectedDamage(float health, float incoming) {
        return incoming >= health ? Math.max(0f,health - 1f) : incoming;
    }
}
