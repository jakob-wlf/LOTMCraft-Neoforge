package de.jakob.lotm.beyonders.abilities.death.passives;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class AbsoluteDeathAbility extends PassiveAbility {
    private static final int RADIUS = 30;

    public AbsoluteDeathAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("death", 2));
    }

    @Override
    public void tick(Level level, LivingEntity entity){
        if (!(level instanceof ServerLevel serverLevel)) return;

        long until = serverLevel.getGameTime() + 10;
        AbilityUtil.getNearbyEntities(entity, serverLevel, entity.position(), RADIUS).forEach(target -> {
            if(AllyUtil.areAllies(entity, target)) return;
            target.setData(ModAttachments.ABSOLUTE_DEATH_UNTIL, until);
        });
    }

    public static boolean isEffectedByAbsoluteDeath(LivingEntity entity) {
        return entity.level().getGameTime() < entity.getData(ModAttachments.ABSOLUTE_DEATH_UNTIL);
    }
}