package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class SwindlerCharmAbility extends PassiveAbility {

    public SwindlerCharmAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 8));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (level.isClientSide) return;

        MobEffectInstance current = entity.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (current != null && (current.getAmplifier() > 0 || current.getDuration() > 60)) return;

        entity.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 3, 0, false, false, false));
    }
}
