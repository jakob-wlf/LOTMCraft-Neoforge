package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.door.SpaceFragmentationAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PositionFragmentationAbility extends PositionAuthorityAbility {

    public PositionFragmentationAbility(String id) {
        super(id, 30);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(15, 30));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(15000f, 25000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 1));
    }

    @Override
    protected float getSpiritualityCost() {
        return 25000;
    }

    @Override
    protected String command() {
        return "fragmentation";
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq) {
        if (LOTMCraft.abilityHandler.getById("space_fragmentation_ability") instanceof SpaceFragmentationAbility fragmentation) {
            fragmentation.shatterSpace(target.serverLevel(), caster, target.position());
        }
    }
}
