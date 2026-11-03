package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAuthorityAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class ConcealNameAbility extends Ability {

    public ConcealNameAbility(String id) {
        super(id, 5);
        canBeUsedByNPC = false;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 0));
    }

    @Override
    protected float getSpiritualityCost() {
        return 8000;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide) return;
        ConcealmentAuthorityAbility.concealName(entity);
    }
}
