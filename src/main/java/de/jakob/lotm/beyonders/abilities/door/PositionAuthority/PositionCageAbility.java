package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.beyonders.abilities.door.SpatialCageAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedList;
import java.util.List;

public class PositionCageAbility extends PositionAuthorityAbility {

    public PositionCageAbility(String id) {
        super(id, 40);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(10, 20, 40));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(5000f, 10000f, 20000f));
    }

    @Override
    protected float getSpiritualityCost() {
        return 20000;
    }

    @Override
    protected String command() {
        return "cage";
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq) {
        SpatialCageAbility.place(caster, target.serverLevel(), target.position().add(0, target.getBbHeight() / 2, 0), false, seq);
    }
}
