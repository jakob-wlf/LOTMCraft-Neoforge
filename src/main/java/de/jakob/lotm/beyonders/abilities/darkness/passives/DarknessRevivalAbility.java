package de.jakob.lotm.beyonders.abilities.darkness.passives;

import de.jakob.lotm.beyonders.abilities.death.passives.AbsoluteDeathAbility;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbilityHandler;
import de.jakob.lotm.beyonders.abilities.justiciar.LawAbility;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeEnforcement;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DarknessRevivalAbility extends PassiveAbility {
    private static final DustParticleOptions dust = new DustParticleOptions(new Vector3f(20 / 255f, 0 / 255f, 40 / 255f), 2f);
    private static final Set<String> COUNTER_PATHWAY = Set.of("death","mother");

    public DarknessRevivalAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 1));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSeq0Damage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        //if (LawAbility.SOLACE_KILLED.contains(entity.getUUID())) return; // I am super unsure about it
        if (JusticeEnforcement.inHalo(entity)) return;
        if (!"darkness".equals(BeyonderData.getPathway(entity)) || BeyonderData.getSequence(entity) != 0) return;
        var pos = entity.blockPosition();
        int block = serverLevel.getBrightness(LightLayer.BLOCK, pos);
        int sky = serverLevel.getBrightness(LightLayer.SKY, pos);
        if (serverLevel.isDay() && sky >= 7) return;
        if (block >= 7) return;
        float health = entity.getHealth();
        if (health - event.getAmount() >= 2) return;
        float allowed = health - 2;
        if (allowed <= 0f) {
            event.setCanceled(true);
            event.setAmount(0);
            if (health < 2) entity.setHealth(2);
            return;
        }
        event.setAmount(allowed);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        if (LawAbility.SOLACE_KILLED.contains(entity.getUUID())) return;
        if (JusticeEnforcement.inHalo(entity)) return;
        if (AbsoluteDeathAbility.isEffectedByAbsoluteDeath(entity)) return;

        boolean seq1 = "darkness".equals(BeyonderData.getPathway(entity)) && BeyonderData.getSequence(entity) == 1;
        if (seq1 && event.getSource().getEntity() instanceof LivingEntity killer
                && COUNTER_PATHWAY.contains(BeyonderData.getPathway(killer))
                && BeyonderData.getSequence(killer) == BeyonderData.getSequence(entity)) return;

        if (seq1) {
            event.setCanceled(true);
            rise(serverLevel, entity, entity.getMaxHealth() / 2);
            entity.getData(ModAttachments.SANITY_COMPONENT).increaseSanityAndSync(-5f, entity);
            return;
        }

        PassiveAbility revival = PassiveAbilityHandler.getById("darkness_revival_ability");
        if (revival == null || !revival.shouldApplyTo(entity)) return;

        event.setCanceled(true);
        rise(serverLevel, entity, entity.getMaxHealth());
    }

    private static void rise(ServerLevel level, LivingEntity entity, float health) {
        entity.setHealth(health);
        ParticleUtil.spawnParticles(level, dust, entity.position().add(0, entity.getEyeHeight() / 2, 0),
                40, .5, entity.getEyeHeight() / 2, .5, 0.1);
    }
}
