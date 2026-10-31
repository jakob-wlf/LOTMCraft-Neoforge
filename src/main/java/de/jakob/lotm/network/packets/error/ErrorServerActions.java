package de.jakob.lotm.network.packets.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.error.*;
import de.jakob.lotm.beyonders.abilities.error.handler.DeceitHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.DeceptionTrapHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ThoroughTheftHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ErrorServerActions {

    private ErrorServerActions() {
    }

    private static boolean has(ServerPlayer player, String abilityId) {
        Ability ability = LOTMCraft.abilityHandler.getById(abilityId);
        return ability != null && ability.hasAbility(player, false);
    }

    public static void handle(ServerPlayer player, String key, CompoundTag data) {
        switch (key) {
            case "typing" -> {
                EloquenceAbility ability = (EloquenceAbility) LOTMCraft.abilityHandler.getById("eloquence_ability");
                if (ability != null && ability.isActiveForEntity(player)) {
                    EloquenceAbility.setTyping(player, data.getBoolean("typing"));
                }
            }
            case "thorough_pick" -> ThoroughTheftHandler.pick(player, data.getInt("slot"));
            case "thorough_close" -> ThoroughTheftHandler.close(player);
            case "gift_concept" -> {
                if (LOTMCraft.abilityHandler.getById("gift_ability") instanceof GiftAbility gift && gift.hasAbility(player, false)) {
                    gift.giftFromMenu(player, data.getString("kind"), data.getString("id"), data.getInt("index"));
                }
            }
            case "deceit_config" -> {
                if (has(player, "deceive_minds_ability")) {
                    DeceitHandler.setConfig(player, data.getCompound("config"));
                }
            }
            case "deceit_apply" -> {
                if (has(player, "deceive_minds_ability")) {
                    DeceiveMindsAbility.get().applyFromWheel(player, data.getInt("choice"));
                }
            }
            case "fake_death_respawn" -> DeceitHandler.onFakeRespawn(player);
            case "trap_config" -> {
                if (has(player, "deceive_connection_ability")) {
                    DeceptionTrapHandler.setConfig(player, readVec(data, "in"), readVec(data, "out"));
                }
            }
            case "self_char" -> {
                if (has(player, "steal_characteristic_ability")) {
                    StealCharacteristicAbility.get().extractFromSelf(player, data.getInt("seq"));
                }
            }
            default -> LOTMCraft.LOGGER.debug("Unknown error pathway action {}", key);
        }
    }

    private static Vec3 readVec(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = data.getCompound(key);
        return new Vec3(tag.getInt("x") + 0.5, tag.getInt("y"), tag.getInt("z") + 0.5);
    }
}
