package de.jakob.lotm.beyonders.abilities.abyss.contract.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.abyss.contract.registry.SoulOwnershipRegistry;
import de.jakob.lotm.beyonders.abilities.core.PhysicalEnhancementsAbility;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ContractSoulHandler {
    private static final float SANITY_DAMAGE = 0.2f;
    private static final Map<UUID, UUID> activeDrains = new ConcurrentHashMap<>(); // soulOwner -> soulHolder
    private static final int DRAIN_INTERVAL_TICKS = 20;
    private static int tickCounter = 0;
    public static void startDrain(UUID soulOwner, UUID soulHolder) {
        activeDrains.put(soulOwner, soulHolder);
    }
    public static void consumeSoul(ServerPlayer holder, UUID ownerUUID) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || holder == null) return;
        LivingEntity owner = null;
        // First check if the soul owner is a player.
        owner = server.getPlayerList().getPlayer(ownerUUID);
        // If not a player, find the NPC in the loaded server worlds.
        if (owner == null) {
            for (var level : server.getAllLevels()) {
                Entity entity = level.getEntity(ownerUUID);
                if (entity instanceof   LivingEntity living) {
                    owner = living;
                    break;
                }
            }
        }
        if (owner == null) return;
        // Keep the drain active until the owner dies.
        activeDrains.put(ownerUUID, holder.getUUID());
        // Grant the soul's pathway enhancements to the player consuming it.
        PhysicalEnhancementsAbility ability = SoulBuffs.getAbility(BeyonderData.getPathway(owner));
        if (ability != null) SoulBuffs.grant(holder, ability, BeyonderData.getSequence(owner));
        // Sanity damage only applies if the soul owner is a player.
        if (owner instanceof ServerPlayer ownerPlayer) {
            int holderSeq = BeyonderData.getSequence(holder);
            ownerPlayer.getData(ModAttachments.SANITY_COMPONENT).decreaseSanityWithSequenceDifference(SANITY_DAMAGE, ownerPlayer, holderSeq, BeyonderData.getSequence(ownerPlayer));
        }
    }


    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < DRAIN_INTERVAL_TICKS) return;
        tickCounter = 0;
        if (activeDrains.isEmpty()) return;
        var server = event.getServer();
        for (Map.Entry<UUID, UUID> entry : activeDrains.entrySet()) {
            UUID ownerUUID = entry.getKey();
            UUID holderUUID = entry.getValue();
            LivingEntity owner = null;
            // Check players first.
            owner = server.getPlayerList().getPlayer(ownerUUID);
            // If not a player, look for the NPC in loaded worlds.
            if (owner == null) {
                for (var level : server.getAllLevels()) {
                    Entity entity = level.getEntity(ownerUUID);
                    if (entity instanceof LivingEntity living) {
                        owner = living;
                        break;
                    }
                }
            }
            if (owner == null) continue;
            if (owner.isDeadOrDying()) {
                activeDrains.remove(ownerUUID);
                SoulOwnershipRegistry.release(ownerUUID);
                continue;
            }
            ServerPlayer holder = server.getPlayerList().getPlayer(holderUUID);
            if (holder == null) {
                activeDrains.remove(ownerUUID);
                continue;
            }
            int holderSeq = BeyonderData.getSequence(holder);
            owner.getData(ModAttachments.SANITY_COMPONENT).decreaseSanityWithSequenceDifference(SANITY_DAMAGE, owner, holderSeq, BeyonderData.getSequence(owner));
            if (owner instanceof ServerPlayer) ContractDeathHandler.markContractDeath(ownerUUID);
        }
    }

    public static void cancelDrain(UUID soulOwnerUUID) {if (soulOwnerUUID != null) activeDrains.remove(soulOwnerUUID);}
}