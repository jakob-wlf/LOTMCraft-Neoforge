package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class CombatProficiencyAbility extends PassiveAbility {

    public CombatProficiencyAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 9));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (level.isClientSide) return;

        var mainHand = entity.getMainHandItem();
        if (!(mainHand.is(ItemTags.SWORDS) || mainHand.getItem() instanceof SwordItem)) return;

        MobEffectInstance current = entity.getEffect(MobEffects.DAMAGE_BOOST);
        if (current != null && (current.getAmplifier() > 0 || current.getDuration() > 20)) return;

        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 15, 0, false, false, false));
    }
}
