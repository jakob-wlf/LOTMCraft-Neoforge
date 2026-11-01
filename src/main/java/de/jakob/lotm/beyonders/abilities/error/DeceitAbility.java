package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.justiciar.ProhibitionAbility;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.rendering.effectRendering.EffectIds;
import de.jakob.lotm.rendering.effectRendering.EffectManager;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.helper.RingEffectManager;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DeceitAbility extends SelectableAbility {

    public static final HashSet<UUID> cannotBeTargeted = new HashSet<>();
    public static final HashSet<UUID> cannotBeHarmed = new HashSet<>();
    public static final HashMap<UUID, Integer> seqMap = new HashMap<>(50);

    private static final Set<ProhibitionAbility.ProhibitionZone> reversedZones = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Map<UUID, Map.Entry<UUID, Long>> grabbedAbilities = new ConcurrentHashMap<>();

    public DeceitAbility(String id) {
        super(id, 15);
        autoClear = false;
        canBeUsedInArtifact = false;
        canBeShared = false;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(5, 7, 9));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(9000f, 4400f, 3400f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    protected float getSpiritualityCost() {
        return 3400;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.deceit.laws",
                "ability.lotmcraft.deceit.ability",
                "ability.lotmcraft.deceit.entities",
                "ability.lotmcraft.deceit.world"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if(!(level instanceof ServerLevel serverLevel)) {
            entity.playSound(SoundEvents.BELL_RESONATE, 2, 1);
            return;
        }

        EffectManager.playEffect(EffectIds.DECEPTION, entity.getX(), entity.getY() + .5, entity.getZ(), serverLevel);

        seqMap.put(entity.getUUID(), AbilityUtil.getSeqWithArt(entity, this));

        switch (abilityIndex) {
            case 0 -> deceiveLaws(serverLevel, entity);
            case 1 -> deceiveAbility(serverLevel, entity);
            case 2 -> deceiveOthers(serverLevel, entity);
            case 3 -> deceiveWorld(serverLevel, entity);
        }
    }

    public static boolean isReversed(ProhibitionAbility.ProhibitionZone zone) {
        synchronized (reversedZones) {
            return reversedZones.contains(zone);
        }
    }

    private void deceiveLaws(ServerLevel level, LivingEntity entity) {
        int seq = AbilityUtil.getSeqWithArt(entity, this);
        int deceived = 0;

        for (ProhibitionAbility.ProhibitionZone zone : ProhibitionAbility.ACTIVE_ZONES) {
            if (!zone.level.equals(level) || !zone.isActive()) continue;
            if (zone.center.distanceTo(entity.position()) > 90) continue;
            if (zone.ownerId.equals(entity.getUUID())) continue;
            if (AbilityUtil.isTargetSignificantlyStronger(seq, zone.casterSequence) || zone.casterSequence < seq - 1) continue;

            synchronized (reversedZones) {
                if (reversedZones.add(zone)) deceived++;
            }
            FxHandler.play(level, "deceit_wave", zone.center.add(0, 0.2, 0), 2.5);
        }

        clearArtifactScaling(entity);
        AbilityUtil.sendActionBar(entity, Component.translatable(deceived > 0 ? "ability.lotmcraft.deceit.laws_reversed" : "ability.lotmcraft.deceit.no_laws", deceived)
                .withColor(ErrorUtil.DECEIT_COLOR));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;

        synchronized (reversedZones) {
            reversedZones.removeIf(zone -> !zone.isActive() || !ProhibitionAbility.ACTIVE_ZONES.contains(zone));
            for (ProhibitionAbility.ProhibitionZone zone : reversedZones) {
                for (Player player : zone.level.players()) {
                    if (player.position().distanceTo(zone.center) > 40) continue;
                    applyReversedLaw(zone.type, player);
                }
                zone.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, zone.center.x, zone.center.y + 1, zone.center.z, 12, 8, 1, 8, 0);
            }
        }

        long now = event.getServer().overworld().getGameTime();
        grabbedAbilities.values().removeIf(entry -> entry.getValue() < now);
    }

    private static void applyReversedLaw(ProhibitionAbility.ProhibitionType type, Player player) {
        int duration = 40;
        switch (type) {
            case BEYONDER_ABILITIES -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 0, false, false, true));
                if (BeyonderData.isBeyonder(player)) BeyonderData.incrementSpirituality(player, BeyonderData.getMaxSpirituality(player) * 0.02f);
            }
            case COMBAT -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, false, false, true));
            }
            case FLYING -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, duration, 2, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0, false, false, true));
            }
            case ITEM_USE -> {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 2, 0, false, false, false));
            }
            case PLAYERS -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0, false, false, true));
            case OUTSIDE_WORLD -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 1, false, false, true));
            case STAND_INS, MARIONETTE_INTERCHANGE -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, false, false, true));
            case THEFT -> player.addEffect(new MobEffectInstance(MobEffects.LUCK, duration, 1, false, false, true));
        }
    }

    private static boolean isAbilityEntity(Entity entity) {
        if (entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof Player) return false;
        return entity.getClass().getName().startsWith("de.jakob.lotm.entity");
    }

    private void deceiveAbility(ServerLevel level, LivingEntity entity) {
        clearArtifactScaling(entity);
        Map.Entry<UUID, Long> grabbed = grabbedAbilities.remove(entity.getUUID());

        if (grabbed != null) {
            Entity held = level.getEntity(grabbed.getKey());
            if (held != null && held.isAlive()) {
                Vec3 destination = AbilityUtil.getTargetLocation(entity, 60, 1.5f);
                FxHandler.play(level, "deceit_swap", held.position(), 1);
                held.teleportTo(destination.x, destination.y, destination.z);
                FxHandler.play(level, "deceit_swap", destination, 1);
                level.playSound(null, held.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1, 1.2f);
                AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.deceit.ability_placed").withColor(ErrorUtil.DECEIT_COLOR));
                return;
            }
        }

        Vec3 eye = entity.getEyePosition();
        Vec3 look = entity.getLookAngle();
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity candidate : level.getEntities((Entity) null, new AABB(eye, eye.add(look.scale(60))).inflate(8), DeceitAbility::isAbilityEntity)) {
            Vec3 to = candidate.position().subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > 60) continue;
            double offAxis = to.subtract(look.scale(along)).length() - candidate.getBbWidth();
            if (offAxis > 4) continue;
            double score = offAxis + along * 0.02;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.deceit.no_ability_entity").withColor(ErrorUtil.DECEIT_COLOR));
            return;
        }

        grabbedAbilities.put(entity.getUUID(), new AbstractMap.SimpleEntry<>(best.getUUID(), level.getGameTime() + 20 * 30));
        level.sendParticles(ParticleTypes.WITCH, best.getX(), best.getY() + 0.5, best.getZ(), 30, 0.6, 0.6, 0.6, 0.05);
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.deceit.ability_grabbed", best.getName()).withColor(ErrorUtil.DECEIT_COLOR));
    }

    private void deceiveWorld(ServerLevel serverLevel, LivingEntity entity) {
        if(random.nextFloat() < 0.7) {
            AbilityUtil.sendActionBar(entity, Component.translatable("lotmcraft.deceive_world_ability.fail").withColor(ErrorUtil.DECEIT_COLOR));
            return;
        }
        cannotBeHarmed.add(entity.getUUID());

        ServerScheduler.scheduleDelayed((int) (20 * 5), () -> {
            cannotBeHarmed.remove(entity.getUUID());
            clearArtifactScaling(entity);
            seqMap.remove(entity.getUUID());
        });
    }

    private void deceiveOthers(ServerLevel serverLevel, LivingEntity entity) {
        cannotBeTargeted.add(entity.getUUID());

        AbilityUtil.getNearbyEntities(entity, serverLevel, entity.position(), 20).forEach(e -> {
            if(BeyonderData.isBeyonder(e) && BeyonderData.getSequence(e) < AbilityUtil.getSeqWithArt(entity, this)) {
                return;
            }

            BeyonderData.addModifierWithTimeLimit(e, "deceit", .8, 1000 * 10);
            RingEffectManager.createRingForAll(e.position().add(0, e.getEyeHeight() / 2, 0), 1.8f, 120, 156 / 255f, 72 / 155f, 219 / 255f, .7f, .75f, 1, serverLevel);
            RingEffectManager.createRingForAll(e.position().add(0, e.getEyeHeight() / 2, 0), 1f, 120, 106 / 255f, 237 / 255f,102 / 255f, .9f, .75f, 1, serverLevel);

            if(e instanceof Mob mob) {
                mob.setTarget(null);
            }
            if(e instanceof BeyonderNPCEntity npc) {
                npc.setTarget(null);
            }
        });

        ServerScheduler.scheduleDelayed((int) (20 * 5), () -> {
            cannotBeTargeted.remove(entity.getUUID());
            clearArtifactScaling(entity);
            seqMap.remove(entity.getUUID());
        });
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null || !cannotBeTargeted.contains(newTarget.getUUID())) {
            return;
        }

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();

        var source = event.getSource().getEntity();

        if(!cannotBeHarmed.contains(entity.getUUID()) || !(entity.level() instanceof ServerLevel serverLevel)) return;

        if(!(source instanceof LivingEntity livingSource) ||
                BeyonderData.getSequence(livingSource) < seqMap.getOrDefault(entity.getUUID(), LOTMCraft.NON_BEYONDER_SEQ)){
           return;
        }

        ParticleUtil.spawnParticles(serverLevel, ParticleTypes.END_ROD, entity.position().add(0, entity.getEyeHeight() / 2, 0), 60, 0.5, 0.5, 0.5, 0.1);
        event.setCanceled(true);
    }
}
