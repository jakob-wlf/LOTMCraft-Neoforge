package de.jakob.lotm.beyonders.abilities.door.passives;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.events.custom.AbilitySealedEvent;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class SealingAuthorityPassiveAbility extends PassiveAbility {

    private static SealingAuthorityPassiveAbility instance;

    public SealingAuthorityPassiveAbility(String id) {
        super(id);
        instance = this;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 2));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    public static boolean applies(LivingEntity entity) {
        return entity != null && instance != null && instance.shouldApplyTo(entity);
    }

    public static int sealingSeq(LivingEntity entity, int seq) {
        return applies(entity) ? Math.max(0, seq - 1) : seq;
    }

    public static boolean unsealable(LivingEntity entity) {
        return applies(entity) && BeyonderData.getSequence(entity) <= 0;
    }

    @SubscribeEvent
    public static void onSealed(AbilitySealedEvent event) {
        if (unsealable(event.getTargetEntity())) event.setCanceled(true);
    }
}
