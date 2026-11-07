package de.jakob.lotm.beyonders.abilities.justiciar;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.TheCorroderAbility;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.rendering.effectRendering.EffectIds;
import de.jakob.lotm.rendering.effectRendering.EffectManager;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ImprisonAbility extends Ability {

    public static final Set<UUID> IMPRISONED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, UUID[]> IMPRISON_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerLevel> PRISON_LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PRISON_GENERATIONS = new ConcurrentHashMap<>();

    private static final DustParticleOptions DUST_GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.75f, 0.0f), 1.3f);
    private static final DustParticleOptions DUST_PALE = new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.6f), 0.8f);

    public ImprisonAbility(String id) {
        super(id, 3f, "imprison");
        interactionRadius = 15;
        hasOptimalDistance = false;
        postsUsedAbilityEventManually = true;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("justiciar", 6));
    }

    @Override
    protected float getSpiritualityCost() {
        return 100;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity caster) {
        if (level.isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) level;

        if (IMPRISON_DATA.containsKey(caster.getUUID())) {
            cancelImprisonment(caster.getUUID());
            spawnReleaseEffect(serverLevel, caster.position());
            serverLevel.playSound(null, caster.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.7f);
            AbilityUtil.sendActionBar(caster, Component.literal("§6⚖ §eImprisonment §7released §6⚖"));
            return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(caster, (int) (15 * multiplier(caster)), 1.4f);
        if (target == null) return;
        if (TheCorroderAbility.isCorroding(target.getUUID())) return;

        int entitySeq = AbilityUtil.getSeqWithArt(caster, this);
        int targetSeq = BeyonderData.getSequence(target);

        if(AbilityUtil.isTargetSignificantlyStronger(entitySeq, targetSeq)) return;

        int duration = 20 * getDuration(entitySeq);

        final UUID targetId = target.getUUID();
        final UUID casterId = caster.getUUID();
        final UUID generation = UUID.randomUUID();
        IMPRISONED.add(targetId);

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 127, false, false));

        spawnImprisonEffect(serverLevel, target);
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2f, 1.1f);
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 0.4f);
        AbilityUtil.sendActionBar(caster, Component.literal("§6⚖ §e" + target.getType().getDescription().getString() + " §fimprisoned §6⚖"));

        UUID velTaskId = ServerScheduler.scheduleRepeating(0, 5, -1, () -> {
            if (TheCorroderAbility.isCorroding(targetId)) { releaseTarget(targetId); return; }
            Entity e = serverLevel.getEntity(targetId);
            if (!(e instanceof LivingEntity t) || !t.isAlive()) return;
            t.setDeltaMovement(Vec3.ZERO);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 127, false, false));
            t.setOnGround(true);
            Vec3 pos = t.position();
            t.hurtMarked = true;
            t.teleportTo(pos.x, pos.y, pos.z);
        }, serverLevel, () -> ownsPrison(casterId, targetId, generation));

        UUID vfxTaskId = ServerScheduler.scheduleRepeating(0, 8, duration, () -> {
            Entity e = serverLevel.getEntity(targetId);
            if (!(e instanceof LivingEntity t) || !t.isAlive()) return;
            Vec3 pos = t.position().add(0, 1, 0);
            ParticleUtil.spawnCircleParticles(serverLevel, DUST_GOLD, pos, 0.8, 12);
            ParticleUtil.spawnCircleParticles(serverLevel, DUST_PALE, pos.add(0, 0.5, 0), 0.5, 8);
            if (serverLevel.getGameTime() % 20 == 0) {
                ParticleUtil.spawnSphereParticles(serverLevel, DUST_GOLD, pos, 1.0, 10);
                ParticleUtil.spawnParticles(serverLevel, ParticleTypes.END_ROD, pos, 4, 0.5, 0.3);
            }
        }, serverLevel, () -> ownsPrison(casterId, targetId, generation));

        UUID effectTaskId = ServerScheduler.scheduleRepeating(0, 100, duration, () -> {
            Entity e = serverLevel.getEntity(targetId);
            if (!(e instanceof LivingEntity t) || !t.isAlive()) return;
            EffectManager.playEffect(EffectIds.IMPRISON, t.getX(), t.getY() + .3, t.getZ(), serverLevel);
        }, serverLevel, () -> ownsPrison(casterId, targetId, generation));

        UUID drainTaskId = ServerScheduler.scheduleRepeating(80, 80, duration, () -> {
            if (!ownsPrison(casterId, targetId, generation) || !caster.isAlive()) {
                cancelImprisonment(caster.getUUID());
                return;
            }
            float current = BeyonderData.getSpirituality(caster);
            if (current < 300) {
                cancelImprisonment(caster.getUUID());
                spawnReleaseEffect(serverLevel, caster.position());
                serverLevel.playSound(null, caster.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
                AbilityUtil.sendActionBar(caster, Component.literal("§c✗ §fInsufficient spirituality — imprisonment collapsed"));
                return;
            }
            BeyonderData.incrementSpirituality(caster, -300);
        }, serverLevel, () -> ownsPrison(casterId, targetId, generation) && caster.isAlive());

        UUID expiryTaskId = ServerScheduler.scheduleDelayed(duration, () -> {
            if (ownsPrison(casterId, targetId, generation)) {
                cancelImprisonment(casterId);
                spawnReleaseEffect(serverLevel, caster.position());
                AbilityUtil.sendActionBar(caster, Component.literal("§6⚖ §eImprisonment §7has ended §6⚖"));
            }
        }, serverLevel);

        IMPRISON_DATA.put(casterId, new UUID[]{targetId, velTaskId, vfxTaskId, effectTaskId, drainTaskId, expiryTaskId});
        PRISON_LEVELS.put(casterId, serverLevel);
        PRISON_GENERATIONS.put(casterId, generation);

        EffectManager.playEffect(EffectIds.IMPRISON, target.getX(), target.getY(), target.getZ(), serverLevel);
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel, caster.position(), caster, this, interactionFlags, 15, 0));
    }

    private void spawnImprisonEffect(ServerLevel level, LivingEntity target) {
        Vec3 pos = target.position().add(0, 1, 0);

        ServerScheduler.scheduleForDuration(0, 2, 30, () -> {
            for (int deg = 0; deg < 360; deg += 10) {
                double rad = Math.toRadians(deg);
                double x = pos.x + 1.2 * Math.cos(rad);
                double z = pos.z + 1.2 * Math.sin(rad);
                level.sendParticles(DUST_GOLD, x, pos.y, z, 1, 0, 0.05, 0, 0);
            }
            ParticleUtil.spawnSphereParticles(level, DUST_PALE, pos, 1.5, 8);
            ParticleUtil.spawnParticles(level, ParticleTypes.END_ROD, pos, 3, 0.6, 0.2);
        }, level);

        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    private void spawnReleaseEffect(ServerLevel level, Vec3 center) {
        ParticleUtil.spawnSphereParticles(level, DUST_GOLD, center.add(0, 1, 0), 1.5, 20);
        ParticleUtil.spawnSphereParticles(level, DUST_PALE, center.add(0, 1, 0), 2.0, 12);
        ParticleUtil.spawnParticles(level, ParticleTypes.END_ROD, center.add(0, 1, 0), 8, 0.8, 0.3);
    }

    private static void cancelImprisonment(UUID casterUUID) {
        UUID[] data = IMPRISON_DATA.remove(casterUUID);
        PRISON_LEVELS.remove(casterUUID);
        PRISON_GENERATIONS.remove(casterUUID);
        if (data == null) return;
        // A different Justiciar may still be maintaining an independent prison on this target.
        if (IMPRISON_DATA.values().stream().noneMatch(other -> other[0].equals(data[0]))) IMPRISONED.remove(data[0]);
        for (int i = 1; i < data.length; i++) {
            if (data[i] != null) ServerScheduler.cancel(data[i]);
        }
    }
    public static void releaseTarget(UUID targetId) {
        for (Map.Entry<UUID, UUID[]> e : new ArrayList<>(IMPRISON_DATA.entrySet())) {
            if (e.getValue()[0].equals(targetId)) cancelImprisonment(e.getKey());
        }
    }

    private static boolean ownsPrison(UUID caster, UUID target, UUID generation) {
        UUID[] data = IMPRISON_DATA.get(caster);
        return data != null && data[0].equals(target) && generation.equals(PRISON_GENERATIONS.get(caster));
    }

    private static void releaseEntity(UUID entity) {
        cancelImprisonment(entity);
        releaseTarget(entity);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide) releaseEntity(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { releaseEntity(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) { releaseEntity(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Covers unloaded mobs and non-player dimension transfers as well as players.
        for (var entry : new ArrayList<>(IMPRISON_DATA.entrySet())) {
            ServerLevel level = PRISON_LEVELS.get(entry.getKey());
            Entity caster = level == null ? null : level.getEntity(entry.getKey());
            Entity target = level == null ? null : level.getEntity(entry.getValue()[0]);
            if (!(caster instanceof LivingEntity owner) || !owner.isAlive()
                    || !(target instanceof LivingEntity prisoner) || !prisoner.isAlive()) cancelImprisonment(entry.getKey());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (UUID caster : new ArrayList<>(IMPRISON_DATA.keySet())) cancelImprisonment(caster);
        IMPRISONED.clear();
        PRISON_LEVELS.clear();
        PRISON_GENERATIONS.clear();
    }

    private static int getDuration(int seq){
        return switch (seq){
          case 6 -> 60;
          case 5 -> 70;
          case 4 -> 100;
          case 3 -> 120;
          case 2 -> 240;
          case 1 -> 300;
          case 0 -> 600;
          default -> 0;
        };
    }

}
