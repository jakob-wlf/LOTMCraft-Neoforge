package de.jakob.lotm.beyonders.abilities.darkness.passives;

import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class ConcealmentSenseAbility extends PassiveAbility {
    public ConcealmentSenseAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 2));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }
}
