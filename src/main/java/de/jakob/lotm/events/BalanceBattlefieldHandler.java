package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.justiciar.IndividualBalanceAbility;
import de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields;
import de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceContext;
import de.jakob.lotm.events.custom.TargetEntityEvent;
import de.jakob.lotm.events.custom.TargetLocationEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class BalanceBattlefieldHandler {
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity entity) {
            // Previous area equalization stored untimed modifiers; clear those obsolete locks on load.
            entity.getData(de.jakob.lotm.attachments.ModAttachments.MULTIPLIER_MODIFIER_COMPONENT)
                    .modifiers.keySet().removeIf(key -> key.startsWith("balancing_zone_"));
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity source = event.getSource().getEntity() instanceof LivingEntity living ? living : BalanceContext.source();
        if (source != null && !BalanceBattlefields.canInteract(source, event.getEntity())) {
            event.setCanceled(true); BalanceBattlefields.notice(source);
        }
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        LivingEntity source = BalanceContext.source();
        if (source != null && !BalanceBattlefields.canInteract(source, event.getEntity()))
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity source = BalanceContext.source();
        if (source != null && !BalanceBattlefields.canInteract(source, event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAbility(AbilityUseEvent event) {
        if (BalanceBattlefields.blocked(event.getEntity())) {
            event.setCanceled(true); BalanceBattlefields.notice(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onTarget(TargetEntityEvent event) {
        if (event.getTargetEntity() != null && !BalanceBattlefields.canInteract(event.getSourceEntity(), event.getTargetEntity()))
            event.setTargetEntity(null);
    }

    @SubscribeEvent
    public static void onLocation(TargetLocationEvent event) {
        LivingEntity entity = event.getSourceEntity();
        if (event.getTargetLocation() != null && entity.level() instanceof ServerLevel level
                && !BalanceBattlefields.canTravel(entity, level, event.getTargetLocation())) {
            event.setTargetLocation(entity.position()); BalanceBattlefields.notice(entity);
        }
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !(entity.level() instanceof ServerLevel level)) return;
        if (!BalanceBattlefields.canTravel(entity, level, new Vec3(event.getTargetX(),event.getTargetY(),event.getTargetZ()))) {
            event.setCanceled(true); BalanceBattlefields.notice(entity);
        }
    }

    @SubscribeEvent
    public static void onDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !(entity.level() instanceof ServerLevel level)) return;
        ServerLevel destination = level.getServer().getLevel(event.getDimension());
        if (destination != null && !BalanceBattlefields.canTravel(entity, destination, entity.position())) {
            event.setCanceled(true); BalanceBattlefields.notice(entity);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && !entity.level().isClientSide) BalanceBattlefields.enforceMovement(entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        BalanceBattlefields.tick(); IndividualBalanceAbility.expireBalances();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        BalanceBattlefields.clear(); IndividualBalanceAbility.clearBalances();
    }
}
