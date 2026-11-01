package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class EloquenceAbility extends ToggleAbility {

    private static final Map<UUID, Long> typingUntil = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    public EloquenceAbility(String id) {
        super(id);
        canBeUsedByNPC = false;
        canBeShared = false;
        tickRate = 2;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 8));
    }

    @Override
    protected float getSpiritualityCost() {
        return 0.6f;
    }

    public static boolean isDeceivingWords(LivingEntity entity) {
        return BeyonderData.getSequence(entity) <= 3;
    }

    @Override
    public Component getNameFormatted(LivingEntity entity) {
        if (isDeceivingWords(entity)) {
            return Component.translatable("lotmcraft.deceiving_words_ability").withStyle(ChatFormatting.BOLD).withColor(getColorForPathway("error"));
        }
        return super.getNameFormatted(entity);
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.eloquenceActive = true;
            return;
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.3f, 1.3f);
        AbilityUtil.sendActionBar(entity, Component.translatable(isDeceivingWords(entity)
                ? "ability.lotmcraft.deceiving_words.on" : "ability.lotmcraft.eloquence.on").withColor(ErrorUtil.DECEIT_COLOR));
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.eloquenceActive = false;
            return;
        }
        typingUntil.remove(entity.getUUID());
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !isDeceivingWords(entity)) return;
        if (typingUntil.getOrDefault(entity.getUUID(), 0L) < serverLevel.getGameTime()) return;

        for (LivingEntity target : getListeners(serverLevel, entity, 24)) {
            Vec3 eyes = entity.getEyePosition();
            if (target instanceof ServerPlayer targetPlayer) {
                targetPlayer.lookAt(EntityAnchorArgument.Anchor.EYES, eyes);
            } else if (target instanceof Mob mob) {
                mob.getLookControl().setLookAt(entity, 180, 180);
                mob.lookAt(entity, 180, 180);
                mob.getNavigation().stop();
            }

            if (serverLevel.getGameTime() % 20 == 0 && RANDOM.nextFloat() < 0.05f) {
                persuade(serverLevel, entity, target, true);
            }
        }
    }

    private static List<LivingEntity> getListeners(ServerLevel level, LivingEntity speaker, double range) {
        Vec3 look = speaker.getLookAngle().multiply(1, 0, 1).normalize();
        return level.getEntitiesOfClass(LivingEntity.class, speaker.getBoundingBox().inflate(range), e -> {
            if (e == speaker || !e.isAlive() || e instanceof ArmorStand) return false;
            if (e.distanceToSqr(speaker) > range * range) return false;
            Vec3 to = e.position().subtract(speaker.position()).multiply(1, 0, 1);
            return to.lengthSqr() < 0.01 || to.normalize().dot(look) >= 0;
        });
    }

    private static float getPersuasionChance(LivingEntity speaker, LivingEntity target) {
        int speakerSeq = BeyonderData.getSequence(speaker);
        if (!BeyonderData.isBeyonder(target)) return 0.3f;

        int targetSeq = BeyonderData.getSequence(target);
        if (AbilityUtil.isTargetSignificantlyStronger(speakerSeq, targetSeq)) return 0f;
        if ("error".equals(BeyonderData.getPathway(target)) && targetSeq <= speakerSeq) return 0f;

        return Math.max(0.03f, Math.min(0.6f, 0.2f + 0.06f * (targetSeq - speakerSeq)));
    }

    private static void persuade(ServerLevel level, LivingEntity speaker, LivingEntity target, boolean silent) {
        if (AllyUtil.isAlly(target, speaker.getUUID())) return;
        if (RANDOM.nextFloat() >= getPersuasionChance(speaker, target)) return;

        AllyUtil.addAllyOneWay(target, speaker);
        if (target instanceof Mob mob && mob.getTarget() == speaker) {
            mob.setTarget(null);
        }

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getEyeY() + 0.3, target.getZ(), 6, 0.3, 0.2, 0.3, 0);
        if (!silent && speaker instanceof ServerPlayer player) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.eloquence.persuaded", target.getName()).withColor(ErrorUtil.DECEIT_COLOR));
        }

        int duration = isDeceivingWords(speaker) ? 20 * 60 * 6 : 20 * 60 * 3;
        UUID speakerId = speaker.getUUID();
        ServerScheduler.scheduleDelayed(duration, () -> AllyUtil.removeAllyOneWay(target, speakerId));
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer speaker = event.getPlayer();
        EloquenceAbility ability = (EloquenceAbility) LOTMCraft.abilityHandler.getById("eloquence_ability");
        if (ability == null || !ability.isActiveForEntity(speaker)) return;

        typingUntil.remove(speaker.getUUID());
        ServerLevel level = speaker.serverLevel();
        double range = isDeceivingWords(speaker) ? 24 : 12;
        for (LivingEntity target : getListeners(level, speaker, range)) {
            persuade(level, speaker, target, false);
        }
    }

    public static void setTyping(ServerPlayer player, boolean typing) {
        if (typing) {
            typingUntil.put(player.getUUID(), player.serverLevel().getGameTime() + 25);
        } else {
            typingUntil.remove(player.getUUID());
        }
    }
}
