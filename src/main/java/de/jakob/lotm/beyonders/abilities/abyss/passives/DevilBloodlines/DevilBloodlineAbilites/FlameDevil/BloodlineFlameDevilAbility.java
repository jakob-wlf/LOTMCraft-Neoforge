package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.FlameDevil;

import de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class BloodlineFlameDevilAbility extends PassiveAbility {

    public BloodlineFlameDevilAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("abyss", 6));
    }

    @Override
    public boolean meetsBloodlineRequirement(LivingEntity entity) {
        return DevilBloodlineAbility.hasBloodline(entity, DevilBloodlineAbility.Bloodline.FLAME_DEVIL);
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }


}