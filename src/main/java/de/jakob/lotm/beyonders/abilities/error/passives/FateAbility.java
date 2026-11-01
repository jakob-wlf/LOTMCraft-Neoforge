package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.common.DivinationAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FateAbility extends PassiveAbility {

    private static final Map<UUID, Set<UUID>> knownImpostors = new ConcurrentHashMap<>();

    public FateAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) return;

        DivinationAbility.DIVINATION_IMMUNE.add(player.getUUID());

        if (player.tickCount % 40 != 0) return;

        String ownId = player.getUUID().toString();
        Set<UUID> known = knownImpostors.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        Set<UUID> current = new HashSet<>();

        for (ServerPlayer other : serverLevel.players()) {
            if (other == player || other.distanceTo(player) > 500) continue;
            String shape = other.getData(ModAttachments.SHAPE_SHIFT).getShape();
            if (shape == null || !shape.startsWith("player:") || !shape.endsWith(ownId)) continue;

            current.add(other.getUUID());
            if (known.add(other.getUUID())) {
                player.sendSystemMessage(Component.translatable("ability.lotmcraft.fate.impostor",
                        other.getGameProfile().getName(), (int) other.distanceTo(player)).withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        known.retainAll(current);
    }

    @Override
    public void onPassiveAbilityRemoved(LivingEntity entity, ServerLevel serverLevel) {
        DivinationAbility.DIVINATION_IMMUNE.remove(entity.getUUID());
        knownImpostors.remove(entity.getUUID());
    }
}
