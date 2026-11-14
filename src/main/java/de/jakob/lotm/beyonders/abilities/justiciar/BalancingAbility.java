package de.jakob.lotm.beyonders.abilities.justiciar;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.RingEffectManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class BalancingAbility extends Ability {
    public static final List<BalancingZone> ACTIVE_ZONES = new CopyOnWriteArrayList<>();

    public BalancingAbility(String id) {
        super(id, 30f);
        interactionRadius = 1;
        hasOptimalDistance = false;
        postsUsedAbilityEventManually = true;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() { return Map.of("justiciar", 3); }

    @Override
    protected float getSpiritualityCost() { return 1600; }

    public static double radiusForSequence(int sequence) { return sequence <= 2 ? 120 : 60; }
    public static int baseDurationTicks(int sequence) { return 20 * (sequence <= 2 ? 240 : 60); }

    @Override
    public void onAbilityUse(Level level, LivingEntity caster) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Replacing an owned zone cannot leave two tasks writing the same modifier key.
        for (BalancingZone old : List.copyOf(ACTIVE_ZONES)) {
            if (old.ownerId.equals(caster.getUUID())) old.close();
        }
        int sequence = AbilityUtil.getSeqWithArt(caster, this);
        double radius = radiusForSequence(sequence);
        int duration = Math.max(1, (int) (baseDurationTicks(sequence) * multiplier(caster)));
        Vec3 center = caster.position();
        List<LivingEntity> initial = AbilityUtil.getNearbyEntities(null, serverLevel, center, (int) radius)
                .stream().filter(BeyonderData::isBeyonder).toList();
        double average = initial.stream().mapToDouble(BeyonderData::getMultiplier).average()
                .orElseGet(() -> BeyonderData.getMultiplier(caster));
        BalancingZone zone = new BalancingZone(caster.getUUID(), center, serverLevel,
                serverLevel.getGameTime() + duration, radius, average);
        ACTIVE_ZONES.add(zone);
        zone.apply();
        RingEffectManager.createRingForAll(center, (float) radius, duration,
                .95f, .95f, 1f, .6f, 2f, 5f, 100f, false, false, serverLevel);
        Component notice = Component.translatable("ability.lotmcraft.balancing.declared").withStyle(ChatFormatting.GOLD);
        serverLevel.players().stream().filter(player -> player.position().distanceTo(center) <= radius)
                .forEach(player -> player.sendSystemMessage(notice));
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel, center, caster, this,
                interactionFlags, radius, 20 * 2));
    }

    public static final class BalancingZone {
        public final UUID ownerId;
        public final Vec3 center;
        public final ServerLevel level;
        public final long expiryTick;
        public final double radius;
        private final double average;
        private final Set<LivingEntity> affected = new HashSet<>();

        BalancingZone(UUID ownerId, Vec3 center, ServerLevel level, long expiryTick, double radius, double average) {
            this.ownerId = ownerId; this.center = center; this.level = level;
            this.expiryTick = expiryTick; this.radius = radius; this.average = average;
        }

        public boolean isActive() { return level.getGameTime() < expiryTick; }
        public boolean isInZone(Vec3 position, ServerLevel world) {
            return world == level && position.distanceTo(center) <= radius;
        }

        public void apply() {
            Set<LivingEntity> current = new HashSet<>(AbilityUtil.getNearbyEntities(null, level, center, (int) radius)
                    .stream().filter(e -> e.isAlive() && BeyonderData.isBeyonder(e)).toList());
            String key = "balancing_zone_" + ownerId;
            for (LivingEntity previous : affected) {
                if (!current.contains(previous)) BeyonderData.removeModifier(previous, key);
            }
            for (LivingEntity entity : current) {
                // Measure without our own modifier so repeated updates do not compound.
                BeyonderData.removeModifier(entity, key);
                double currentPower = BeyonderData.getMultiplier(entity);
                if (currentPower > 0 && Double.isFinite(currentPower)) {
                    BeyonderData.addModifier(entity, key, average / currentPower);
                }
            }
            affected.clear(); affected.addAll(current);
        }

        public void close() {
            for (LivingEntity entity : affected) BeyonderData.removeModifier(entity, "balancing_zone_" + ownerId);
            affected.clear(); ACTIVE_ZONES.remove(this);
        }
    }
}
