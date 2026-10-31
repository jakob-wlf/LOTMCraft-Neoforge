package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GodSaysItsEffectiveAbility extends Ability {
    public GodSaysItsEffectiveAbility(String id) {
        super(id, 20);

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3000f, 1100f, 670f, 420f, 410f, 270f, 240f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 6));
    }

    @Override
    protected float getSpiritualityCost() {
        return 25;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        level.playSound(null, entity.position().x, entity.position().y, entity.position().z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1, 1);
        BeyonderData.addModifier(entity, "notary_buff", 1.15);
        ServerLevel serverLevel = (ServerLevel) level;
        FxHandler.play(serverLevel, "notary_proclaim", entity.position().add(0, .05, 0), 1);
        UUID blessing = FxHandler.playOnBody(serverLevel, "notary_blessing", entity);
        ServerScheduler.scheduleDelayed(20 * 20, () -> {
            BeyonderData.removeModifier(entity, "notary_buff");
            FxHandler.cancelOnEntity(serverLevel, blessing);
        }, serverLevel, () -> AbilityUtil.getTimeInArea(entity, new Location(entity.position(), level)));
    }
}
