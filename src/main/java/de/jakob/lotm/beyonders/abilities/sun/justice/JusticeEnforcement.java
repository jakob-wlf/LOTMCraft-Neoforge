package de.jakob.lotm.beyonders.abilities.sun.justice;

import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class JusticeEnforcement {
    public static final long ENHANCE_AFTERGLOW_TICKS = 20L * 30;
    private static final long PENALTY_COOLDOWN_TICKS = 100L;
    private static final String PENALTY_MOD = "justice_verdict";

    private record Halo(UUID owner, ResourceKey<Level> dimension, Vec3 center, double radius, long end) {}
    private record Edict(UUID owner, JusticePrinciple principle, long end) {}
    private record Charge(UUID owner, JusticePrinciple principle, boolean judgement) {}

    private static final Map<UUID, Halo> halos = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> enhancedUntil = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Edict>> edicts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> strikes = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastPenalty = new ConcurrentHashMap<>();

    private JusticeEnforcement() {}

    public static void startHalo(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, long durationTicks) {
        long end = level.getGameTime() + durationTicks;
        halos.put(owner.getUUID(), new Halo(owner.getUUID(), level.dimension(), center, radius, end));
        enhancedUntil.put(owner.getUUID(), end + ENHANCE_AFTERGLOW_TICKS);
    }

    public static void endHalo(UUID owner) {
        halos.remove(owner);
        strikes.keySet().removeIf(key -> key.startsWith(owner + ":h:"));
    }

    public static boolean isEnhanced(Player player) {
        Long until = enhancedUntil.get(player.getUUID());
        if (until == null) return false;
        if (player.level().getGameTime() > until) {
            enhancedUntil.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void addEdict(ServerPlayer owner, LivingEntity target, JusticePrinciple principle, long durationTicks) {
        long end = owner.level().getGameTime() + durationTicks;
        List<Edict> list = edicts.computeIfAbsent(target.getUUID(), k -> new CopyOnWriteArrayList<>());
        list.removeIf(e -> e.owner().equals(owner.getUUID()) && e.principle() == principle);
        list.add(new Edict(owner.getUUID(), principle, end));
        strikes.remove(owner.getUUID() + ":e:" + target.getUUID() + ":" + principle.id());
    }

    private static List<Charge> charges(MinecraftServer server, LivingEntity subject) {
        List<Charge> result = new ArrayList<>();
        long now = subject.level().getGameTime();

        for (Halo halo : halos.values()) {
            if (halo.owner().equals(subject.getUUID()) || now > halo.end()) continue;
            if (subject.level().dimension() != halo.dimension()) continue;
            double dx = subject.getX() - halo.center().x;
            double dz = subject.getZ() - halo.center().z;
            if (dx * dx + dz * dz > halo.radius() * halo.radius()) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(halo.owner());
            if (owner == null) continue;
            for (JusticePrinciple principle : JusticeData.chosen(owner)) {
                result.add(new Charge(halo.owner(), principle, false));
            }
        }

        List<Edict> list = edicts.get(subject.getUUID());
        if (list != null) {
            list.removeIf(e -> now > e.end());
            for (Edict edict : list) result.add(new Charge(edict.owner(), edict.principle(), true));
        }
        return result;
    }

    public static void onHarm(LivingEntity attacker, LivingEntity victim) {
        MinecraftServer server = attacker.getServer();
        if (server == null || attacker == victim) return;
        for (Charge charge : charges(server, attacker)) {
            if (charge.principle().brokenByHarm(attacker, victim)) penalize(server, charge, attacker);
        }
    }

    public static void onKill(LivingEntity attacker, LivingEntity victim) {
        MinecraftServer server = attacker.getServer();
        if (server == null || attacker == victim) return;
        for (Charge charge : charges(server, attacker)) {
            if (charge.principle().brokenByKill(attacker, victim)) penalize(server, charge, attacker);
        }
    }

    public static void tickSubject(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        if (halos.isEmpty() && edicts.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (Charge charge : charges(server, player)) {
            if (charge.principle().brokenByState(player)) penalize(server, charge, player);
        }
    }

    public static void onArtifactUsed(LivingEntity user) {
        MinecraftServer server = user.getServer();
        if (server == null) return;
        for (Charge charge : charges(server, user)) {
            if (charge.principle().brokenByArtifact()) penalize(server, charge, user);
        }
    }

    private static void penalize(MinecraftServer server, Charge charge, LivingEntity subject) {
        ServerPlayer owner = server.getPlayerList().getPlayer(charge.owner());
        if (owner == null || !JusticeManager.isMentor(owner)) return;

        long now = subject.level().getGameTime();
        Long last = lastPenalty.get(subject.getUUID());
        if (last != null && now - last < PENALTY_COOLDOWN_TICKS) return;
        lastPenalty.put(subject.getUUID(), now);

        String key = charge.owner() + (charge.judgement() ? ":e:" : ":h:") + subject.getUUID() + ":" + charge.principle().id();
        int tier = strikes.merge(key, 1, Integer::sum) + (charge.judgement() ? 1 : 0);

        ServerLevel level = (ServerLevel) subject.level();
        ParticleUtil.spawnParticles(level, ParticleTypes.END_ROD, subject.getEyePosition(), 30, .4, .6, .4, .08);
        level.playSound(null, subject.blockPosition(), SoundEvents.BEACON_DEACTIVATE, subject.getSoundSource(), 1f, .8f);

        String verdict;
        if (tier <= 1) {
            verdict = "weakened";
            BeyonderData.addModifierWithTimeLimit(subject, PENALTY_MOD, 0.6, 30_000L);
            subject.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
            subject.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 0));
        } else if (tier == 2) {
            verdict = "nullified";
            BeyonderData.addModifierWithTimeLimit(subject, PENALTY_MOD, 0.2, 60_000L);
            if (subject instanceof Player) BeyonderData.reduceSpirituality(subject, BeyonderData.getSpirituality(subject));
            subject.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 1));
        } else {
            verdict = "sealed";
            BeyonderData.addModifierWithTimeLimit(subject, PENALTY_MOD, 0.2, 120_000L);
            AbilityUtil.sealAllTargetAbilities(owner, subject, "justice_verdict", 20 * 120);
        }

        Component message = Component.translatable("justice.lotmcraft.verdict." + verdict, charge.principle().shortName()).withStyle(ChatFormatting.GOLD);
        if (subject instanceof ServerPlayer sp) {
            sp.sendSystemMessage(message);
            AbilityUtil.sendActionBar(sp, message);
        }
        AbilityUtil.sendActionBar(owner, Component.translatable("justice.lotmcraft.verdict.owner", subject.getDisplayName(), charge.principle().shortName()).withStyle(ChatFormatting.YELLOW));
    }
}
