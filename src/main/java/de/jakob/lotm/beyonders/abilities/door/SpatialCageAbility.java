package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.BlockExpiryData;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class SpatialCageAbility extends SelectableAbility {

    private static final HashMap<UUID, HashSet<SpatialCage>> activeCages = new HashMap<>();

    public SpatialCageAbility(String id) {
        super(id, 35);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(10, 15, 25, 30));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(24000f, 12000f, 7500f, 5000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 1400;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.spatial_cage.surround",
                "ability.lotmcraft.spatial_cage.open_front"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if(level.isClientSide) return;

        Vec3 targetLoc = AbilityUtil.getTargetLocation(entity, baseDistance, 2);
        Direction frontDirection = entity.getDirection().getOpposite();
        createSpatialCage(entity, level, targetLoc, selectedAbility != 0, frontDirection);
    }

    private void createSpatialCage(LivingEntity entity, Level level, Vec3 targetLoc, boolean openFront, Direction frontDirection) {
        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 center = BlockPos.containing(targetLoc).getCenter();
        UUID effectId = openFront
                ? FxHandler.playCancellable(serverLevel, "spatial_cage_open", center, 1, FxHandler.yawTowards(Vec3.atLowerCornerOf(frontDirection.getNormal())))
                : FxHandler.playCancellable(serverLevel, "spatial_cage", center, 1);

        SpatialCage cage = new SpatialCage(serverLevel, targetLoc, openFront, 5, new ArrayList<>(), frontDirection, entity.getUUID(), center, effectId);
        cage.createCage();
        activeCages.computeIfAbsent(entity.getUUID(), k -> new HashSet<>()).add(cage);

        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, .5f);
        ServerScheduler.scheduleDelayed(18, () -> level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, .5f, 1.6f), serverLevel);

        ServerScheduler.scheduleForDuration(0, 1,  openFront ? 20 * 20 : 20 * 10, () -> {
            cage.updateCage();
            cage.addSlownessToEntities();
        }, cage::removeCage, serverLevel, () -> AbilityUtil.getTimeInArea(entity, new Location(targetLoc, level)));
    }

    private record SpatialCage(ServerLevel level, Vec3 position, boolean isFrontOpen, int radius, List<BlockPos> barrierBlocks, Direction frontDirection, UUID owner, Vec3 center, UUID effectId) {
        void createCage() {
            List<BlockPos> barrierBlocks = new ArrayList<>();

            BlockPos center = BlockPos.containing(position);
            var livingOwner = (LivingEntity) level.getEntity(owner);
            boolean griefing = false;

            if(livingOwner != null){
                griefing = BeyonderData.isGriefingEnabled(livingOwner);
            }

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        double distSq = x * x + y * y + z * z;
                        double outerRadius = radius;
                        double innerRadius = radius - 1;

                        if (distSq <= outerRadius * outerRadius && distSq > innerRadius * innerRadius) {
                            BlockPos blockPos = center.offset(x, y, z);

                            BlockState state = level.getBlockState(blockPos);

                            if (!griefing && !state.canBeReplaced()) {
                                continue;
                            }

                            if (isFrontOpen) {
                                Vec3 blockVec = new Vec3(x, y, z).normalize();
                                Vec3 frontVec = Vec3.atLowerCornerOf(frontDirection.getNormal());
                                double dot = blockVec.dot(frontVec);

                                if (dot > 0.5) {
                                    continue;
                                }
                            }

                            barrierBlocks.add(blockPos);}
                    }
                }
            }

            this.barrierBlocks.addAll(barrierBlocks);
            BlockExpiryData.get(level).scheduleAll(level, barrierBlocks, 20 * 60);
        }

        void removeCage() {
            if (!activeCages.getOrDefault(owner, new HashSet<>()).remove(this)) {
                return;
            }

            for (BlockPos pos : barrierBlocks) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }

            FxHandler.cancel(level, effectId);
            if (isFrontOpen) {
                FxHandler.play(level, "spatial_cage_open_collapse", center, 1, FxHandler.yawTowards(Vec3.atLowerCornerOf(frontDirection.getNormal())));
            } else {
                FxHandler.play(level, "spatial_cage_collapse", center, 1);
            }
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 2f, .7f);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, .6f);
        }

        void updateCage() {
            for (BlockPos pos : barrierBlocks) {
                if(!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    continue;
                }
                level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
                BlockExpiryData.get(level).schedule(level, pos, 20 * 60);
            }
        }

        void addSlownessToEntities() {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(position.x - radius, position.y - radius, position.z - radius, position.x + radius, position.y + radius, position.z + radius))) {
                if (entity.position().distanceTo(position) <= radius && !entity.getUUID().equals(owner)) {
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, this.isFrontOpen ? 3 : 9, false, false, false));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        if (activeCages.containsKey(playerUUID)) {
            for (SpatialCage cage : new ArrayList<>(activeCages.get(playerUUID))) {
                if(cage != null)
                    cage.removeCage();
            }
            activeCages.remove(playerUUID);
        }
    }
}
