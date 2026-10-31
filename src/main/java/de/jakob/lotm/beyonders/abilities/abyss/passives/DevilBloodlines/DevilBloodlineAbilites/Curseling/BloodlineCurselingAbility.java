package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.Curseling;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbility;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.particle.ModParticles;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class BloodlineCurselingAbility extends Ability {


    // Foulness damage and duration multiplier for Curselings
    public static final float FOULNESS_MULTIPLIER = 1.5F;
    // Weaker than the Demoness Curse
    private static final int CURSE_BASE_TICKS = 20 * 8;   // Demoness: 20 * 10
    private static final int CURSE_INTERVAL_TICKS = 12;   // Demoness: 8
    private static final float SPIRIT_DRAIN = 250f;    // flat spirituality removed
    private static final float SANITY_DRAIN = 0.03f;
    public BloodlineCurselingAbility(String id) {
        super(id, 120f, "curse"); // Demoness Curse cooldown is 80
        canBeUsedByNPC = false;
        autoClear = false;
    }
    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("abyss", 6));
    }
    @Override
    public float getSpiritualityCost() {
        return 300;
    }
    @Override
    public boolean meetsBloodlineRequirement(LivingEntity entity) {
        return hasCurselingBloodline(entity);
    }
    private static boolean hasCurselingBloodline(LivingEntity entity) {
        return DevilBloodlineAbility.hasBloodline(entity, DevilBloodlineAbility.Bloodline.CURSELING);
    }
    public static boolean isValidCurseling(LivingEntity entity) {
        return hasCurselingBloodline(entity);
    }
    public static float getFoulnessMultiplier(LivingEntity entity) {
        return isValidCurseling(entity) ? FOULNESS_MULTIPLIER : 1.0F;
    }
    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) return;
        ItemStack offHandItem = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHandItem.is(ModItems.BLOOD)) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.curse.no_blood").withColor(0x6d32a8));
            return;
        }
        UUID targetUUID = null;
        try {
            String ownerStr = offHandItem.getOrDefault(ModDataComponents.BLOOD_OWNER, "");
            if (!ownerStr.isEmpty()) targetUUID = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException ignored) {}
        LivingEntity found = null;
        if (targetUUID != null) for (ServerLevel l : serverLevel.getServer().getAllLevels()) if (l.getEntity(targetUUID) instanceof LivingEntity le) { found = le; break; }
        if (found == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.curse.no_target").withColor(0x6d32a8));
            offHandItem.consume(1, player);
            return;
        }
        final LivingEntity cursed = found;
        int entitySeq = AbilityUtil.getSeqWithArt(entity, this);
        int targetSeq = BeyonderData.isBeyonder(cursed) ? BeyonderData.getSequence(cursed) : 10;
        if (AbilityUtil.isTargetSignificantlyStronger(entitySeq, targetSeq)) {
            entity.addEffect(new MobEffectInstance(ModEffects.LOOSING_CONTROL, 20 * 5, 3));
            entity.hurt(ModDamageTypes.source(entity.level(), ModDamageTypes.CURSE), 10);
            return;
        }
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.curse.cursed_target").withColor(0x6d32a8));
        offHandItem.consume(1, player);
        int curseDuration = CURSE_BASE_TICKS * (int) Math.max(multiplier(entity), 1);
        AtomicReference<UUID> taskIdRef = new AtomicReference<>();
        AtomicBoolean stopped = new AtomicBoolean(false);
        Runnable stop = () -> { stopped.set(true); UUID id = taskIdRef.get(); if (id != null) ServerScheduler.cancel(id); };
        UUID taskId = ServerScheduler.scheduleForDuration(0, CURSE_INTERVAL_TICKS, curseDuration, () -> {
            if (stopped.get()) return;
            if (cursed.isDeadOrDying() || InteractionHandler.isInteractionPossibleForEntity(new Location(cursed.position(), cursed.level()), "cleansing", entitySeq, entity)) {
                stop.run();
                return;
            }
            if (BeyonderData.isBeyonder(cursed)) BeyonderData.setSpirituality(cursed, Math.max(0f, BeyonderData.getSpirituality(cursed) - SPIRIT_DRAIN));
            var sanity = cursed.getData(ModAttachments.SANITY_COMPONENT.get());
            sanity.setSanityAndSync(Math.max(0f, sanity.getSanity() - SANITY_DRAIN), cursed);
            if (cursed.level() instanceof ServerLevel cl) {ParticleUtil.spawnParticles(cl, ModParticles.BLACK_FLAME.get(), cursed.position().add(0, cursed.getEyeHeight() / 2, 0), 60, .4, cursed.getEyeHeight() / 2, .4, 0.01);}
        }, () -> clearArtifactScaling(entity), serverLevel, () -> AbilityUtil.getTimeInArea(entity, new Location(entity.position(), serverLevel)));
        taskIdRef.set(taskId);
    }
}