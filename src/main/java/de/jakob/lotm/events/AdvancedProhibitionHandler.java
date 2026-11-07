package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.demoness.InvisibilityAbility;
import de.jakob.lotm.beyonders.abilities.demoness.ShadowConcealmentAbility;
import de.jakob.lotm.beyonders.abilities.error.DeceitAbility;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.ControllingUtils;
import de.jakob.lotm.beyonders.abilities.justiciar.ProhibitionAbility;
import de.jakob.lotm.beyonders.abilities.justiciar.ProhibitionAbility.ProhibitionZone;
import de.jakob.lotm.beyonders.abilities.justiciar.ProhibitionAbility.ProhibitionType;
import de.jakob.lotm.beyonders.abilities.twilight_giant.LightConcealmentAbility;
import de.jakob.lotm.beyonders.abilities.visionary.PsychologicalInvisibilityAbility;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class AdvancedProhibitionHandler {
    private static final Map<LivingEntity, Vec3> FROZEN = new WeakHashMap<>();
    private static final Map<Mob, Boolean> PREVIOUS_AI = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> LAST_NOTICE = new WeakHashMap<>();
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.78f, 0.3f), 1.2f);
    private static final Set<String> CONCEALMENT_POWERS = Set.of("invisibility_ability", "shadow_concealment_ability",
            "psychological_invisibility_ability", "lesser_concealment_ability", "light_concealment_ability");

    private AdvancedProhibitionHandler() {}

    private static boolean contains(ProhibitionZone zone, LivingEntity position, ProhibitionType type) {
        return zone.type == type && zone.isActive() && !DeceitAbility.isReversed(zone)
                && de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields.canApplyFrom(zone.ownerId, position)
                && position.level() instanceof ServerLevel level && zone.isInZone(position.position(), level);
    }

    public static boolean isMarionetteProhibited(LivingEntity marionette) {
        return isMarionetteProhibitedAt(marionette, marionette);
    }

    private static boolean isMarionetteProhibitedAt(LivingEntity marionette, LivingEntity position) {
        if (!(position.level() instanceof ServerLevel level)) return false;
        var data = marionette.getData(ModAttachments.MARIONETTE_COMPONENT);
        if (!data.isMarionette()) return false;
        LivingEntity controller = null;
        try {
            UUID controllerId = UUID.fromString(data.getControllerUUID());
            controller = level.getServer().getPlayerList().getPlayer(controllerId);
            if (controller == null) {
                for (ServerLevel world : level.getServer().getAllLevels()) {
                    if (world.getEntity(controllerId) instanceof LivingEntity living) { controller = living; break; }
                }
            }
        } catch (IllegalArgumentException ignored) { }
        int controllerSequence = BeyonderData.getSequence(controller == null ? marionette : controller);
        for (ProhibitionZone zone : ProhibitionAbility.ACTIVE_ZONES) {
            // Unlike general angel immunity, Chaos Hunters can suspend a Miracle Invoker's marionettes.
            if (contains(zone, position, ProhibitionType.MARIONETTES)
                    && zone.casterSequence - 1 <= controllerSequence) return true;
        }
        return false;
    }

    private static boolean controlledMarionetteProhibited(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        var control = player.getData(ModAttachments.ENTITY_CONTROLLING_COMPONENT);
        return control.isControlling() && control.getControlledEntity() != null
                && isMarionetteProhibitedAt(control.getControlledEntity(), player);
    }

    private static boolean concealmentProhibited(LivingEntity entity) {
        int sequence = BeyonderData.getSequence(entity);
        var concealment = entity.getEffect(ModEffects.CONCEALMENT);
        if (concealment != null) sequence = Math.min(sequence, Math.max(0, 10 - concealment.getAmplifier()));
        for (ProhibitionZone zone : ProhibitionAbility.ACTIVE_ZONES) {
            if (!contains(zone, entity, ProhibitionType.CONCEALMENT)) continue;
            if (sequence <= 2 && zone.casterSequence >= 3) continue;
            if (sequence == 0 && zone.casterSequence >= 1) continue;
            if (zone.casterSequence - 1 > sequence) continue;
            return true;
        }
        return false;
    }

    public static void applyToZone(ProhibitionZone zone) {
        if (zone.type != ProhibitionType.MARIONETTES && zone.type != ProhibitionType.CONCEALMENT) return;
        for (LivingEntity entity : zone.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(zone.center, zone.center).inflate(zone.radius))) {
            enforce(entity);
        }
    }

    private static void enforce(LivingEntity entity) {
        if (!entity.isAlive() || !(entity.level() instanceof ServerLevel level)) return;
        if (controlledMarionetteProhibited(entity) && entity instanceof ServerPlayer player) {
            LivingEntity body = player.getData(ModAttachments.ENTITY_CONTROLLING_COMPONENT).getControlledEntity();
            ToggleAbility.cleanUp(level, player);
            ControllingUtils.cancel(player, 0, true, false);
            ControllingUtils.syncControllingData(player);
            Component message = Component.translatable("ability.lotmcraft.prohibition.control_returned")
                    .withStyle(ChatFormatting.RED);
            player.sendSystemMessage(message);
            AbilityUtil.sendActionBar(player, message);
            // The restored body is frozen immediately, before its next entity tick.
            if (body != null && body.isAlive() && isMarionetteProhibited(body)) freeze(level, body);
            if (entity.level() != level) return;
        }
        if (isMarionetteProhibited(entity)) {
            freeze(level, entity);
        } else {
            FROZEN.remove(entity);
        }
        if (concealmentProhibited(entity)) reveal(level, entity);
    }

    private static void freeze(ServerLevel level, LivingEntity entity) {
        boolean first = !FROZEN.containsKey(entity);
        Vec3 anchor = FROZEN.computeIfAbsent(entity, ignored -> entity.position());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        if (entity instanceof Mob mob) { mob.getNavigation().stop(); mob.setTarget(null); }
        if (entity instanceof ServerPlayer player) player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
        if (first) { ToggleAbility.cleanUp(level, entity); sealEffect(level, entity); }
    }

    private static void reveal(ServerLevel level, LivingEntity entity) {
        boolean hidden = entity.isInvisible() || entity.hasEffect(ModEffects.CONCEALMENT)
                || InvisibilityAbility.invisiblePlayers.contains(entity.getUUID())
                || ShadowConcealmentAbility.invisiblePlayers.contains(entity.getUUID())
                || PsychologicalInvisibilityAbility.invisiblePlayers.containsKey(entity.getUUID())
                || LightConcealmentAbility.isHidden(entity);
        if (!hidden) return;
        if (entity instanceof ServerPlayer player && LightConcealmentAbility.isHidden(player)) LightConcealmentAbility.leave(player);
        var psychological = LOTMCraft.abilityHandler.getById("psychological_invisibility_ability");
        if (psychological instanceof ToggleAbility toggle) toggle.stopForEntity(entity);
        if (PsychologicalInvisibilityAbility.finalInvisiblePlayers.containsKey(entity.getUUID())
                || PsychologicalInvisibilityAbility.invisiblePlayers.containsKey(entity.getUUID())) {
            PsychologicalInvisibilityAbility.revealByProhibition(entity);
        }
        InvisibilityAbility.invisiblePlayers.remove(entity.getUUID());
        ShadowConcealmentAbility.invisiblePlayers.remove(entity.getUUID());
        entity.removeEffect(MobEffects.INVISIBILITY);
        entity.removeEffect(ModEffects.CONCEALMENT);
        entity.setInvisible(false);
        sealEffect(level, entity);
        notice(entity, "ability.lotmcraft.prohibition.concealment_revealed");
    }

    private static void sealEffect(ServerLevel level, LivingEntity entity) {
        ParticleUtil.spawnCircleParticles(level, GOLD, entity.position().add(0, 1, 0), 0.9, 20);
        level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    public static void notice(LivingEntity entity, String key) {
        if (!(entity instanceof ServerPlayer player)) return;
        long now = player.serverLevel().getGameTime();
        Long previous = LAST_NOTICE.get(entity);
        if (previous != null && now - previous < 40) return;
        LAST_NOTICE.put(entity, now);
        AbilityUtil.sendActionBar(entity, Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide) return;
        enforce(entity);
        // Suppress AI only during the mob's tick, preserving effect timers, damage and normal lifetime.
        if (entity instanceof Mob mob && entity.isAlive() && isMarionetteProhibited(entity)) {
            PREVIOUS_AI.putIfAbsent(mob, mob.isNoAi());
            mob.setNoAi(true);
        }
    }

    @SubscribeEvent
    public static void onTickFinished(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        Boolean previous = PREVIOUS_AI.remove(mob);
        if (previous != null) mob.setNoAi(previous);
        if (isMarionetteProhibited(mob)) {
            Vec3 anchor = FROZEN.get(mob);
            if (anchor != null) mob.setPos(anchor);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        LivingEntity entity = event.getEntity();
        if (isMarionetteProhibited(entity) || controlledMarionetteProhibited(entity)) {
            event.setCanceled(true);
            notice(entity, "ability.lotmcraft.prohibition.marionette_suspended");
        } else if (event.getAbility() != null && CONCEALMENT_POWERS.contains(event.getAbility().getId())
                && concealmentProhibited(entity)) {
            event.setCanceled(true);
            notice(entity, "ability.lotmcraft.prohibition.concealment_revealed");
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && (isMarionetteProhibited(attacker) || controlledMarionetteProhibited(attacker))) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if ((event.getEffectInstance().is(MobEffects.INVISIBILITY) || event.getEffectInstance().is(ModEffects.CONCEALMENT))
                && concealmentProhibited(event.getEntity())) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
}
