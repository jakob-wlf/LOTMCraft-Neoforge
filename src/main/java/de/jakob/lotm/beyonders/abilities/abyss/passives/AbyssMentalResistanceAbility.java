package de.jakob.lotm.beyonders.abilities.abyss.passives;

import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbilityHandler;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;


public class AbyssMentalResistanceAbility extends PassiveAbility{
    public AbyssMentalResistanceAbility(String id) {
        super(id);
    }
    @Override
    public Map<String, Integer> getRequirements() {return new HashMap<>(Map.of("abyss", 7));}
    @Override
    public void tick(Level level, LivingEntity entity) {
    }
    public static float reduceSanityDrain(LivingEntity entity, float drain) {
        PassiveAbility mental = PassiveAbilityHandler.getById("abyss_mental_resistance_ability");
        boolean hasMental = mental != null && mental.shouldApplyTo(entity) && BeyonderData.getSequence(entity) > 2;
        boolean hasMalice = MaliceAuthorityAbility.isActiveFor(entity);
        if (hasMental && hasMalice) return drain * 0.5f;
        if (hasMental || hasMalice) return drain * 0.75f;
        return drain;
    }
}