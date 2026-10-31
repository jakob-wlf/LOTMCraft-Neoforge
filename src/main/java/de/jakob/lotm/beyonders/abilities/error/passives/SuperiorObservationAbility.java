package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.DeceitHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.ObservationHandler;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class SuperiorObservationAbility extends PassiveAbility {

    public SuperiorObservationAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 9));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel) || !(entity instanceof ServerPlayer player)) return;

        int seq = BeyonderData.getSequence(player);

        if (player.tickCount % 20 == 0) {
            ObservationHandler.scanValuables(player);
        }

        if (seq <= 8) {
            tickSanityReading(player);
        }

        if (seq <= 3 && player.tickCount % 20 == 0) {
            ObservationHandler.sendConnections(player);
            ObservationHandler.sendApproachingThreats(player);
        }
    }

    private void tickSanityReading(ServerPlayer player) {
        int ticks = ObservationHandler.tickFaceLook(player, 5, 20);
        if (ticks < ObservationHandler.SANITY_LOOK_TICKS || player.tickCount % 10 != 0) return;

        LivingEntity target = ObservationHandler.getLookedAtFace(player, 20);
        if (target == null) return;

        CompoundTag data = new CompoundTag();
        data.putInt("id", target.getId());
        data.putFloat("sanity", DeceitHandler.getObservedSanity(player, target));
        ErrorUtil.send(player, "sanity_reveal", data);
    }

    @Override
    public void onPassiveAbilityRemoved(LivingEntity entity, ServerLevel serverLevel) {
        ObservationHandler.clear(entity.getUUID());
        if (entity instanceof ServerPlayer player) {
            ErrorUtil.send(player, "observation_reset", new CompoundTag());
        }
    }
}
