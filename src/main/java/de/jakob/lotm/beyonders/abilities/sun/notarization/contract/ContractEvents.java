package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.gui.ModMenuTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ContractEvents {
    private ContractEvents() {}

    @SubscribeEvent public static void onTick(ServerTickEvent.Post e) { ContractManager.tick(e.getServer()); }

    @SubscribeEvent public static void onToss(ItemTossEvent e) {
        if (e.getPlayer() instanceof ServerPlayer sp) ContractManager.onToss(sp, e.getEntity());
    }

    @SubscribeEvent public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer v) ContractManager.onHarm(v, e.getSource().getEntity(), true, 1f);
    }

    @SubscribeEvent public static void onDamage(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer v) ContractManager.onHarm(v, e.getSource().getEntity(), false, e.getNewDamage());
    }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ContractManager.onLogin(p);
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ContractManager.onLogout(p);
    }

    @SubscribeEvent public static void onStopped(ServerStoppedEvent e) { ContractManager.reset(); }
}
