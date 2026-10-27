package de.jakob.lotm.beyonders.abilities.sun.notarization.contract.event;

import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.Contract;
import net.neoforged.bus.api.Event;

public class ContractFulfilledEvent extends Event {
    private final Contract contract;
    public ContractFulfilledEvent(Contract c) { this.contract = c; }
    public Contract getContract() { return contract; }
}
