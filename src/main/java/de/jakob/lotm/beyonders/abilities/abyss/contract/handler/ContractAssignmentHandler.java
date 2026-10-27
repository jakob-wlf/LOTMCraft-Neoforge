package de.jakob.lotm.beyonders.abilities.abyss.contract.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.contract.ContractData;
import de.jakob.lotm.beyonders.abilities.abyss.contract.ContractFinalizer;
import de.jakob.lotm.beyonders.abilities.abyss.contract.ContractItem;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ContractAssignmentHandler {

    private record PendingRequest(UUID casterUUID, UUID contractUUID) {}
    private static final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<>();
    public static void sendRequest(ServerPlayer caster, ServerPlayer target, UUID contractUUID, ContractData data) {
        pending.put(target.getUUID(), new PendingRequest(caster.getUUID(), contractUUID));
        ContractData preview = ContractData.of(data.toTag().copy());
        String role;
        if (preview.getPartyA() == null) {
            preview.setPartyAName(target.getName().getString());
            role = "Party A";
        } else {
            preview.setPartyBName(target.getName().getString());
            role = "Party B";
        }
        target.sendSystemMessage(Component.literal("══════════════════════════════").withColor(0xFFa3070c));
        target.sendSystemMessage(Component.literal(caster.getName().getString() + " wants to bind you to a contract as " + role + ":").withColor(0xFFa3070c));
        target.sendSystemMessage(Component.literal(preview.describe()).withColor(0xCCCCCC));
        target.sendSystemMessage(Component.literal("Type 'I accept' or 'I decline'").withColor(0xFFa3070c));
        target.sendSystemMessage(Component.literal("══════════════════════════════").withColor(0xFFa3070c));
        AbilityUtil.sendActionBar(caster, Component.literal("Request sent to " + target.getName().getString() + ".").withColor(0xFFa3070c));
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void onChatMessage(ServerChatEvent event) {
        ServerPlayer target = event.getPlayer();
        PendingRequest req = pending.get(target.getUUID());
        if (req == null) return;
        String msg = event.getRawText().trim().toLowerCase();
        if (msg.equals("i accept")) {
            event.setCanceled(true);
            pending.remove(target.getUUID());
            resolveAccept(target, req);
        } else if (msg.equals("i decline")) {
            event.setCanceled(true);
            pending.remove(target.getUUID());
            ServerPlayer caster = target.server.getPlayerList().getPlayer(req.casterUUID());
            if (caster != null) AbilityUtil.sendActionBar(caster, Component.literal(target.getName().getString() + " declined.").withColor(0xFFff124d));
        }
    }
    private static void resolveAccept(ServerPlayer target, PendingRequest req) {
        ServerPlayer caster = target.server.getPlayerList().getPlayer(req.casterUUID());
        if (caster == null) {
            AbilityUtil.sendActionBar(target, Component.literal("The broker is no longer online.").withColor(0xFFff124d));
            return;
        }
        for (ItemStack stack : caster.getInventory().items) {
            if (!(stack.getItem() instanceof ContractItem)) continue;
            ContractData data = ContractItem.readData(stack);
            if (!data.isActive() || data.hasBothParties()) continue;
            if (!req.contractUUID().equals(data.contractUUID())) continue;
            if (data.getPartyA() == null) {
                data.setPartyA(target.getUUID());
                data.setPartyAName(target.getName().getString());
            } else {
                data.setPartyB(target.getUUID());
                data.setPartyBName(target.getName().getString());
            }
            if (data.hasBothParties()) {
                ContractFinalizer.activate(data);
                AbilityUtil.sendActionBar(caster, Component.literal("Contract formed.").withColor(0xFFa3070c));
                AbilityUtil.sendActionBar(target, Component.literal("Contract formed.").withColor(0xFFa3070c));
            }
            else {AbilityUtil.sendActionBar(caster, Component.literal(target.getName().getString() + " has joined. One role remains.").withColor(0xFFa3070c));}
            ContractItem.writeData(stack, data);
            return;
        }
        AbilityUtil.sendActionBar(target, Component.literal("That contract could no longer be found.").withColor(0xFFa3070c));
    }

    public static void assignNpc(ServerPlayer caster, LivingEntity npc, UUID contractUUID) {
        for (ItemStack stack : caster.getInventory().items) {
            if (!(stack.getItem() instanceof ContractItem)) continue;
            ContractData data = ContractItem.readData(stack);
            if (!data.isActive() || data.hasBothParties() || !contractUUID.equals(data.contractUUID()) || !caster.getUUID().equals(data.getPartyA())) {
                continue;
            }
            if (!NpcContractHandler.canAccept(caster, npc, data)) {return;}
            NpcContractHandler.sign(caster, npc, stack, data);
            return;
        }
        AbilityUtil.sendActionBar(caster, Component.literal("That contract could no longer be found.").withColor(0xFFa3070c));
    }

}