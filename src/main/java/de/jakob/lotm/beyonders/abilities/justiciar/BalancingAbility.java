package de.jakob.lotm.beyonders.abilities.justiciar;

import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.helper.BalancingVisuals;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BalancingAbility extends SelectableAbility {

    public static final List<BalancingZone> ACTIVE_ZONES = new CopyOnWriteArrayList<>();

    private static final double ZONE_RADIUS = de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields.RADIUS;
    private static final int INITIAL_TARGET_RANGE = 64;
    private static final double INITIAL_ZONE_RADIUS = 48.0;
    private static final Map<LivingEntity, Integer> LAST_EXIT_NOTICE_TICK = new WeakHashMap<>();

    public BalancingAbility(String id) {
        super(id, 30f);
        interactionRadius = 1;
        hasOptimalDistance = false;
        postsUsedAbilityEventManually = true;
        canBeShared = false;


    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("justiciar", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 1600;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{"ability.lotmcraft.balancing.confrontation", "ability.lotmcraft.balancing.field_order",
                "ability.lotmcraft.balancing.power_balance"};
    }

    @Override
    public String[] getSequenceDependantAbilityNames(int sequence) {
        String[] names = getAllAbilityNames();
        return sequence <= 2 ? names : new String[]{names[0], names[1]};
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (level.isClientSide) return;
        if (abilityIndex < 0 || abilityIndex >= getSequenceDependantAbilityNames(BeyonderData.getSequence(entity)).length) return;
        ServerLevel serverLevel = (ServerLevel) level;
        if (abilityIndex == 1) {
            createInitialBattlefield(serverLevel, entity, true);
            return;
        }
        if (abilityIndex == 0) {
            createInitialBattlefield(serverLevel, entity, false);
            return;
        }

        de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields.create(serverLevel, entity);
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel, entity.position(), entity, this,
                interactionFlags, ZONE_RADIUS, 20 * 2));
    }

    /** Preliminary Balance orders a fight or a fixed area without angel-level isolation. */
    private void createInitialBattlefield(ServerLevel level, LivingEntity caster, boolean areaOnly) {
        LivingEntity target = areaOnly ? caster : AbilityUtil.getTargetEntity(caster, INITIAL_TARGET_RANGE, 1.5f, true);
        if (!areaOnly && (target == null || !target.isAlive() || target.level() != level
                || caster.distanceTo(target) > INITIAL_TARGET_RANGE || AllyUtil.areAllies(caster, target))) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.balancing.no_target"));
            return;
        }
        ACTIVE_ZONES.removeIf(z -> z.preliminary && z.ownerId.equals(caster.getUUID()));
        Vec3 center = areaOnly ? caster.position() : caster.position().add(target.position()).scale(0.5);
        double radius = INITIAL_ZONE_RADIUS;
        int duration = 20 * 20;
        // Freeze admission when the battlefield is created: changing alliances cannot bypass it.
        Set<UUID> admitted = new HashSet<>();
        admitted.add(caster.getUUID());
        admitted.add(target.getUUID());
        AllyUtil.getAllAllies(caster).forEach(ally -> admitted.add(ally.uuid()));
        if (!areaOnly) AllyUtil.getAllAllies(target).forEach(ally -> admitted.add(ally.uuid()));
        BalancingZone zone = new BalancingZone(caster.getUUID(), center, level,
                level.getGameTime() + duration, radius, true, admitted);
        ACTIVE_ZONES.add(zone);

        BalancingVisuals.activate(zone, caster, target);
        ServerScheduler.scheduleForDuration(0, 2, duration, () -> {
            if (!ACTIVE_ZONES.contains(zone)) return;
            if (!caster.isAlive() || !target.isAlive() || caster.level() != level || target.level() != level) {
                if (ACTIVE_ZONES.remove(zone)) BalancingVisuals.end(zone);
                return;
            }
            if (!zone.isActive()) return;
            if (level.getGameTime() % 10 < 2) {
                BalancingVisuals.boundary(zone);
            }
            if (!areaOnly) {
                resistBoundary(caster, zone);
                resistBoundary(target, zone);
            }
        }, () -> {
            if (ACTIVE_ZONES.remove(zone)) BalancingVisuals.end(zone);
        }, level);

        Component declared = Component.translatable(areaOnly
                ? "ability.lotmcraft.balancing.field_declared"
                : "ability.lotmcraft.balancing.initial_declared").withStyle(ChatFormatting.GOLD);
        caster.sendSystemMessage(declared);
        if (areaOnly) {
            AbilityUtil.sendActionBar(caster, declared);
        } else {
            Component targetNotice = Component.translatable("ability.lotmcraft.balancing.target_bound")
                    .withStyle(ChatFormatting.GOLD);
            target.sendSystemMessage(targetNotice);
            AbilityUtil.sendActionBar(target, targetNotice);
        }
        // Both selectable modes retain balancing_ability as their ID for the same acting task.
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(level, center, caster, this,
                interactionFlags, radius, 20 * 2));
    }

    private void resistBoundary(LivingEntity participant, BalancingZone zone) {
        // An initial authority cannot hold angels; teleportation and flight can still escape.
        if (BeyonderData.isBeyonder(participant) && BeyonderData.getSequence(participant) <= 2) return;
        Vec3 offset = participant.position().subtract(zone.center);
        double distance = offset.horizontalDistance();
        if (Math.abs(offset.y) > zone.radius || distance < zone.radius - 2 || distance > zone.radius + 2) return;
        Vec3 inward = new Vec3(-offset.x, 0, -offset.z).normalize();
        Vec3 motion = participant.getDeltaMovement();
        double outwardSpeed = -motion.dot(inward);
        // Only announce an attempted outward crossing, not standing at or returning from the rim.
        if (outwardSpeed > 0.01 && participant instanceof ServerPlayer) {
            Integer lastTick = LAST_EXIT_NOTICE_TICK.get(participant);
            if (lastTick == null || participant.tickCount - lastTick >= 40) {
                LAST_EXIT_NOTICE_TICK.put(participant, participant.tickCount);
                AbilityUtil.sendActionBar(participant,
                        Component.translatable("ability.lotmcraft.balancing.exit_prohibited")
                                .withStyle(ChatFormatting.RED));
            }
        }
        participant.setDeltaMovement(motion.add(inward.scale(0.12 + Math.max(0, outwardSpeed) * 0.5)));
        participant.hurtMarked = true;
        participant.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 0, false, false));
    }

    public static class BalancingZone {
        public final UUID ownerId;
        public final Vec3 center;
        public final ServerLevel level;
        public final long expiryTick;
        public final double radius;
        public final boolean preliminary;
        public final Set<UUID> admitted;

        public BalancingZone(UUID ownerId, Vec3 center, ServerLevel level, long expiryTick) {
            this(ownerId, center, level, expiryTick, ZONE_RADIUS, false, Set.of());
        }

        private BalancingZone(UUID ownerId, Vec3 center, ServerLevel level, long expiryTick,
                              double radius, boolean preliminary, Set<UUID> admitted) {
            this.admitted = Set.copyOf(admitted);
            this.radius = radius;
            this.preliminary = preliminary;
            this.ownerId = ownerId;
            this.center = center;
            this.level = level;
            this.expiryTick = expiryTick;
        }

        public boolean isActive() {
            return level.getGameTime() < expiryTick;
        }

        public boolean isInZone(Vec3 pos, ServerLevel lvl) {
            return lvl.equals(level) && pos.distanceTo(center) <= radius;
        }

        public boolean restrictsEntry(LivingEntity entity) {
            if (!preliminary || !isActive() || entity.level() != level || admitted.contains(entity.getUUID()))
                return false;
            if (BeyonderData.isBeyonder(entity) && BeyonderData.getSequence(entity) <= 2) return false;
            return ProhibitionAbility.ProhibitionZone.appliesTo(entity, ProhibitionAbility.ProhibitionType.PLAYERS);
        }
    }
}
