package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import net.minecraft.network.chat.Component;

public enum ContractAction {
    DROP_ITEM("drop_item", true, 5, 10, 15, 30, 60, 120, 300),
    NOT_KILL("not_kill", false, 30, 60, 300, 600, 1800, 3600),
    NOT_DAMAGE("not_damage", false, 30, 60, 300, 600, 1800, 3600),
    NOT_BE_NEAR("not_be_near", false, 30, 60, 300, 600, 1800, 3600);

    private final String key;
    private final boolean deadline;
    private final int[] durations;

    ContractAction(String key, boolean deadline, int... durations) {
        this.key = key; this.deadline = deadline; this.durations = durations;
    }

    public String key() { return key; }
    public boolean isDeadline() { return deadline; }
    public int[] durations() { return durations; }
    public int getId() { return ordinal(); }

    public boolean allows(int seconds) {
        for (int d : durations) if (d == seconds) return true;
        return false;
    }

    public Component displayName() { return Component.translatable("contractnotary.action." + key); }
    public Component description() { return Component.translatable("contractnotary.action." + key + ".desc"); }

    public static ContractAction byId(int id) {
        ContractAction[] v = values();
        return id >= 0 && id < v.length ? v[id] : null;
    }
}
