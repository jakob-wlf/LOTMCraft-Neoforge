package de.jakob.lotm.beyonders.abilities.sun.justice;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.Set;

public final class JusticeData {
    public static final String ROOT = "lotm_justice";
    public static final String MASK = "mask";
    public static final String FAILS = "fails";
    public static final String LOCK = "lock";
    public static final String LAST_FAIL = "last_fail";
    public static final String SEQUENCE = "sequence";
    public static final String DEADLINE = "purify_deadline";
    public static final String ALLY = "loyal_ally";
    public static final String DARK = "dark";

    private JusticeData() {}

    public static CompoundTag of(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT)) root.put(ROOT, new CompoundTag());
        return root.getCompound(ROOT);
    }

    public static int mask(Player player) {
        return of(player).getInt(MASK);
    }

    public static Set<JusticePrinciple> chosen(Player player) {
        int mask = mask(player);
        Set<JusticePrinciple> set = EnumSet.noneOf(JusticePrinciple.class);
        for (JusticePrinciple p : JusticePrinciple.values()) {
            if ((mask & (1 << p.ordinal())) != 0) set.add(p);
        }
        return set;
    }

    public static boolean has(Player player, JusticePrinciple principle) {
        return (mask(player) & (1 << principle.ordinal())) != 0;
    }

    public static boolean isSworn(Player player) {
        return Integer.bitCount(mask(player)) >= 2;
    }

    public static int fails(Player player) {
        return of(player).getInt(FAILS);
    }
}
