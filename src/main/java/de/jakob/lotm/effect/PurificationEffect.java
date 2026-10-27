package de.jakob.lotm.effect;


import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

public class PurificationEffect extends MobEffect {


    protected PurificationEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if(BeyonderData.isEvilPathway(livingEntity) || livingEntity.getType().is(EntityTypeTags.UNDEAD)) {
            livingEntity.hurt(ModDamageTypes.source(livingEntity.level(), ModDamageTypes.PURIFICATION), (amplifier + 1) * 2.5f);
            return true;
        }
        livingEntity.setTicksFrozen(0);
        livingEntity.removeEffect(MobEffects.WITHER);
        livingEntity.removeEffect(MobEffects.POISON);
        livingEntity.removeEffect(MobEffects.CONFUSION);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}