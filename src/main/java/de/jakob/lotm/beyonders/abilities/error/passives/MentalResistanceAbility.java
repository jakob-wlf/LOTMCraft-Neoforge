package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class MentalResistanceAbility extends PassiveAbility {

    public MentalResistanceAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 6));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    public static float getSanityAttackMultiplier(LivingEntity entity) {
        if (entity.level().isClientSide || !ErrorUtil.hasPassive(entity, "mental_resistance_ability")) return 1f;

        int seq = BeyonderData.getSequence(entity);
        if (seq <= 2) return 0.35f;
        if (seq <= 4) return 0.45f;
        return 0.6f;
    }
}
