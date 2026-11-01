package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class SuperiorObservationFocusAbility extends ToggleAbility {

    public SuperiorObservationFocusAbility(String id) {
        super(id);
        canBeUsedByNPC = false;
        tickRate = 20;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 6));
    }

    @Override
    protected float getSpiritualityCost() {
        return 1f;
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.observationFocusActive = true;
            entity.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.6f);
        }
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide) ErrorClientFlags.observationFocusActive = false;
    }
}
