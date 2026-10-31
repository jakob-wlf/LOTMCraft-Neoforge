package de.jakob.lotm.beyonders.abilities.error.handler;

import java.util.UUID;

public final class ErrorClientFlags {

    public static volatile Thread clientThread = null;

    public static volatile UUID localPlayer = null;

    public static volatile boolean pickpocketActive = false;

    public static volatile boolean eloquenceActive = false;

    public static volatile boolean decryptionActive = false;

    public static volatile boolean observationFocusActive = false;
    public static volatile boolean treasureHunterActive = false;

    public static volatile long abilityDeceiveUntil = 0;

    public static volatile int abilityDeceiveMode = 0;

    public static volatile long lastOwnAbilityUse = -1000;

    public static volatile long clientGameTime = 0;

    private ErrorClientFlags() {
    }

    public static boolean isLocalPlayer(UUID uuid) {
        return uuid != null && uuid.equals(localPlayer);
    }
}
