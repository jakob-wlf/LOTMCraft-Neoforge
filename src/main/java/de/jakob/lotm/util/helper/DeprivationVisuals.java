package de.jakob.lotm.util.helper;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import org.joml.Vector3f;

/** A brass seal closes around the target's power; no equipment or glow state is changed. */
public final class DeprivationVisuals {
    private static final DustParticleOptions BRASS = new DustParticleOptions(new Vector3f(0.95f, 0.7f, 0.25f), 1.2f);
    private static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(1f, 0.92f, 0.65f), 1f);

    private DeprivationVisuals() {}

    public static void activate(ServerLevel level, LivingEntity caster, LivingEntity target) {
        level.playSound(null, caster.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1f, 0.65f);
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 0.8f);
        ParticleUtil.drawParticleLine(level, PALE, caster.getEyePosition(), target.getEyePosition(), 0.25, 1);
        int[] elapsed = {0};
        ServerScheduler.scheduleForDuration(0, 2, 24, () -> {
            if (!target.isAlive() || target.level() != level) return;
            int age = elapsed[0];
            elapsed[0] += 2;
            Vec3 center = target.position().add(0, target.getBbHeight() * 0.55, 0);
            double radius = Math.max(0.4, 1.7 - age * 0.055);
            ParticleUtil.spawnCircleParticles(level, BRASS, center, radius, 24);
            for (int axis = 0; axis < 4; axis++) {
                double angle = axis * Math.PI / 2 + age * 0.08;
                for (int height = -2; height <= 2; height++) {
                    level.sendParticles(PALE, center.x + Math.cos(angle) * radius,
                            center.y + height * 0.3, center.z + Math.sin(angle) * radius,
                            1, 0, 0, 0, 0);
                }
            }
            if (age == 18) {
                level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 0.9f, 0.7f);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                        12, 0.3, 0.5, 0.3, 0.03);
            }
        }, level);
    }
}
