package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.entity.custom.ability_entities.TimeChangeEntity;
import de.jakob.lotm.entity.custom.ability_entities.door_pathway.BlinkAfterimageEntity;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.PlayPhotonBlockEffectPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SpaceFragmentationAbility extends SelectableAbility {

    private static final int COPIES = 9;
    private static final int SHATTER_RADIUS = 15;
    private static final int SHATTER_TICKS = 20 * 10;

    public SpaceFragmentationAbility(String id) {
        super(id, 20);

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(22500f, 12000f));

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(5, 15));

        baseDamage = 6f;

        isHoldable(40,0);
        isHoldable(80,1);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 1));
    }

    @Override
    protected float getSpiritualityCost() {
        return 12000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.space_fragmentation.shatter_space",
                "ability.lotmcraft.space_fragmentation.starfall"
        };
    }

    @Override
    public String[] getSequenceDependantAbilityNames(int sequence) {
        if (sequence <= 0) return getAllAbilityNames();
        return new String[]{"ability.lotmcraft.space_fragmentation.shatter_space"};
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (selectedAbility == 0) shatterSpace(serverLevel, entity, entity.position());
        else                      starfall(serverLevel, entity);
    }
// using this in position authority
    public void shatterSpace(ServerLevel level, LivingEntity entity, Vec3 center) {
        boolean griefing = BeyonderData.isGriefingEnabled(entity);

        List<BlinkAfterimageEntity> copies = new ArrayList<>();
        for (int i = 0; i < COPIES; i++) {
            double angle = Math.PI * 2 * i / COPIES;
            Vec3 pos = center.add(Math.cos(angle) * SHATTER_RADIUS, 1.25, Math.sin(angle) * SHATTER_RADIUS);
            BlinkAfterimageEntity copy = new BlinkAfterimageEntity(level, pos, entity, SHATTER_TICKS);
            float yaw = (float) Math.toDegrees(Math.atan2(center.z - pos.z, center.x - pos.x)) - 90f;
            copy.setYRot(yaw);
            copy.setYHeadRot(yaw);
            copy.setYBodyRot(yaw);
            copy.setNoGravity(true);
            level.addFreshEntity(copy);
            copies.add(copy);
        }

        TimeChangeEntity timeStop = new TimeChangeEntity(ModEntities.TIME_CHANGE.get(), level, SHATTER_TICKS, entity.getUUID(), SHATTER_RADIUS, 0.001f);
        timeStop.setPos(center);
        level.addFreshEntity(timeStop);

        UUID tear = FxHandler.playCancellable(level, "shatter_space", center, 1);
        Runnable sealSpace = () -> {
            FxHandler.cancel(level, tear);
            FxHandler.play(level, "shatter_space_end", center, 1);
            level.playSound(null, BlockPos.containing(center), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.AMBIENT, 2f, 0.5f);
        };
        level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.5f, 0.6f);

        float damage = baseDamage * 2.5f;
        BlockPos below = BlockPos.containing(entity.position().subtract(0, 1, 0));
        AtomicInteger tick = new AtomicInteger();
        UUID[] task = new UUID[1];
        task[0] = ServerScheduler.scheduleForDuration(0, 1, SHATTER_TICKS, () -> {
            int t = tick.incrementAndGet();

            if (copies.stream().filter(LivingEntity::isAlive).count() < (COPIES + 1) / 2) {
                copies.forEach(LivingEntity::discard);
                timeStop.discard();
                sealSpace.run();
                ServerScheduler.cancel(task[0]);
                return;
            }

            if (t % 10 == 0) {
                AbilityUtil.damageNearbyEntities(level, entity, SHATTER_RADIUS, ModDamageTypes.SPACE_DESTRUCTION, damage, center, true, false);
                level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_SHOOT, SoundSource.AMBIENT, 1.2f, 0.6f + random.nextFloat() * 0.4f);
            }

            AABB ring = new AABB(center, center).inflate(SHATTER_RADIUS + 10);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, ring)) {
                if (living == entity || copies.contains(living) || !AbilityUtil.mayTarget(entity, living)) continue;
                Vec3 toCenter = center.subtract(living.position());
                double dist = toCenter.length();
                if (dist <= SHATTER_RADIUS || dist > SHATTER_RADIUS + 10) continue;
                living.setDeltaMovement(living.getDeltaMovement().add(toCenter.normalize().scale(0.12)));
                living.hurtMarked = true;
            }

            if (griefing && t % 10 == 0) {
                AbilityUtil.getBlocksInSphereRadius(level, center, SHATTER_RADIUS - 1, true, true, false).forEach(pos -> {
                    if (random.nextInt(30) == 0
                            && !pos.equals(below)
                            && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                });
            }
        }, sealSpace, level);
    }

    private void starfall(ServerLevel level, LivingEntity entity) {
        Vec3 targetLoc =  AbilityUtil.getTargetLocation(entity, (int) (35*multiplier(entity)), 2);

        BlockPos blockPos = BlockPos.containing(targetLoc);

        double offsetX = targetLoc.x - (blockPos.getX() + 0.5);
        double offsetY = targetLoc.y - (blockPos.getY() + 0.5) + .25;
        double offsetZ = targetLoc.z - (blockPos.getZ() + 0.5);

        PacketHandler.sendToNearbyPlayers(
                new PlayPhotonBlockEffectPacket("space_fragmentation", blockPos, offsetX, offsetY, offsetZ, 1, null, -1, false, true, null),
                level, targetLoc, 128
        );

        final int BLOW_TICK = 160;
        final int GRIEF_START = 60;
        final int PHASE2_END = 100;

        float blowDamage = baseDamage * 10;
        float tickDamage = baseDamage;

        AtomicInteger tick = new AtomicInteger(0);
        ServerScheduler.scheduleForDuration(0, 1, BLOW_TICK + 1, () -> {
            int t = tick.get();
            float progress = Math.min(t / (float) BLOW_TICK, 1f);

            if (t == BLOW_TICK) {
                AbilityUtil.damageNearbyEntities(level, entity, 40,
                        blowDamage, targetLoc, true, true);

                if (BeyonderData.isGriefingEnabled(entity)) {
                    AbilityUtil.getBlocksInSphereRadius(level, targetLoc, 37, true, true, false).forEach(pos -> {
                        if (level.getBlockState(pos).getDestroySpeed(level, pos) >= 0
                                && !pos.getCenter().equals(BlockPos.containing(entity.position().subtract(0, 1, 0)).getCenter())
                                && level.random.nextFloat() < 0.75f) {
                            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        }
                    });
                    AbilityUtil.getBlocksInSphereRadius(level, targetLoc, 32, true, true, false).forEach(pos -> {
                        if (level.getBlockState(pos).getDestroySpeed(level, pos) >= 0
                                && !pos.getCenter().equals(BlockPos.containing(entity.position().subtract(0, 1, 0)).getCenter())) {
                            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        }
                    });
                }
                tick.getAndIncrement();
                return;
            }

            if (t % 20 == 0) {
                double dmgRadius = 8 + progress * 22;
                AbilityUtil.damageNearbyEntities(level, entity, dmgRadius,
                        tickDamage, targetLoc, true, true);
            }

            AABB box = new AABB(targetLoc, targetLoc).inflate(40);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (living == entity) continue;
                Vec3 toCenter = targetLoc.subtract(living.position());
                double dist = toCenter.length();
                if (dist < 1.5 || dist > 40) continue;

                double strength = 0.10 + 0.05 * progress;       // moderate, slightly stronger over time
                Vec3 motion = living.getDeltaMovement().add(toCenter.normalize().scale(strength));
                if (motion.length() > 0.7) motion = motion.normalize().scale(0.7); // cap speed
                living.setDeltaMovement(motion);
                living.hurtMarked = true;
            }

            if (t >= GRIEF_START && BeyonderData.isGriefingEnabled(entity)) {
                float radius;
                if (t < PHASE2_END) {
                    radius = 3 + 3 * (t - GRIEF_START) / (float) (PHASE2_END - GRIEF_START);
                } else {
                    radius = 7 + 2 * (t - PHASE2_END) / (float) (BLOW_TICK - PHASE2_END);
                }

                AbilityUtil.getBlocksInSphereRadius(level, targetLoc, radius, true, true, false).forEach(pos -> {
                    if (level.random.nextFloat() < 0.3f
                            && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0
                            && !pos.getCenter().equals(BlockPos.containing(entity.position().subtract(0, 1, 0)).getCenter())) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                });
            }

            tick.getAndIncrement();
        }, null, level);
    }
}
