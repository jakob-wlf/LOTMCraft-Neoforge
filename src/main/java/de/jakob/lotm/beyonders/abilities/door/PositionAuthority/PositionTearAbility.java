package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.door.SpaceTearingAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedList;
import java.util.List;

public class PositionTearAbility extends PositionAuthorityAbility {

    public PositionTearAbility(String id) {
        super(id, 15);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(5, 10, 15));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3000f, 6000f, 10000f));
    }

    @Override
    protected float getSpiritualityCost() {
        return 10000;
    }

    @Override
    protected String command() {
        return "tear";
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq) {
        if (LOTMCraft.abilityHandler.getById("space_tearing_ability") instanceof SpaceTearingAbility tearing) {
            tearing.tear(target.serverLevel(), caster, target.position().add(0, target.getBbHeight() / 2, 0));
        }
    }
}
