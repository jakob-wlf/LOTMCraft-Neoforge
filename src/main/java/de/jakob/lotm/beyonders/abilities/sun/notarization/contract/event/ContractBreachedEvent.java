package de.jakob.lotm.beyonders.abilities.sun.notarization.contract.event;

import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.Contract;
import net.neoforged.bus.api.Event;

public class ContractBreachedEvent extends Event {
    private final Contract contract; private final int severity; private final boolean notaryEmpowered;
    public ContractBreachedEvent(Contract c, int severity, boolean notaryEmpowered) {
        this.contract = c; this.severity = severity; this.notaryEmpowered = notaryEmpowered;
    }
    public Contract getContract() { return contract; }
    public int getSeverity() { return severity; }
    public boolean isNotaryEmpowered() { return notaryEmpowered; }
}
