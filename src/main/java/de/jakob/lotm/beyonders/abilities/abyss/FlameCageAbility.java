package de.jakob.lotm.beyonders.abilities.abyss;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.DamageLookup;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlameCageAbility extends Ability {

    private static final Map<UUID, Vec3> activeCages = new HashMap<>();

    public FlameCageAbility(String id) {
        super(id, 20f, "burning");
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("abyss", 6));
    }

    @Override
    public float getSpiritualityCost() {
        return 150;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (activeCages.containsKey(entity.getUUID())) return;

        LivingEntity targetEntity = AbilityUtil.getTargetEntity(entity, 20, 2.0f);
        Vec3 cageCenter = targetEntity != null
                ? targetEntity.position()
                : AbilityUtil.getTargetLocation(entity, 20, 1.5f);

        double radius  = 5.0 * multiplier(entity);
        int    duration = 20 * 6 * (int) Math.max(multiplier(entity) / 2, 1);

        activeCages.put(entity.getUUID(), cageCenter);

        serverLevel.playSound(null, BlockPos.containing(cageCenter),
                SoundEvents.BLAZE_AMBIENT, SoundSource.BLOCKS, 3.0f, 0.6f);

        // the effect is authored for a radius of 5 blocks
        UUID cageFx = FxHandler.playCancellable(serverLevel, "flame_cage", cageCenter, radius / 5.0);

        ServerScheduler.scheduleForDuration(0, 3, duration, () -> {
            double dps = DamageLookup.lookupDps(6, 0.7, 3, 60) * multiplier(entity);
            AbilityUtil.damageNearbyEntities(serverLevel, entity,
                    radius - 0.5, dps, cageCenter, true, false,
                    true, 0,
                    ModDamageTypes.source(serverLevel, ModDamageTypes.BEYONDER_GENERIC, entity));

            AbilityUtil.getNearbyEntities(entity, serverLevel, cageCenter, radius + 1.5)
                    .stream()
                    .filter(t -> AbilityUtil.mayDamage(entity, t))
                    .forEach(t -> {
                        Vec3 horiz = new Vec3(t.getX() - cageCenter.x, 0, t.getZ() - cageCenter.z);
                        if (horiz.length() > radius - 0.3) {
                            Vec3 inward = horiz.normalize().scale(-0.45);
                            t.setDeltaMovement(t.getDeltaMovement().add(inward));
                            t.hurtMarked = true;
                            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 5, false, false, false));
                        }

                        if (random.nextInt(5) == 0) {
                            t.setRemainingFireTicks(20 * 3);
                        }
                    });
        }, () -> {
            activeCages.remove(entity.getUUID());
            FxHandler.cancel(serverLevel, cageFx);
            FxHandler.play(serverLevel, "flame_cage_collapse", cageCenter, radius / 5.0);
            serverLevel.playSound(null, cageCenter.x, cageCenter.y, cageCenter.z,
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 2.0f, 0.8f);
        }, serverLevel, () -> AbilityUtil.getTimeInArea(entity, new Location(cageCenter, serverLevel)));

        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel, cageCenter, entity, this,
                interactionFlags, radius, duration));
    }
}
