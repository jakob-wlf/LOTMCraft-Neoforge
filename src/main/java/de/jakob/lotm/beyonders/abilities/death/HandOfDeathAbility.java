package de.jakob.lotm.beyonders.abilities.death;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class HandOfDeathAbility extends ToggleAbility {
    private static final List<Float> SOUL_DAMAGE = List.of(66f, 48f, 40f, 30f);

    private static final Set<UUID> activeCasters = ConcurrentHashMap.newKeySet();

    public HandOfDeathAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("death", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 20;
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        activeCasters.add(entity.getUUID());

        Vec3 center = entity.position().add(0, 1, 0);
        ParticleUtil.spawnSphereParticles(serverLevel, ParticleTypes.SOUL, center, 1.2, 50, 0.35);
        ParticleUtil.spawnCircleParticles(serverLevel, ParticleTypes.SOUL, entity.position(), 1.5, 28);
        serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.45f);
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (InteractionHandler.isInteractionPossibleStrictlyHigher(new Location(entity.position(), serverLevel), "purification", BeyonderData.getSequence(entity), -1)) {
            cancel(serverLevel, entity);
            return;
        }

        ParticleUtil.spawnParticles(serverLevel, ParticleTypes.SOUL, entity.position().add(0, 1, 0), 3, 0.4, 0.01);
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        activeCasters.remove(entity.getUUID());
    }

    @SubscribeEvent
    public static void onMeleeHit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        DamageSource source = event.getSource();
        if (!source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)) return;
        if (!(source.getDirectEntity() instanceof LivingEntity caster)) return;
        if (!activeCasters.contains(caster.getUUID())) return;

        int seq = BeyonderData.getSequence(caster);
        if (seq < 0 || seq >= SOUL_DAMAGE.size()) return;

        float damage = SOUL_DAMAGE.get(seq) * (float) BeyonderData.getMultiplier(caster);

        // Next tick, so the melee hit's i-frames don't swallow the soul damage
        ServerScheduler.scheduleDelayed(1, () -> {
            if (!target.isAlive()) return;

            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.source(serverLevel, ModDamageTypes.SOUL_BASED, caster), damage);

            Vec3 center = target.position().add(0, 1, 0);
            ParticleUtil.spawnSphereParticles(serverLevel, ParticleTypes.SOUL, center, 0.8, 30, 0.3);
            ParticleUtil.spawnParticles(serverLevel, ParticleTypes.LARGE_SMOKE, center, 8, 0.5, 0.02);
            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_SAND_BREAK, SoundSource.PLAYERS, 1.0f, 0.55f);
        }, serverLevel);
    }
}
