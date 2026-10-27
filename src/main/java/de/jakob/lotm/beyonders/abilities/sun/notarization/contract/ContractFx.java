package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ContractFx {
    private ContractFx() {}
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.18f), 1.2f);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9f, 0.1f, 0.05f), 1.3f);

    private static void sound(ServerLevel l, Vec3 p, SoundEvent s, float vol, float pitch) {
        l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void ring(ServerLevel l, Vec3 c, double r, int n, ParticleOptions o, double yVel) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            l.sendParticles(o, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 0, 0, yVel, 0, 1.0);
        }
    }

    public static void pillar(ServerLevel l, Vec3 c, double height) {
        for (double h = 0; h <= height; h += 0.2)
            l.sendParticles(ParticleTypes.END_ROD, c.x, c.y + h, c.z, 1, 0.12, 0, 0.12, 0.01);
    }

    public static void sunBurst(ServerLevel l, Vec3 pos) {
        for (int k = 0; k < 4; k++) {
            Vec3 c = pos.add(0, 0.1 + k * 0.6, 0);
            ring(l, c, 0.8 + k * 0.4, 22, GOLD, 0);
            ring(l, c, 0.6 + k * 0.35, 12, ParticleTypes.FLAME, 0.03);
        }
        pillar(l, pos, 3.0);
    }

    public static void proposed(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        ring(l, p.position().add(0, 0.1, 0), 1.0, 28, GOLD, 0);
        ring(l, p.position().add(0, 0.1, 0), 0.7, 14, ParticleTypes.END_ROD, 0.04);
        sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
    }

    public static void sealed(ServerPlayer a, ServerPlayer b) {
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            if (p == null) continue;
            ServerLevel l = p.serverLevel();
            sunBurst(l, p.position());
            sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.9f, 1.5f);
            sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
        }
        if (a != null && b != null && a.level() == b.level()) link(a.serverLevel(), a.getEyePosition(), b.getEyePosition());
    }

    public static void link(ServerLevel l, Vec3 from, Vec3 to) {
        int n = Math.max(2, (int) (from.distanceTo(to) * 3));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.lerp(to, i / (double) n);
            l.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : GOLD, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    public static void aura(ServerPlayer p) {
        p.serverLevel().sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 0.2, p.getZ(), 4, 0.4, 0.1, 0.4, 0.01);
    }

    public static void fulfilled(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        ring(l, p.position().add(0, 0.1, 0), 1.0, 24, GOLD, 0.08);
        l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 25, 0.4, 0.8, 0.4, 0.05);
        sound(l, p.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.2f);
        sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
    }

    public static void cancelled(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        sound(l, p.position(), SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
    }

    public static void judgment(ServerPlayer p, int level) {
        ServerLevel l = p.serverLevel();
        Vec3 pos = p.position();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
        if (bolt != null) { bolt.moveTo(pos.x, pos.y, pos.z); bolt.setVisualOnly(true); l.addFreshEntity(bolt); }
        int n = 40 + level * 8;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n, a = t * Math.PI * 6, r = 1.5 * (1 - t * 0.5);
            double x = pos.x + Math.cos(a) * r, y = pos.y + t * 2.8, z = pos.z + Math.sin(a) * r;
            l.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.02, 0.02, 0.02, 0.01);
            if (i % 3 == 0) l.sendParticles(RED, x, y, z, 1, 0, 0, 0, 0);
        }
        ring(l, pos.add(0, 0.1, 0), 1.6, 30, RED, 0);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 2, pos.z, 10 + level * 2, 0.4, 0.3, 0.4, 0.02);
        sound(l, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
        sound(l, pos, SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
    }

    public static void blessing(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        sunBurst(l, p.position());
        ring(l, p.position().add(0, 2.3, 0), 0.5, 24, ParticleTypes.END_ROD, 0);
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        sound(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.2f);
        sound(l, p.position(), SoundEvents.PLAYER_LEVELUP, 0.9f, 1.0f);
    }

    public static void authentic(ServerLevel l, Vec3 pos) {
        ring(l, pos, 0.5, 16, GOLD, 0.05);
        l.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 20, 0.3, 0.3, 0.3, 0.05);
        sound(l, pos, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        sound(l, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
    }

    public static void fake(ServerLevel l, Vec3 pos) {
        ring(l, pos, 0.5, 16, RED, 0.03);
        l.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.x, pos.y + 0.3, pos.z, 4, 0.3, 0.2, 0.3, 0);
        l.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 12, 0.3, 0.3, 0.3, 0.02);
        sound(l, pos, SoundEvents.VILLAGER_NO, 1.0f, 0.8f);
    }

    public static void mundane(ServerLevel l, Vec3 pos) {
        l.sendParticles(ParticleTypes.WAX_OFF, pos.x, pos.y, pos.z, 10, 0.3, 0.3, 0.3, 0.0);
        sound(l, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.6f);
    }
}
