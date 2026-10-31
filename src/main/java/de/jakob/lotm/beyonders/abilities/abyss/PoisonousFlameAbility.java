package de.jakob.lotm.beyonders.abilities.abyss;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.DamageLookup;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

public class PoisonousFlameAbility extends Ability {
    public PoisonousFlameAbility(String id) {
        super(id, .8f, "burning", "poison");

        hasOptimalDistance = true;
        optimalDistance = 1.75f;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of(
                "abyss", 8
        ));
    }

    @Override
    public float getSpiritualityCost() {
        return 12;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        Vec3 startPos = entity.getEyePosition().subtract(0, .2, 0).add(entity.getLookAngle().normalize());
        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.BLAZE_SHOOT, entity.getSoundSource(), 2.0f, .5f);

        if (InteractionHandler.isInteractionPossible(new Location(startPos, level), "water",
                AbilityUtil.getSeqWithArt(entity, this))) {
            level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.FIRE_EXTINGUISH, entity.getSoundSource(), 2.0f, .5f);
            ParticleUtil.spawnParticles((ServerLevel) level, ParticleTypes.LARGE_SMOKE, startPos, 10, .2, .1);
            ParticleUtil.spawnParticles((ServerLevel) level, ParticleTypes.FALLING_WATER, startPos, 50, .2, .1);
            return;
        }

        ServerScheduler.scheduleDelayed(4, () -> {
            Vec3 look = entity.getLookAngle().normalize();
            FxHandler.play((ServerLevel) level, "poisonous_flame", entity.getEyePosition().subtract(0, .25, 0).add(look.scale(.5)),
                    Mth.clamp(multiplier(entity), 1, 3), FxHandler.pointing(look));
        });

        // Purification neutralizes the poison effect
        Location flameLoc = new Location(startPos, level);
        int seq = AbilityUtil.getSeqWithArt(entity, this);
        boolean purified = InteractionHandler.isInteractionPossible(flameLoc, "purification", seq);

        AbilityUtil.damageNearbyEntities(
                (ServerLevel) level,
                entity,
                2.75* multiplier(entity),
                DamageLookup.lookupDamage(8, .9) *
                        multiplier(entity),
                startPos,
                true,
                false,
                true,
                0,
                20 * 3,
                ModDamageTypes.source(level, ModDamageTypes.BEYONDER_GENERIC, entity)
        );

        if(!purified) {
            AbilityUtil.addPotionEffectToNearbyEntities(
                    (ServerLevel) level, entity, 3, startPos, new MobEffectInstance(MobEffects.POISON, 20 * 8* (int) Math.max(multiplier(entity)/2,1), 2, false, true)
            );
        }

        BlockState block = level.getBlockState(BlockPos.containing(startPos));
        if(block.isAir()) {
            level.setBlockAndUpdate(BlockPos.containing(startPos), Blocks.LIGHT.defaultBlockState());
        }

        ServerScheduler.scheduleDelayed(25, () -> level.setBlockAndUpdate(BlockPos.containing(startPos), Blocks.AIR.defaultBlockState()));
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent((ServerLevel) level, startPos, entity, this, interactionFlags, 2.4f, interactionCacheTicks));
    }
}
