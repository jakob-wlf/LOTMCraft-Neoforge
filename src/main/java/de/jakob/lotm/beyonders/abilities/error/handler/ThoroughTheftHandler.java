package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ThoroughTheftHandler {

    public static final int MAX_RANGE = 50;
    private static final int EQUIPMENT_OFFSET = 100;
    private static final int LOOT_OFFSET = 200;

    private static final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private static class Session {
        final UUID target;
        final long expiresAt;
        int picksLeft;
        final Set<Integer> hidden = new HashSet<>();
        final Set<Integer> taken = new HashSet<>();
        final List<ItemStack> virtualLoot = new ArrayList<>();

        Session(UUID target, long expiresAt, int picks) {
            this.target = target;
            this.expiresAt = expiresAt;
            this.picksLeft = picks;
        }
    }

    public static void open(ServerPlayer thief, LivingEntity target, Ability skill) {
        ServerLevel level = thief.serverLevel();
        Random random = new Random();

        int thiefSeq = AbilityUtil.getSeqWithArt(thief, skill);
        int targetSeq = ErrorUtil.seqOf(target);

        if (BeyonderData.isBeyonder(target) && AbilityUtil.isTargetSignificantlyStronger(thiefSeq, targetSeq)) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.theft.too_strong").withColor(ErrorUtil.ERROR_COLOR));
            return;
        }
        if (BeyonderData.isBeyonder(target) && TheftHandler.doesTheftFail(thief, target, random, skill)) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.theft.failed").withColor(ErrorUtil.ERROR_COLOR));
            return;
        }

        int difference = targetSeq - thiefSeq;
        int seconds;
        int picks;
        if (difference < 0) {
            seconds = 8;
            picks = 1;
        } else {
            seconds = Math.min(40, 10 + 5 * difference);
            picks = Math.min(6, 1 + difference);
        }

        Session session = new Session(target.getUUID(), level.getGameTime() + seconds * 20L, picks);

        ListTag slots = new ListTag();
        if (target instanceof Player victim) {
            boolean inventoryFull = victim.getInventory().getFreeSlot() == -1;
            List<Integer> filled = new ArrayList<>();

            for (int i = 0; i < 41; i++) {
                ItemStack stack = getPlayerSlot(victim, i);
                if (!stack.isEmpty()) filled.add(i);
            }

            double hideChance = difference < 0 ? 0.5 : (inventoryFull ? 0.25 : 0);
            for (int index : filled) {
                if (random.nextDouble() < hideChance) session.hidden.add(index);
            }

            for (int i = 0; i < 41; i++) {
                slots.add(writeSlot(level, i, getPlayerSlot(victim, i), session.hidden.contains(i)));
            }
        } else {
            EquipmentSlot[] equipment = EquipmentSlot.values();
            for (int i = 0; i < equipment.length; i++) {
                ItemStack stack = target.getItemBySlot(equipment[i]);
                if (stack.isEmpty()) continue;
                boolean hide = difference < 0 && random.nextBoolean();
                if (hide) session.hidden.add(EQUIPMENT_OFFSET + i);
                slots.add(writeSlot(level, EQUIPMENT_OFFSET + i, stack, hide));
            }

            for (TheftHandler.TheftLoot loot : TheftHandler.getLootForEntity(target)) {
                int amount = loot.minAmount() + random.nextInt(loot.maxAmount() - loot.minAmount() + 1);
                if (amount <= 0) continue;
                ItemStack stack = loot.loot().copyWithCount(amount);
                int index = LOOT_OFFSET + session.virtualLoot.size();
                session.virtualLoot.add(stack);
                slots.add(writeSlot(level, index, stack, false));
            }
        }

        if (slots.isEmpty()) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.theft.nothing").withColor(ErrorUtil.ERROR_COLOR));
            return;
        }

        sessions.put(thief.getUUID(), session);

        CompoundTag data = new CompoundTag();
        data.putInt("target", target.getId());
        data.putString("name", target.getName().getString());
        data.putBoolean("player", target instanceof Player);
        data.putInt("ticks", seconds * 20);
        data.putInt("picks", picks);
        data.put("slots", slots);
        ErrorUtil.send(thief, "thorough_open", data);

        level.playSound(null, thief.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.3f, 1.4f);
    }

    private static CompoundTag writeSlot(ServerLevel level, int index, ItemStack stack, boolean hidden) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("i", index);
        tag.putBoolean("h", hidden);
        if (!hidden && !stack.isEmpty()) {
            tag.put("item", stack.saveOptional(level.registryAccess()));
        }
        return tag;
    }

    private static ItemStack getPlayerSlot(Player player, int index) {
        if (index < 36) return player.getInventory().items.get(index);
        if (index < 40) return player.getInventory().armor.get(index - 36);
        return player.getInventory().offhand.get(0);
    }

    private static void clearPlayerSlot(Player player, int index) {
        if (index < 36) player.getInventory().items.set(index, ItemStack.EMPTY);
        else if (index < 40) player.getInventory().armor.set(index - 36, ItemStack.EMPTY);
        else player.getInventory().offhand.set(0, ItemStack.EMPTY);
    }

    public static void pick(ServerPlayer thief, int index) {
        Session session = sessions.get(thief.getUUID());
        ServerLevel level = thief.serverLevel();
        if (session == null || session.picksLeft <= 0 || level.getGameTime() > session.expiresAt) {
            close(thief);
            return;
        }
        if (session.taken.contains(index)) return;

        Entity raw = level.getEntity(session.target);
        if (!(raw instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(thief) > MAX_RANGE) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.theft.out_of_reach").withColor(ErrorUtil.ERROR_COLOR));
            close(thief);
            return;
        }

        ItemStack stolen = ItemStack.EMPTY;
        if (target instanceof Player victim && index >= 0 && index < 41) {
            ItemStack live = getPlayerSlot(victim, index);
            if (!live.isEmpty()) {
                stolen = live.copy();
                clearPlayerSlot(victim, index);
            }
        } else if (index >= EQUIPMENT_OFFSET && index < LOOT_OFFSET) {
            EquipmentSlot[] equipment = EquipmentSlot.values();
            int slot = index - EQUIPMENT_OFFSET;
            if (slot < equipment.length) {
                ItemStack live = target.getItemBySlot(equipment[slot]);
                if (!live.isEmpty()) {
                    stolen = live.copy();
                    target.setItemSlot(equipment[slot], ItemStack.EMPTY);
                }
            }
        } else if (index >= LOOT_OFFSET && index - LOOT_OFFSET < session.virtualLoot.size()) {
            stolen = session.virtualLoot.get(index - LOOT_OFFSET).copy();
        }

        session.taken.add(index);

        if (stolen.isEmpty()) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.theft.slot_empty").withColor(ErrorUtil.ERROR_COLOR));
        } else {
            session.picksLeft--;
            ErrorUtil.markOwner(stolen, target);
            ErrorUtil.giveOrDrop(thief, stolen.copy());
            level.playSound(null, thief.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        }

        CompoundTag data = new CompoundTag();
        data.putInt("i", index);
        data.putInt("picks", session.picksLeft);
        if (!stolen.isEmpty()) data.put("item", stolen.saveOptional(level.registryAccess()));
        ErrorUtil.send(thief, "thorough_update", data);

        if (session.picksLeft <= 0) {
            close(thief);
        }
    }

    public static void close(ServerPlayer thief) {
        if (sessions.remove(thief.getUUID()) != null) {
            ErrorUtil.send(thief, "thorough_close", new CompoundTag());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (sessions.isEmpty() || event.getServer().getTickCount() % 5 != 0) return;

        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            ServerPlayer thief = event.getServer().getPlayerList().getPlayer(uuid);
            Session session = sessions.get(uuid);
            if (thief == null) {
                sessions.remove(uuid);
                continue;
            }
            if (session != null && thief.serverLevel().getGameTime() > session.expiresAt) {
                close(thief);
            }
        }
    }
}
