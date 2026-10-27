package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

public final class ContractRules {
    private ContractRules() {}
    /** Blocks within which allies can be chosen for a contract. */
    public static final double SELECT_RANGE = 16;
    /** 15 seconds to write "Yes". */
    public static final int CONFIRM_TICKS = 15 * 20;
    /** "Not be near": breach when closer than this. */
    public static final double NEAR_RADIUS = 10;
    /** "Drop an item": the item must be dropped within this distance of the second party. */
    public static final double DROP_RADIUS = 6;
    /** After sealing a "not be near" contract the parties get this long to part ways. */
    public static final int NEAR_GRACE_TICKS = 100;
    /** How long an opened menu may be used to submit a contract. */
    public static final int MENU_TIMEOUT_TICKS = 20 * 120;

    public static String formatDuration(int s) {
        if (s >= 3600 && s % 3600 == 0) { int h = s / 3600; return h + (h == 1 ? " hour" : " hours"); }
        if (s >= 60 && s % 60 == 0) { int m = s / 60; return m + (m == 1 ? " minute" : " minutes"); }
        return s + (s == 1 ? " second" : " seconds");
    }
}
