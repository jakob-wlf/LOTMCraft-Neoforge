package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.error.handler.ConceptStorage;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.*;

public class StealYourselfAbility extends Ability {

    public StealYourselfAbility(String id) {
        super(id, 6);
        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(2, 2, 3, 3, 4, 5, 6));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(1500f, 900f, 700f, 450f, 380f, 300f, 220f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 6));
    }

    @Override
    protected float getSpiritualityCost() {
        return 220;
    }

    @Override
    public boolean shouldUseAbility(LivingEntity entity) {
        return entity.isOnFire() || entity.getTicksFrozen() > 0
                || entity.getActiveEffects().stream().anyMatch(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL);
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        List<String> stolen = new ArrayList<>();

        if (entity.getRemainingFireTicks() > 0) {
            ConceptStorage.add(entity, ConceptStorage.Concept.FIRE, Math.max(1, entity.getRemainingFireTicks() / 20));
            entity.clearFire();
            stolen.add("fire");
        }

        if (entity.getTicksFrozen() > 0) {
            ConceptStorage.add(entity, ConceptStorage.Concept.FROST, Math.max(1, entity.getTicksFrozen() / 20));
            entity.setTicksFrozen(0);
            stolen.add("frost");
        }

        for (MobEffectInstance effect : new ArrayList<>(entity.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) continue;
            if (effect.isInfiniteDuration()) continue;
            ConceptStorage.addEffect(entity, new MobEffectInstance(effect));
            entity.removeEffect(effect.getEffect());
            stolen.add(effect.getEffect().value().getDisplayName().getString());
        }

        if (stolen.isEmpty()) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.steal_yourself.nothing").withColor(ErrorUtil.ERROR_COLOR));
            return;
        }

        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 40, 0.35, 0.6, 0.35, 0.04);
        serverLevel.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 15, 0.3, 0.5, 0.3, 0.01);
        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.2f);
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.steal_yourself.success", String.join(", ", stolen)).withColor(ErrorUtil.ERROR_COLOR));
    }
}
