package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class QuickReactionAbility extends PassiveAbility {

    private static final Random RANDOM = new Random();

    public QuickReactionAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 8));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    public static boolean isPhysical(DamageSource source) {
        if (source.getDirectEntity() == null) return false;
        return source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.STING);
    }

    public static float getDodgeChance(int defenderSeq, int attackerSeq) {
        float chance = 0.15f + 0.05f * (attackerSeq - defenderSeq);
        return Math.max(0f, Math.min(0.6f, chance));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (!(defender.level() instanceof ServerLevel level)) return;
        if (!isPhysical(event.getSource())) return;
        if (!ErrorUtil.hasPassive(defender, "quick_reaction_ability")) return;

        int attackerSeq = event.getSource().getEntity() instanceof LivingEntity attacker
                ? ErrorUtil.seqOf(attacker) : LOTMCraft.NON_BEYONDER_SEQ;

        if (RANDOM.nextFloat() >= getDodgeChance(ErrorUtil.seqOf(defender), attackerSeq)) return;

        event.setCanceled(true);

        // a quick sidestep away from the blow
        Vec3 from = event.getSource().getSourcePosition();
        if (from != null) {
            Vec3 away = defender.position().subtract(from).multiply(1, 0, 1);
            Vec3 side = new Vec3(-away.z, 0, away.x).normalize().scale(RANDOM.nextBoolean() ? 0.45 : -0.45);
            defender.setDeltaMovement(defender.getDeltaMovement().add(side));
            defender.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.CLOUD, defender.getX(), defender.getY() + 0.1, defender.getZ(), 6, 0.25, 0.05, 0.25, 0.01);
        level.playSound(null, defender.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6f, 1.6f);
    }
}
