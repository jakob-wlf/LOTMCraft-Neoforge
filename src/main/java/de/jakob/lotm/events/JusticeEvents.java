package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeData;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeEnforcement;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeManager;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeProvocation;
import de.jakob.lotm.events.custom.ArtifactUsedEvent;
import de.jakob.lotm.events.custom.ChangeBeyonderDataEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class JusticeEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!event.getSource().is(de.jakob.lotm.damage.ModDamageTypes.PURIFICATION)) return;
        float multiplier = JusticeManager.purificationMultiplier(attacker, event.getEntity());
        if (multiplier != 1f) event.setAmount(event.getAmount() * multiplier);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        LivingEntity victim = event.getEntity();
        if (attacker == victim || event.getNewDamage() <= 0f) return;
        if (JusticeProvocation.registerHit(attacker, victim)) return;
        if (attacker instanceof ServerPlayer player) JusticeManager.onHarm(player, victim);
        JusticeEnforcement.onHarm(attacker, victim);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        LivingEntity victim = event.getEntity();
        if (attacker == victim) return;
        if (attacker instanceof ServerPlayer player) JusticeManager.onKill(player, victim);
        JusticeEnforcement.onKill(attacker, victim);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) JusticeManager.tick(player);
    }

    @SubscribeEvent
    public static void onArtifactUsed(ArtifactUsedEvent event) {
        LivingEntity user = event.getSourceEntity();
        if (user == null || user.level().isClientSide()) return;
        if (user instanceof ServerPlayer player) JusticeManager.onArtifactUsed(player);
        JusticeEnforcement.onArtifactUsed(user);
    }

    @SubscribeEvent
    public static void onBeyonderDataChange(ChangeBeyonderDataEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        JusticeManager.onBeyonderDataChange(player, event.getOldPathway(), event.getNewPathway(),
                event.getOldSequence(), event.getNewSequence());
    }

    @SubscribeEvent
    public static void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) JusticeManager.onFoodEaten(player, event.getItem());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        var original = event.getOriginal().getPersistentData();
        if (original.contains(JusticeData.ROOT)) {
            event.getEntity().getPersistentData().put(JusticeData.ROOT, original.getCompound(JusticeData.ROOT).copy());
        }
    }
}
