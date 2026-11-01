package de.jakob.lotm.beyonders.abilities.sun.notarization.ability;

import de.jakob.lotm.beyonders.abilities.sun.notarization.authentication.Authentication;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractManager;
import de.jakob.lotm.beyonders.abilities.sun.notarization.net.Candidate;
import de.jakob.lotm.gui.custom.notarization.ContractMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.List;

public final class NotaryAbilities {
    private NotaryAbilities() {}

    public static void openContractMenu(ServerPlayer caster) {
        List<Candidate> candidates = ContractManager.candidates(caster);
        caster.openMenu(
                new SimpleMenuProvider((id, inv, p) -> new ContractMenu(id, candidates),
                        Component.translatable("contractnotary.screen.title")),
                buf -> Candidate.LIST_CODEC.encode(buf, candidates));
    }

    public static void authenticate(ServerPlayer caster) {
        Authentication.authenticate(caster);
    }
}
