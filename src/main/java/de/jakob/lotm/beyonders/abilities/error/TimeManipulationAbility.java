package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.AbilityHoldEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ConceptStorage;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.TimeHandler;
import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.entity.custom.ability_entities.TimeChangeEntity;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class TimeManipulationAbility extends SelectableAbility {

    private static final int FIELD_DURATION = 20 * 10;
    private static final int FIELD_RADIUS = 50;

    @SubscribeEvent
    public static void onHold(AbilityHoldEvent event) {
        LivingEntity entity = event.getEntity();
        TimeChangeEntity newest = null;
        for (TimeChangeEntity change : entity.level().getEntitiesOfClass(TimeChangeEntity.class, entity.getBoundingBox().inflate(FIELD_RADIUS))) {
            if (entity.distanceTo(change) > change.getRadius()) continue;
            if (newest == null || change.tickCount < newest.tickCount) newest = change;
        }
        if (newest != null) event.multiply(newest.getTimeMultiplier());
    }

    public TimeManipulationAbility(String id) {
        super(id, 17);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(10, 15));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(20000f, 10000f));

        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 1));
    }

    @Override
    protected float getSpiritualityCost() {
        return 10000f;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.time_manipulation.stop_time",
                "ability.lotmcraft.time_manipulation.accelerate_time",
                "ability.lotmcraft.time_manipulation.slow_time",
                "ability.lotmcraft.time_manipulation.reverse_time",
                "ability.lotmcraft.time_manipulation.summon",
                "ability.lotmcraft.time_manipulation.steal_time",
                "ability.lotmcraft.time_manipulation.day_night"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if(!(level instanceof ServerLevel serverLevel))
            return;

        switch (selectedAbility) {
            case 0, 1, 2 -> timeField(serverLevel, entity, selectedAbility);
            case 3 -> reverseTime(serverLevel, entity);
            case 4 -> summonClock(serverLevel, entity);
            case 5 -> stealTime(serverLevel, entity);
            case 6 -> stealDayNight(serverLevel, entity);
        }
    }

    private Vec3 clockPosition(LivingEntity entity) {
        return entity.position().add(0, entity.getBbHeight() + 2.2, 0);
    }

    private void timeField(ServerLevel level, LivingEntity entity, int mode) {
        level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, mode == 1 ? 1.6f : mode == 2 ? 0.6f : 1f);

        ParticleUtil.spawnParticles(level, ParticleTypes.ENCHANT, entity.getEyePosition(), 400, 10, 2, 10, 0.05);
        ParticleUtil.spawnParticles(level, ParticleTypes.END_ROD, entity.getEyePosition(), 100, 10, 2, 10, 0.05);

        String effect = switch (mode) {
            case 0 -> "time_clock_stop";
            case 1 -> "time_clock_accelerate";
            default -> "time_clock_slow";
        };
        FxHandler.play(level, effect, clockPosition(entity), 1.6);

        float timeMultiplier = mode == 0 ? 0.001f : (mode == 1 ? 4f : 0.2f);
        TimeChangeEntity timeChangeEntity = new TimeChangeEntity(ModEntities.TIME_CHANGE.get(), level, FIELD_DURATION, entity.getUUID(), FIELD_RADIUS, timeMultiplier);
        timeChangeEntity.setPos(entity.getX(), entity.getY(), entity.getZ());
        level.addFreshEntity(timeChangeEntity);
    }

    private void reverseTime(ServerLevel level, LivingEntity entity) {
        boolean started = TimeHandler.startOrTriggerReverse(level, entity, 30, 20 * 8);
        level.playSound(null, entity.blockPosition(), started ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 0.8f);
        AbilityUtil.sendActionBar(entity, Component.translatable(started ? "ability.lotmcraft.time_manipulation.reverse_recording" : "ability.lotmcraft.time_manipulation.reverse_triggered")
                .withColor(ErrorUtil.TIME_COLOR));
    }

    private void summonClock(ServerLevel level, LivingEntity entity) {
        Vec3 pos = AbilityUtil.getTargetLocation(entity, 30, 2).add(0, 3, 0);
        TimeHandler.summonClock(level, entity, pos, 20 * 30);
        level.playSound(null, entity.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.5f);
    }

    private void stealTime(ServerLevel level, LivingEntity entity) {
        LivingEntity target = AbilityUtil.getTargetEntity(entity, 50, 2);
        if (target == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.time_manipulation.no_target").withColor(ErrorUtil.TIME_COLOR));
            return;
        }
        if (AbilityUtil.isTargetSignificantlyStronger(entity, target)
                || random.nextDouble() < AbilityUtil.getSequenceFailureChance(entity, target)) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.time_manipulation.resisted").withColor(ErrorUtil.TIME_COLOR));
            return;
        }

        int seconds = (int) (30 * multiplier(entity));
        TimeHandler.stealTime(target, seconds * 20);
        ConceptStorage.add(entity, ConceptStorage.Concept.TIME, seconds);
        FxHandler.playOnEntity(level, "time_steal", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));
        level.playSound(null, target.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.2f, 0.5f);
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.time_manipulation.time_stolen", target.getName(), seconds).withColor(ErrorUtil.TIME_COLOR));
    }

    private void stealDayNight(ServerLevel serverLevel, LivingEntity entity) {
        boolean isDay = serverLevel.isDay();

        long currentTime = serverLevel.getDayTime();
        long currentDayTime = currentTime % 24000;
        long targetTime = isDay ? 18000 : 6000;
        long ticksToAdd = currentDayTime <= targetTime ? targetTime - currentDayTime : (24000 - currentDayTime) + targetTime;
        serverLevel.setDayTime(currentTime + ticksToAdd);

        FxHandler.play(serverLevel, "time_clock_accelerate", clockPosition(entity), 1.6);

        if (entity instanceof Player player) {
            ItemStack stolen = new ItemStack(isDay ? ModItems.SUN_ITEM.get() : ModItems.MOON_ITEM.get());
            if (!player.getInventory().add(stolen)) player.drop(stolen, false);
        }
    }
}
