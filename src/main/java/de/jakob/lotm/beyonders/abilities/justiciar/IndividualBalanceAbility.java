package de.jakob.lotm.beyonders.abilities.justiciar;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IndividualBalanceAbility extends Ability {
    private static final Map<UUID, Balance> BALANCES = new HashMap<>();
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f,.8f,.3f),1.2f);

    public IndividualBalanceAbility(String id) {
        super(id, 40f);
        interactionRadius = 48;
        hasOptimalDistance = true;
        optimalDistance = 16f;
        postsUsedAbilityEventManually = true;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() { return Map.of("justiciar", 2); }
    @Override
    protected float getSpiritualityCost() { return 2000; }

    @Override
    public void onAbilityUse(Level level, LivingEntity caster) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        LivingEntity target = AbilityUtil.getTargetEntity(caster, 48, 1.5f, true);
        if (target == null || !BeyonderData.isBeyonder(target)) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.individual_balance.not_beyonder").withStyle(ChatFormatting.RED));
            return;
        }
        int casterSequence = AbilityUtil.getSeqWithArt(caster, this);
        int targetSequence = BeyonderData.getSequence(target);
        int duration = durationTicks(casterSequence, targetSequence);
        double power = BeyonderData.getMultiplier(caster);
        Balance balance = new Balance(caster, target, serverLevel,
                serverLevel.getServer().overworld().getGameTime() + duration, power);
        BALANCES.put(target.getUUID(), balance);
        Component notice = Component.translatable("ability.lotmcraft.individual_balance.equalized", target.getDisplayName(),
                String.format(java.util.Locale.ROOT,"%.1f",duration / 20.0)).withStyle(ChatFormatting.GOLD);
        Component received = Component.translatable("ability.lotmcraft.individual_balance.received", caster.getDisplayName(),
                String.format(java.util.Locale.ROOT,"%.1f",duration / 20.0)).withStyle(ChatFormatting.GOLD);
        caster.sendSystemMessage(notice);
        target.sendSystemMessage(received);
        AbilityUtil.sendActionBar(target, received);
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1f,.65f);
        ParticleUtil.drawParticleLine(serverLevel, GOLD, caster.getEyePosition(), target.getEyePosition(), .25,1);
        ServerScheduler.scheduleForDuration(0, 10, duration, () -> {
            if (BALANCES.get(target.getUUID()) != balance || !balance.active()) return;
            ParticleUtil.spawnCircleParticles(serverLevel, GOLD, target.position().add(0,1,0), 1.1, 20);
        }, () -> {
            if (BALANCES.get(target.getUUID()) != balance) return;
            BALANCES.remove(target.getUUID());
            announceEnd(balance);
        }, serverLevel);
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel,target.position(),caster,this,interactionFlags,3,40));
    }

    public static int durationTicks(int casterSequence, int targetSequence) {
        return de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceRules.individualDurationTicks(casterSequence, targetSequence);
    }

    public static double balancedMultiplier(LivingEntity entity, double original) {
        if (entity.level().isClientSide) return original;
        Balance balance = BALANCES.get(entity.getUUID());
        if (balance == null) return original;
        if (!balance.active() || balance.target != entity) {
            BALANCES.remove(entity.getUUID()); announceEnd(balance); return original;
        }
        return balance.power;
    }

    public static void expireBalances() {
        BALANCES.values().removeIf(balance -> {
            if (balance.active()) return false;
            announceEnd(balance); return true;
        });
    }
    public static void clearBalances() { BALANCES.clear(); }

    private static void announceEnd(Balance balance) {
        LivingEntity target = balance.target;
        if (target.isAlive() && !target.isRemoved() && target.level() instanceof ServerLevel level) {
            level.playSound(null,target.blockPosition(),SoundEvents.BEACON_DEACTIVATE,SoundSource.PLAYERS,.7f,1.2f);
            target.sendSystemMessage(Component.translatable("ability.lotmcraft.individual_balance.ended").withStyle(ChatFormatting.GOLD));
        }
    }

    private record Balance(LivingEntity caster, LivingEntity target, ServerLevel level, long expiry, double power) {
        boolean active() {
            return caster.isAlive() && target.isAlive() && !caster.isRemoved() && !target.isRemoved()
                    && caster.level() == level && target.level() == level
                    && level.getServer().overworld().getGameTime() < expiry;
        }
    }
}
