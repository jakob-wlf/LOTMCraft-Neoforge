package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.AbilityCooldownComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.entity.custom.ability_entities.TimeChangeEntity;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.helper.RegionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class TimeHandler {

    private static final Map<UUID, Long> stolenTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> giftedTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> cooldownAccumulators = new ConcurrentHashMap<>();

    private static final List<Recording> recordings = new ArrayList<>();
    private static final List<StoneClock> clocks = new ArrayList<>();

    public static final float STOLEN_FACTOR = 0.5f;
    public static final float GIFTED_FACTOR = 1.6f;


    public static void stealTime(LivingEntity target, int ticks) {
        long until = target.level().getGameTime() + ticks;
        stolenTime.merge(target.getUUID(), until, Math::max);
        giftedTime.remove(target.getUUID());
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 1, false, true, true));
    }

    public static boolean giftTime(LivingEntity target, int ticks) {
        if (stolenTime.remove(target.getUUID()) != null) {
            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            target.removeEffect(MobEffects.DIG_SLOWDOWN);
            return true;
        }
        long until = target.level().getGameTime() + ticks;
        giftedTime.merge(target.getUUID(), until, Math::max);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 1, false, true, true));
        return false;
    }

    public static boolean isTimeStolen(LivingEntity entity) {
        Long until = stolenTime.get(entity.getUUID());
        return until != null && until > entity.level().getGameTime();
    }

    private static boolean isTimeGifted(LivingEntity entity) {
        Long until = giftedTime.get(entity.getUUID());
        return until != null && until > entity.level().getGameTime();
    }

    public static float getProcessMultiplier(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return 1f;
        return isTimeStolen(entity) ? STOLEN_FACTOR : 1f;
    }

    public static float getCooldownMultiplier(LivingEntity entity) {
        float multiplier = 1f;
        float field = TimeChangeEntity.getTimeMultiplier(entity.getUUID());
        if (entity instanceof Player || field > 1f) multiplier *= field;
        if (isTimeStolen(entity)) multiplier *= STOLEN_FACTOR;
        if (isTimeGifted(entity)) multiplier *= GIFTED_FACTOR;
        return multiplier;
    }

    public static void tickCooldowns(LivingEntity entity, AbilityCooldownComponent component) {
        if (entity.level().isClientSide) {
            component.tick();
            return;
        }

        float multiplier = getCooldownMultiplier(entity);
        if (multiplier == 1f) {
            component.tick();
            cooldownAccumulators.remove(entity.getUUID());
            return;
        }

        float accumulated = cooldownAccumulators.getOrDefault(entity.getUUID(), 0f) + multiplier;
        int steps = (int) accumulated;
        cooldownAccumulators.put(entity.getUUID(), accumulated - steps);
        for (int i = 0; i < Math.min(steps, 8); i++) {
            component.tick();
        }
    }

    private record Snapshot(Vec3 pos, float yRot, float xRot, float health, int fire) {}

    private static class Recording {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final double radius;
        final RegionSnapshot blocks;
        final Map<UUID, List<Snapshot>> history = new HashMap<>();
        final long endRecording;
        long rewindStart = -1;
        static final int REWIND_TICKS = 40;
        UUID effectId;

        Recording(ServerLevel level, UUID owner, Vec3 center, double radius, int duration) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.radius = radius;
            this.blocks = new RegionSnapshot(level, BlockPos.containing(center), 10);
            this.blocks.captureSphere(level, BlockPos.containing(center), 10);
            this.endRecording = level.getGameTime() + duration;
        }
    }

    public static boolean startOrTriggerReverse(ServerLevel level, LivingEntity owner, double radius, int duration) {
        for (Recording recording : recordings) {
            if (recording.owner.equals(owner.getUUID()) && recording.rewindStart < 0) {
                beginRewind(recording);
                return false;
            }
        }

        Recording recording = new Recording(level, owner.getUUID(), owner.position(), radius, duration);
        recording.effectId = FxHandler.playCancellable(level, "time_clock_reverse", owner.position().add(0, owner.getBbHeight() + 2.2, 0), 1.6);
        recordings.add(recording);
        return true;
    }

    private static void beginRewind(Recording recording) {
        recording.rewindStart = recording.level.getGameTime();
        recording.level.playSound(null, BlockPos.containing(recording.center), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, 0.5f);
    }

    private static void tickRecording(Recording recording) {
        ServerLevel level = recording.level;
        long now = level.getGameTime();

        if (recording.rewindStart < 0) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, net.minecraft.world.phys.AABB.ofSize(recording.center, recording.radius * 2, recording.radius * 2, recording.radius * 2),
                    e -> e.isAlive() && e.position().distanceTo(recording.center) <= recording.radius)) {
                recording.history.computeIfAbsent(entity.getUUID(), k -> new ArrayList<>())
                        .add(new Snapshot(entity.position(), entity.getYRot(), entity.getXRot(), entity.getHealth(), entity.getRemainingFireTicks()));
            }
            if (now >= recording.endRecording) {
                beginRewind(recording);
            }
            return;
        }

        long elapsed = now - recording.rewindStart;
        float progress = Math.min(1f, elapsed / (float) Recording.REWIND_TICKS);

        for (Map.Entry<UUID, List<Snapshot>> entry : recording.history.entrySet()) {
            Entity raw = level.getEntity(entry.getKey());
            if (!(raw instanceof LivingEntity entity) || !entity.isAlive()) continue;
            List<Snapshot> history = entry.getValue();
            if (history.isEmpty()) continue;

            int index = Math.max(0, (int) ((history.size() - 1) * (1f - progress)));
            Snapshot snapshot = history.get(index);
            entity.teleportTo(snapshot.pos.x, snapshot.pos.y, snapshot.pos.z);
            entity.setYRot(snapshot.yRot);
            entity.setXRot(snapshot.xRot);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;

            if (elapsed % 4 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
            }

            if (progress >= 1f) {
                Snapshot first = history.get(0);
                entity.setHealth(Math.min(entity.getMaxHealth(), Math.max(entity.getHealth(), first.health)));
                entity.setRemainingFireTicks(first.fire);
            }
        }

        if (progress >= 1f) {
            recording.blocks.restore(level);
            FxHandler.cancel(level, recording.effectId);
            recording.history.clear();
        }
    }

    private static class StoneClock {
        final ServerLevel level;
        final UUID owner;
        final Vec3 pos;
        final long expires;
        final UUID effectId;

        StoneClock(ServerLevel level, UUID owner, Vec3 pos, long expires, UUID effectId) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.expires = expires;
            this.effectId = effectId;
        }
    }

    public static void summonClock(ServerLevel level, LivingEntity owner, Vec3 pos, int duration) {
        clocks.removeIf(clock -> {
            if (!clock.owner.equals(owner.getUUID())) return false;
            FxHandler.cancel(clock.level, clock.effectId);
            return true;
        });
        UUID effectId = FxHandler.playCancellable(level, "time_clock_summon", pos, 2.2);
        clocks.add(new StoneClock(level, owner.getUUID(), pos, level.getGameTime() + duration, effectId));
    }

    private static void tickClock(StoneClock clock) {
        if (clock.level.getGameTime() % 20 != 0) return;
        Entity ownerRaw = clock.level.getEntity(clock.owner);
        LivingEntity owner = ownerRaw instanceof LivingEntity living ? living : null;

        int leeched = 0;
        for (LivingEntity entity : AbilityUtil.getNearbyEntities(owner, clock.level, clock.pos, 14)) {
            if (owner != null && (AllyUtil.areAllies(owner, entity) || !AbilityUtil.mayTarget(owner, entity))) continue;
            if (owner != null && AbilityUtil.isTargetSignificantlyStronger(owner, entity)) continue;
            entity.getData(ModAttachments.COOLDOWN_COMPONENT).addToAll(10);
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, true));
            clock.level.sendParticles(ParticleTypes.SCULK_SOUL, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
            leeched++;
        }

        if (owner != null && leeched > 0) {
            owner.getData(ModAttachments.COOLDOWN_COMPONENT).addToAll(-5 * leeched);
            ConceptStorage.add(owner, ConceptStorage.Concept.TIME, leeched);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!recordings.isEmpty()) {
            for (Recording recording : new ArrayList<>(recordings)) {
                tickRecording(recording);
                if (recording.rewindStart >= 0 && recording.level.getGameTime() - recording.rewindStart > Recording.REWIND_TICKS) {
                    recordings.remove(recording);
                }
            }
        }

        if (!clocks.isEmpty()) {
            for (StoneClock clock : new ArrayList<>(clocks)) {
                if (clock.level.getGameTime() >= clock.expires) {
                    FxHandler.cancel(clock.level, clock.effectId);
                    clocks.remove(clock);
                    continue;
                }
                tickClock(clock);
            }
        }

        if (event.getServer().getTickCount() % 200 == 0) {
            long now = event.getServer().overworld().getGameTime();
            stolenTime.values().removeIf(until -> until < now - 20);
            giftedTime.values().removeIf(until -> until < now - 20);
        }
    }

    public static void dissolveOwnedTime(UUID owner) {
        clocks.removeIf(clock -> {
            if (!clock.owner.equals(owner)) return false;
            FxHandler.cancel(clock.level, clock.effectId);
            return true;
        });
        recordings.removeIf(recording -> {
            if (!recording.owner.equals(owner)) return false;
            FxHandler.cancel(recording.level, recording.effectId);
            return true;
        });
        giftedTime.remove(owner);
    }

    public static void notifyGift(ServerPlayer player, LivingEntity target, boolean restored) {
        player.level().playSound(null, target.blockPosition(), restored ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.4f);
    }
}
