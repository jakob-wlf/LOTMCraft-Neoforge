package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.justiciar.BalancingAbility;
import de.jakob.lotm.beyonders.abilities.justiciar.IndividualBalanceAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class BalancingHandler {
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (var zone : List.copyOf(BalancingAbility.ACTIVE_ZONES)) {
            if (!zone.isActive()) zone.close();
            else if (zone.level.getGameTime() % 5 == 0) zone.apply();
        }
        IndividualBalanceAbility.expireBalances();
    }

    @SubscribeEvent
    public static void effect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (BalancingAbility.ACTIVE_ZONES.stream().anyMatch(zone -> zone.isActive()
                && zone.isInZone(event.getEntity().position(), level))) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static void removeObsoleteModifiers(LivingEntity entity) {
        entity.getData(ModAttachments.MULTIPLIER_MODIFIER_COMPONENT).modifiers.keySet()
                .removeIf(key -> key.startsWith("balancing_zone_"));
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity entity)
            removeObsoleteModifiers(entity);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity entity)
            removeObsoleteModifiers(entity);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        for (var zone : List.copyOf(BalancingAbility.ACTIVE_ZONES)) zone.close();
        IndividualBalanceAbility.clearBalances();
    }
}
