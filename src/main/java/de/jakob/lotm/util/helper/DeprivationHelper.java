package de.jakob.lotm.util.helper;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Timed, persistent power locks. A selectable power is identified by its translation key, not menu index. */
public final class DeprivationHelper {
    private static final String DATA_KEY = "lotmcraft_deprivation";

    private DeprivationHelper() {}

    public static boolean isProtected(Ability ability) {
        return ability.cannotBeStolen || !ability.canBeReplicated;
    }

    private static String key(Ability ability, String mode) {
        return ability.getId() + (mode.isEmpty() ? "" : "#" + mode);
    }

    public static boolean isDisabled(LivingEntity entity, Ability ability, String mode) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        CompoundTag locks = entity.getPersistentData().getCompound(DATA_KEY);
        String key = key(ability, mode);
        if (isProtected(ability)) {
            // Previously saved deprivation must not continue sealing a now-protected authority.
            locks.remove(key);
            return false;
        }
        long expiry = locks.getLong(key);
        if (expiry <= level.getServer().overworld().getGameTime()) {
            locks.remove(key);
            return false;
        }
        return true;
    }

    public static void disable(LivingEntity entity, Ability ability, String mode, int ticks) {
        if (isProtected(ability)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        CompoundTag locks = entity.getPersistentData().getCompound(DATA_KEY);
        locks.putLong(key(ability, mode), level.getServer().overworld().getGameTime() + ticks);
        entity.getPersistentData().put(DATA_KEY, locks);
    }

    public static boolean isModeDisabled(LivingEntity entity, SelectableAbility ability, int index) {
        String[] names = ability.getSequenceDependantAbilityNames(BeyonderData.getSequence(entity));
        return index >= 0 && index < names.length && isDisabled(entity, ability, names[index]);
    }

    public static boolean blocksUse(LivingEntity entity, Ability ability) {
        if (ability instanceof SelectableAbility selectable) {
            if (entity instanceof Player) {
                return isModeDisabled(entity, selectable, selectable.getCastSubAbilityIndex(entity));
            }
            String[] names = selectable.getSequenceDependantAbilityNames(BeyonderData.getSequence(entity));
            for (String name : names) {
                if (!isDisabled(entity, ability, name)) return false;
            }
            return names.length > 0;
        }
        return isDisabled(entity, ability, "");
    }

    public static void notifyBlocked(LivingEntity entity) {
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.deprivation.blocked")
                .withStyle(ChatFormatting.RED));
    }
}
