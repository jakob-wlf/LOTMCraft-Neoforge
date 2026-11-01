package de.jakob.lotm.beyonders.abilities.sun.notarization;

import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.Contract;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractManager;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

public final class PendingAccess {
    private PendingAccess() {}
    private static Field field;

    @SuppressWarnings("unchecked")
    public static Map<UUID, Contract> pending() {
        try {
            if (field == null) {
                field = ContractManager.class.getDeclaredField("PENDING");
                field.setAccessible(true);
            }
            return (Map<UUID, Contract>) field.get(null);
        } catch (ReflectiveOperationException e) {
            return Map.of();
        }
    }

    public static boolean needsAnswer(Contract c, UUID player) {
        return c.involves(player) && !(player.equals(c.partyA) ? c.confirmedA : c.confirmedB);
    }

    public static boolean stillWaiting(UUID contractId, UUID player) {
        Contract c = pending().get(contractId);
        return c != null && needsAnswer(c, player);
    }
}