package de.jakob.lotm.rendering.error;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public final class ErrorClientState {

    private ErrorClientState() {
    }

    public record ValuableHint(Vec3 pos, float strength) {}

    public static List<ValuableHint> valuables = new ArrayList<>();
    public static long valuablesReceived = 0;

    public record SanityReveal(float sanity, long until) {}

    public static final Map<Integer, SanityReveal> sanityReveals = new HashMap<>();

    public record Link(int a, int b, String type) {}

    public static List<Link> links = new ArrayList<>();
    public static long linksReceived = 0;

    public record Threat(String pathway, float distance) {}

    public static List<Threat> threats = new ArrayList<>();
    public static long threatsReceived = 0;

    public static final Map<Integer, ItemStack> lastHeld = new HashMap<>();

    public static int decryptionTarget = -1;
    public static int decryptionLevel = 0;
    public static float decryptionFraction = 0;
    public static List<String> decryptionLines = new ArrayList<>();
    public static long decryptionReceived = 0;

    public static final Map<Integer, Long> revealed = new HashMap<>();

    public static int theftMark = -1;
    public static long theftMarkReceived = 0;

    public static int treasureTarget = -1;
    public static String treasureName = "";
    public static int treasureValue = 0;
    public static List<ItemStack> treasureItems = new ArrayList<>();
    public static List<Integer> treasureItemValues = new ArrayList<>();
    public static long treasureReceived = 0;

    public static float fakeSanity = 1f;
    public static long fakeSanityUntil = 0;

    public static float fakeHealth = 1f;
    public static long fakeHealthUntil = 0;
    public static boolean fakeDeathShown = false;

    public static int spiritMode = 0;
    public static float spiritValue = 0.5f;
    public static long spiritUntil = 0;

    public static int hungerMode = 0;
    public static long hungerUntil = 0;
    public static long hungerStart = 0;

    public static ItemStack[] fakeInventory = null;
    public static long fakeInventoryUntil = 0;

    public record FakeAlly(UUID uuid, String name, long until, boolean wasReal) {}

    public static final List<FakeAlly> fakeAllies = new ArrayList<>();

    public static boolean isActive(long until, long now) {
        return until > now;
    }

    public static void reset() {
        valuables = new ArrayList<>();
        sanityReveals.clear();
        links = new ArrayList<>();
        threats = new ArrayList<>();
        lastHeld.clear();
        decryptionTarget = -1;
        decryptionLines = new ArrayList<>();
        revealed.clear();
        theftMark = -1;
        treasureTarget = -1;
        treasureItems = new ArrayList<>();
        fakeSanityUntil = 0;
        fakeHealthUntil = 0;
        fakeDeathShown = false;
        spiritUntil = 0;
        hungerUntil = 0;
        fakeInventory = null;
        fakeInventoryUntil = 0;
        fakeAllies.clear();
    }
}
