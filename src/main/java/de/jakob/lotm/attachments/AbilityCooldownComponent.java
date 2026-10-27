package de.jakob.lotm.attachments;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.SyncAbilityCooldownsPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.Map;

public class AbilityCooldownComponent implements INBTSerializable<CompoundTag> {
    private final Map<String, Integer> cooldowns = new HashMap<>();

    // Sends the entity's full cooldown map to its client; no-op for non-players
    public static void syncToClient(LivingEntity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            Map<String, Integer> cooldowns = new HashMap<>(serverPlayer.getData(ModAttachments.COOLDOWN_COMPONENT).getCooldowns());
            PacketHandler.sendToPlayer(serverPlayer, new SyncAbilityCooldownsPacket(cooldowns));
        }
    }

    public void setCooldown(String abilityId, int ticks) {
        cooldowns.put(abilityId, ticks);
    }
    
    public boolean isOnCooldown(String abilityId) {
        return cooldowns.getOrDefault(abilityId, 0) > 0;
    }
    
    public int getRemainingCooldown(String abilityId) {
        return cooldowns.getOrDefault(abilityId, 0);
    }

    public void removeAllCooldowns() {
        cooldowns.clear();
    }

    public void tick() {
        cooldowns.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    public Map<String, Integer> getCooldowns(){
        return cooldowns;
    }

    public void setCooldowns(Map<String, Integer> newCooldowns) {
        cooldowns.clear();
        cooldowns.putAll(newCooldowns);
    }   

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        CompoundTag cooldownsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
            cooldownsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("cooldowns", cooldownsTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        cooldowns.clear();
        CompoundTag cooldownsTag = tag.getCompound("cooldowns");
        for (String key : cooldownsTag.getAllKeys()) {
            cooldowns.put(key, cooldownsTag.getInt(key));
        }
    }
}