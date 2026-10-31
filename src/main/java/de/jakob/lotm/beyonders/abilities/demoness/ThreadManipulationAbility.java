package de.jakob.lotm.beyonders.abilities.demoness;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.ability_entities.TendrilEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.helper.VectorUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ThreadManipulationAbility extends SelectableAbility {

    private static final HashSet<UUID> boundEntities = new HashSet<>();
    private static final int THREAD_FADE = 8;
    private static final float THREAD_WIDTH = .018f;
    private static final int COCOON_FADE = 10;
    private static final HashSet<UUID> inCocoon = new HashSet<>();

    public ThreadManipulationAbility(String id) {
        super(id, 1.5f);
        autoClear = false;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(1, 1, 1, 2, 2, 3, 3));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(2500f, 1000f, 750f, 360f, 280f, 200f, 150f));

        baseDamage = 7f;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("demoness", 6));
    }

    @Override
    protected float getSpiritualityCost() {
        return 45;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.threads.binding",
                "ability.lotmcraft.threads.cocoon",
                "ability.lotmcraft.threads.shoot"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if(level.isClientSide)
            return;

        switch (abilityIndex) {
            case 0 -> binding((ServerLevel) level, entity);
            case 1 -> cocoon((ServerLevel) level, entity);
            case 2 -> shoot((ServerLevel) level, entity);
        }
    }

    private void cocoon(ServerLevel level, LivingEntity entity) {
        if(inCocoon.contains(entity.getUUID())) {
            inCocoon.remove(entity.getUUID());
            return;
        }

        inCocoon.add(entity.getUUID());

        int duration = 20 * 5;
        Location loc = new Location(entity.position(), level);

        // strands are spun from all around and woven into a shell, half of them winding up and half down
        List<TendrilEntity> strands = new ArrayList<>();
        float shellRadius = Math.max(.7f, entity.getBbWidth() * .5f + .45f);
        Vec3 center = entity.position().add(0, entity.getBbHeight() * .5, 0);
        int strandCount = 12;
        for(int i = 0; i < strandCount; i++) {
            double yaw = i * Math.PI * 2 / strandCount + random.nextDouble(-.25, .25);
            double pitch = random.nextDouble(-.5, .9);
            double distance = random.nextDouble(2.2, 3.4);
            Vec3 anchor = center.add(Math.cos(yaw) * Math.cos(pitch) * distance, Math.sin(pitch) * distance, Math.sin(yaw) * Math.cos(pitch) * distance);
            boolean upwards = i % 2 == 0;
            strands.add(TendrilEntity.spawn(level, TendrilEntity.Kind.THREAD, anchor, entity, anchor, random.nextInt(8, 18),
                            duration + COCOON_FADE, COCOON_FADE, (float) random.nextDouble(2.5, 3.5), .02f)
                    .shell(shellRadius, upwards ? .06f : .94f, upwards ? .94f : .06f));
        }
        FxHandler.playOnBody(level, "thread_cocoon_weave", entity);
        UUID cocoonFx = FxHandler.playOnBody(level, "thread_cocoon", entity);
        level.playSound(null, BlockPos.containing(center), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.2f, .7f);

        AtomicBoolean opened = new AtomicBoolean(false);
        Runnable open = () -> {
            if(opened.getAndSet(true))
                return;
            strands.forEach(TendrilEntity::release);
            FxHandler.cancelOnEntity(level, cocoonFx);
            FxHandler.playOnBody(level, "thread_cocoon_burst", entity);
            level.playSound(null, entity.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.2f, .8f);
        };

        AtomicReference<UUID> taskIdRef = new AtomicReference<>();
        UUID taskId = ServerScheduler.scheduleForDuration(0, 5, duration, () -> {
            if(InteractionHandler.isInteractionPossible(loc, "burning")) {
                inCocoon.remove(entity.getUUID());
                ServerScheduler.cancel(taskIdRef.get());

                entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                entity.removeEffect(MobEffects.REGENERATION);

                Vec3 pos = entity.position().add(0, entity.getBbHeight() * .5, 0);
                FxHandler.play(level, "bindings_burn", pos, Math.max(1, entity.getBbHeight() / 1.8));
                level.playSound(null, BlockPos.containing(pos),
                        SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.2f);
            }

            if(!inCocoon.contains(entity.getUUID())) {
                open.run();
                return;
            }

            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 2, false, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 2, 2, false, false, false));
            entity.setDeltaMovement(new Vec3(0, 0, 0));
            entity.hurtMarked = true;

            loc.setPosition(entity.position());
            loc.setLevel(entity.level());
        }, () -> {
            inCocoon.remove(entity.getUUID());
            open.run();
        }, level);
        taskIdRef.set(taskId);
        clearArtifactScaling(entity);
    }

    private void shoot(ServerLevel level, LivingEntity entity) {
        Vec3 startPos = VectorUtil.getRelativePosition(entity.getEyePosition().add(entity.getLookAngle().normalize()), entity.getLookAngle().normalize(), 0, random.nextDouble(-.65, .65), random.nextDouble(-.1, .6));
        Vec3 direction = AbilityUtil.getTargetLocation(entity, baseDistance, 1.4f).subtract(startPos).normalize();

        AtomicReference<Vec3> currentPos = new AtomicReference<>(startPos);

        AtomicBoolean hasHit = new AtomicBoolean(false);

        // the thread stays tied to where it was spun while its tip flies on
        TendrilEntity thread = TendrilEntity.spawn(level, TendrilEntity.Kind.THREAD, startPos, null, startPos, 1, 20 * 20 + THREAD_FADE, THREAD_FADE, 0, .026f);

        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.SNOWBALL_THROW, entity.getSoundSource(), 1.0f, 1.0f);

        ServerScheduler.scheduleForDuration(0, 1, 20 * 20, () -> {
            if(hasHit.get())
                return;

            Vec3 pos = currentPos.get();

            if(AbilityUtil.damageNearbyEntities(level, entity, 2.5f, ModDamageTypes.THREAD, baseDamage, pos, true, false, true,0)) {
                hasHit.set(true);
                AbilityUtil.addPotionEffectToNearbyEntities(level, entity, 2.5, pos, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 8, 4, false, false, false));
                thread.moveEnd(pos);
                thread.release();
                FxHandler.play(level, "thread_shot_hit", pos, 1);
                return;
            }

            if(!level.getBlockState(BlockPos.containing(pos.x, pos.y, pos.z)).isAir()) {
                Vec3 surface = pos.subtract(direction.scale(.5));
                if(BeyonderData.isGriefingEnabled(entity)) {
                    pos = pos.subtract(direction);
                    level.setBlockAndUpdate(BlockPos.containing(pos.x, pos.y, pos.z), Blocks.FIRE.defaultBlockState());
                }
                hasHit.set(true);
                thread.moveEnd(surface);
                thread.release();
                FxHandler.play(level, "thread_shot_hit", surface, 1);
                return;
            }

            thread.moveEnd(pos);
            currentPos.set(pos.add(direction));
        }, () -> {
            thread.release();
            clearArtifactScaling(entity);
        }, level);
    }

    private void binding(ServerLevel level, LivingEntity entity) {
        // the threads are spun from points in the air around the caster
        Vec3 targetLoc = AbilityUtil.getTargetLocation(entity, 16, 1.4f);
        List<Vec3> anchors = new ArrayList<>();
        for(int i = 0; i < 11; i++) {
            anchors.add(VectorUtil.getRelativePosition(entity.getEyePosition().add(entity.getLookAngle().normalize()), entity.getLookAngle().normalize(),  random.nextDouble(-4.5, 3f), random.nextDouble(-7, 7), random.nextDouble(-2, 5)));
        }

        level.playSound(null, BlockPos.containing(entity.position()), SoundEvents.SNOWBALL_THROW, SoundSource.BLOCKS, 1, 1);
        LivingEntity targetEntity = AbilityUtil.getTargetEntity(entity, 20, 2);
        boolean alreadyBound = targetEntity != null && boundEntities.contains(targetEntity.getUUID());
        if(targetEntity == null || alreadyBound) {
            if(alreadyBound && entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("Entity is already bound by your threads.").withColor(0xFFaa42f5));
                player.connection.send(packet);
            }
            for(Vec3 anchor : anchors) {
                int reach = threadReach(anchor, targetLoc);
                TendrilEntity.spawn(level, TendrilEntity.Kind.THREAD, anchor, null, targetLoc, reach, reach + 25, THREAD_FADE, 0, THREAD_WIDTH);
            }
            return;
        }

        boundEntities.add(targetEntity.getUUID());
        float multiplier_target = multiplier(targetEntity);
        int duration = 20 * 5;

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this);
        int targetEntitySeq = BeyonderData.getSequence(targetEntity);

        if(AbilityUtil.isTargetSignificantlyStronger(entitySeq, targetEntitySeq)) {
            duration = 20;
        }
        if(AbilityUtil.isTargetSignificantlyWeaker(entitySeq, targetEntitySeq)) {
            duration = 20 * 15;
        }

        if(!BeyonderData.isBeyonder(targetEntity) || BeyonderData.getSequence(targetEntity) - 1 > AbilityUtil.getSeqWithArt(entity, this)) {
            if(targetEntity instanceof Mob) {
                ((Mob) targetEntity).setNoAi(true);
                ServerScheduler.scheduleDelayed(duration, () -> ((Mob) targetEntity).setNoAi(false), level, () -> AbilityUtil.getTimeInArea(entity, new Location(entity.position(), level)));
            }
        }

        Location loc = new Location(targetEntity.position(), targetEntity.level());

        int finalDuration = duration;
        List<TendrilEntity> threads = new ArrayList<>();
        for(Vec3 anchor : anchors) {
            threads.add(TendrilEntity.spawn(level, TendrilEntity.Kind.THREAD, anchor, targetEntity, targetLoc, threadReach(anchor, targetEntity.position()),
                    finalDuration + THREAD_FADE, THREAD_FADE, (float) random.nextDouble(1.5, 3), THREAD_WIDTH));
        }
        AtomicReference<UUID> bindingFx = new AtomicReference<>();
        AtomicBoolean released = new AtomicBoolean(false);
        Runnable release = () -> {
            released.set(true);
            FxHandler.cancelOnEntity(level, bindingFx.get());
            threads.forEach(TendrilEntity::release);
        };
        FxHandler.playOnBody(level, "thread_binding_snap", targetEntity);
        ServerScheduler.scheduleDelayed(20, () -> {
            if(!released.get())
                bindingFx.set(FxHandler.playOnBody(level, "thread_binding", targetEntity));
        });

        AtomicReference<UUID> taskIdRef = new AtomicReference<>();
        UUID taskId = ServerScheduler.scheduleForDuration(0, 5, finalDuration, () -> {
            // Burn Binding
            if(InteractionHandler.isInteractionPossible(loc, "burning")) {
                ServerScheduler.cancel(taskIdRef.get());

                targetEntity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                targetEntity.removeEffect(MobEffects.WEAKNESS);
                targetEntity.removeEffect(MobEffects.DIG_SLOWDOWN);
                if (targetEntity instanceof Mob mob) mob.setNoAi(false);

                Vec3 pos = targetEntity.position().add(0, targetEntity.getBbHeight() * .5, 0);
                FxHandler.play(level, "bindings_burn", pos, Math.max(1, targetEntity.getBbHeight() / 1.8));
                level.playSound(null, BlockPos.containing(pos),
                        SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.2f);

                release.run();
                boundEntities.remove(targetEntity.getUUID());
                return;
            }

            if(InteractionHandler.isInteractionPossibleForEntity(loc, "escape", AbilityUtil.getSeqWithArt(entity, this), targetEntity)) {
                ServerScheduler.cancel(taskIdRef.get());

                targetEntity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                targetEntity.removeEffect(MobEffects.WEAKNESS);
                targetEntity.removeEffect(MobEffects.DIG_SLOWDOWN);
                if (targetEntity instanceof Mob mob) mob.setNoAi(false);

                release.run();
                boundEntities.remove(targetEntity.getUUID());
                return;
            }

            targetEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 10, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 10, false, false, false));
            targetEntity.setDeltaMovement(new Vec3(0, 0, 0));
            targetEntity.hurtMarked = true;

            loc.setLevel(targetEntity.level());
            loc.setPosition(targetEntity.position());
        }, () -> {
            release.run();
            clearArtifactScaling(entity);
        }, level, () -> AbilityUtil.getTimeInArea(entity, new Location(entity.position(), level)));
        taskIdRef.set(taskId);

        ServerScheduler.scheduleDelayed(duration, () -> boundEntities.remove(targetEntity.getUUID()));
    }

    /** Ticks a thread takes to shoot from its anchor to the target. */
    private static int threadReach(Vec3 from, Vec3 to) {
        return Mth.clamp((int) (from.distanceTo(to) / 1.6), 4, 14);
    }
}
