package de.jakob.lotm.util.helper;

import de.jakob.lotm.beyonders.abilities.justiciar.BalancingAbility.BalancingZone;
import de.jakob.lotm.beyonders.abilities.justiciar.BalancingAbility;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/** Brass geometry conveys imposed Order without obscuring the battlefield. */
public final class BalancingVisuals {
    private static final DustParticleOptions BRASS = new DustParticleOptions(new Vector3f(0.9f, 0.64f, 0.21f), 1.5f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.88f, 0.5f), 1.2f);

    private BalancingVisuals() {}

    public static void activate(BalancingZone zone, LivingEntity caster, LivingEntity target) {
        activationSound(zone, false);
        RingEffectManager.createRingForAll(zone.center.add(0, 0.6, 0), (float) zone.radius, 32,
                1f, 0.74f, 0.26f, 0.4f, 0.4f, 1.2f, 1f, false, true, zone.level);
        for (LivingEntity participant : caster == target ? List.of(caster) : List.of(caster, target)) {
            Vec3 feet = participant.position().add(0, 0.15, 0);
            ParticleUtil.spawnCircleParticles(zone.level, BRASS, feet, 1.2, 24);
            zone.level.sendParticles(ParticleTypes.ENCHANT, participant.getX(), participant.getY() + 1,
                    participant.getZ(), 12, 0.4, 0.7, 0.4, 0.05);
        }
        activationSequence(zone, caster, target);
        boundary(zone);
    }

    /** A brief local seal precedes a field-wide wave, so activation reads even in first person. */
    private static void activationSequence(BalancingZone zone, LivingEntity caster, LivingEntity target) {
        Vec3 sealCenter = caster.position().add(0, 0.15, 0);
        int[] elapsed = {0};
        ServerScheduler.scheduleForDuration(0, 2, 32, () -> {
            if (!zone.isActive() || !BalancingAbility.ACTIVE_ZONES.contains(zone)
                    || !caster.isAlive() || !target.isAlive()
                    || caster.level() != zone.level || target.level() != zone.level) return;
            int age = elapsed[0];
            elapsed[0] += 2;
            List<ServerPlayer> viewers = zone.level.players().stream()
                    .filter(player -> player.position().distanceToSqr(zone.center) <= Math.pow(zone.radius + 32, 2))
                    .toList();
            if (viewers.isEmpty()) return;

            if (age <= 20) {
                // Opposing concentric rings and straight uprights evoke a formal brass seal.
                for (int i = 0; i < 24; i++) {
                    double angle = 2 * Math.PI * i / 24 + age * 0.035;
                    point(zone.level, viewers, BRASS,
                            sealCenter.add(Math.cos(angle) * 2.6, 0, Math.sin(angle) * 2.6));
                    if (i % 2 == 0) {
                        point(zone.level, viewers, GOLD,
                                sealCenter.add(Math.cos(-angle) * 1.6, 0.12, Math.sin(-angle) * 1.6));
                    }
                }
                double pillarHeight = Math.min(3.5, 0.5 + age * 0.3);
                for (int pillar = 0; pillar < 4; pillar++) {
                    double angle = pillar * Math.PI / 2;
                    Vec3 base = sealCenter.add(Math.cos(angle) * 2.6, 0, Math.sin(angle) * 2.6);
                    for (int step = 0; step < 6; step++) {
                        point(zone.level, viewers, step % 2 == 0 ? BRASS : GOLD,
                                base.add(0, pillarHeight * step / 5, 0));
                    }
                }
            }

            if (age >= 6) {
                double progress = Math.min(1, (age - 4) / 26.0);
                double waveRadius = zone.radius * progress;
                for (int i = 0; i < 64; i++) {
                    double angle = 2 * Math.PI * i / 64;
                    point(zone.level, viewers, GOLD, at(zone, angle, waveRadius, 0.8));
                    if (i % 4 == 0) {
                        point(zone.level, viewers, BRASS, at(zone, angle, waveRadius, 2.2));
                    }
                }
            }
            if (age == 30) {
                activationSound(zone, true);
                // The wave resolves into the eight seals which persist on the boundary.
                for (int pillar = 0; pillar < 8; pillar++) {
                    double angle = pillar * Math.PI / 4;
                    for (int step = 0; step < 8; step++) {
                        point(zone.level, viewers, GOLD, at(zone, angle, zone.radius, step * 0.6));
                    }
                }
            }
        }, zone.level);
    }

    private static void activationSound(BalancingZone zone, boolean resolved) {
        double audibleRadius = zone.radius + 32;
        for (ServerPlayer player : zone.level.players()) {
            double distance = player.position().distanceTo(zone.center);
            if (distance > audibleRadius) continue;
            float volume = (float) (1 - 0.65 * distance / audibleRadius);
            // Explicit delivery keeps the cue audible across the field without excessive world volume.
            if (resolved) {
                player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                        0.7f * volume, 1.1f);
            } else {
                player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS,
                        1.0f * volume, 0.65f);
                player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                        0.5f * volume, 0.7f);
            }
        }
    }

    /** Refreshed twice a second; all particles expire naturally after early cancellation. */
    public static void boundary(BalancingZone zone) {
        List<ServerPlayer> viewers = zone.level.players().stream()
                .filter(player -> player.position().distanceToSqr(zone.center) <= Math.pow(zone.radius + 32, 2))
                .toList();
        if (viewers.isEmpty()) return;
        // Short ring segments remain legible even when the particle setting is reduced.
        RingEffectManager.createRingForAll(zone.center.add(0, 0.12, 0), (float) zone.radius, 12,
                0.9f, 0.64f, 0.21f, 0.22f, 0.18f, 0.15f, 100f, false, true, zone.level);
        for (int i = 0; i < 64; i++) {
            double angle = 2 * Math.PI * i / 64;
            point(zone.level, viewers, BRASS, at(zone, angle, zone.radius, 0.25));
        }
        // Eight upright geometric seals mark the imposed boundary, rather than a solid wall.
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            Vec3 base = at(zone, angle, zone.radius, 0.5);
            Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            for (int height = 0; height < 4; height++) {
                point(zone.level, viewers, GOLD, base.add(0, height, 0));
            }
            for (int side : new int[]{-1, 1}) {
                point(zone.level, viewers, BRASS, base.add(tangent.scale(side * 0.7)).add(0, 2, 0));
                point(zone.level, viewers, GOLD, base.add(tangent.scale(side * 0.7)).add(0, 3, 0));
            }
        }
        // A sparse moving crown makes the field readable from above without filling the interior.
        double phase = zone.level.getGameTime() * 0.012;
        for (int i = 0; i < 16; i++) {
            point(zone.level, viewers, GOLD, at(zone, phase + 2 * Math.PI * i / 16, zone.radius, 5));
        }
    }

    public static void repel(ServerLevel level, LivingEntity entity) {
        if (entity.tickCount % 5 != 0) return;
        Vec3 center = entity.position().add(0, Math.min(1, entity.getBbHeight() * 0.5), 0);
        ParticleUtil.spawnCircleParticles(level, BRASS, center, entity.getBbWidth() * 0.5 + 0.25, 8);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 2, 0.25, 0.4, 0.25, 0.015);
    }

    public static void end(BalancingZone zone) {
        RingEffectManager.createRingForAll(zone.center.add(0, 0.15, 0), (float) zone.radius, 16,
                1f, 0.88f, 0.5f, 0.22f, 0.25f, 0.2f, 100f, false, true, zone.level);
    }

    private static Vec3 at(BalancingZone zone, double angle, double radius, double height) {
        return zone.center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static void point(ServerLevel level, List<ServerPlayer> viewers, ParticleOptions particle, Vec3 position) {
        for (ServerPlayer player : viewers) {
            // The 48-block rim must remain visible from the middle of the battlefield.
            level.sendParticles(player, particle, true, position.x, position.y, position.z, 1, 0, 0, 0, 0);
        }
    }
}
