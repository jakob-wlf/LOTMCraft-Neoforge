package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.justiciar.BalancingAbility;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.BalancingVisuals;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Admission rules for preliminary Balance; this does not isolate attacks from outside. */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class BalancingHandler {
    private static final double REPULSION_SPEED = 0.6;
    private static final Map<LivingEntity, Integer> LAST_NOTICE_TICK = new WeakHashMap<>();

    private static BalancingAbility.BalancingZone restrictedZone(LivingEntity entity, Vec3 position) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) return null;
        for (var zone : BalancingAbility.ACTIVE_ZONES) {
            if (zone.restrictsEntry(entity) && zone.isInZone(position, level)) return zone;
        }
        return null;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        var zone = restrictedZone(entity, entity.position());
        if (zone == null) return;
        // Checking occupancy also catches direct Door teleports which do not post a teleport event.
        Vec3 offset = entity.position().subtract(zone.center);
        Vec3 outward = new Vec3(offset.x, 0, offset.z).normalize();
        if (outward.lengthSqr() < 0.001) outward = new Vec3(1, 0, 0);
        double vertical = entity.getDeltaMovement().y;
        entity.setDeltaMovement(outward.x * REPULSION_SPEED, vertical, outward.z * REPULSION_SPEED);
        entity.hurtMarked = true;
        BalancingVisuals.repel(zone.level, entity);
        notifyRestricted(entity);
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        LivingEntity entity = event.getEntity();
        if (restrictedZone(entity, entity.position()) == null) return;
        event.setCanceled(true);
        notifyRestricted(entity);
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        Vec3 destination = new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        if (restrictedZone(entity, destination) == null) return;
        event.setCanceled(true);
        notifyRestricted(entity);
    }

    private static void notifyRestricted(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer)) return;
        Integer lastTick = LAST_NOTICE_TICK.get(entity);
        if (lastTick != null && entity.tickCount - lastTick < 40) return;
        LAST_NOTICE_TICK.put(entity, entity.tickCount);
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.balancing.entry_prohibited")
                .withStyle(ChatFormatting.RED));
    }
}
