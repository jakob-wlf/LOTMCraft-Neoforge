package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.TheCorroderAbility;
import de.jakob.lotm.attachments.BlockExpiryData;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.OpenSpatialCageScreenPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class SpatialCageAbility extends SelectableAbility {

    private static final int SURROUND_TICKS = 20 * 60 * 10;
    private static final int OPEN_FRONT_TICKS = 20 * 20;
    private static final int RADIUS = 5;
    private static final int SEAL_INTERVAL = 20;
    private static final float REMOVE_COST_PER_SECOND = 10f;
    private static final String SEAL_CAUSE = "sealed_spatial_cage";

    private static final HashMap<UUID, HashSet<SpatialCage>> activeCages = new HashMap<>();

    public SpatialCageAbility(String id) {
        super(id, 35);
        isHoldable(60, 0, 1);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(10, 15, 25, 30));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(24000f, 12000f, 7500f, 5000f));
    }

    @Override
    public int getHoldTime(LivingEntity entity, int subIndex) {
        if (AbilityUtil.getSeqWithArt(entity, this) <= 0) return 0;
        return super.getHoldTime(entity, subIndex);
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
        place(entity, (ServerLevel) level, targetLoc, selectedAbility != 0, AbilityUtil.getSeqWithArt(entity, this));
    }

    public static void place(LivingEntity entity, ServerLevel level, Vec3 targetLoc, boolean openFront, int seq) {
        SpatialCage existing = cageAt(level, targetLoc, openFront);
        if (existing != null) {
            existing.extend(openFront ? OPEN_FRONT_TICKS : SURROUND_TICKS, maxTicks(seq));
            level.playSound(null, existing.center.x, existing.center.y, existing.center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, .6f);
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.spatial_cage.extended", existing.secondsLeft()).withColor(BeyonderData.pathwayInfos.get("door").color()));
            return;
        }

        createSpatialCage(entity, level, targetLoc, openFront, entity.getDirection().getOpposite(), seq);
    }

    private static void createSpatialCage(LivingEntity entity, ServerLevel serverLevel, Vec3 targetLoc, boolean openFront, Direction frontDirection, int seq) {
        Level level = serverLevel;
        Vec3 center = BlockPos.containing(targetLoc).getCenter();
        UUID effectId = openFront
                ? FxHandler.playCancellable(serverLevel, "spatial_cage_open", center, 1, FxHandler.yawTowards(Vec3.atLowerCornerOf(frontDirection.getNormal())))
                : FxHandler.playCancellable(serverLevel, "spatial_cage", center, 1);

        SpatialCage cage = new SpatialCage(serverLevel, targetLoc, openFront, RADIUS, frontDirection, entity.getUUID(),
                seq, center, effectId, openFront ? OPEN_FRONT_TICKS : SURROUND_TICKS);
        cage.createCage();
        activeCages.computeIfAbsent(entity.getUUID(), k -> new HashSet<>()).add(cage);

        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, .5f);
        ServerScheduler.scheduleDelayed(18, () -> level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, .5f, 1.6f), serverLevel);
    }

    private static int maxTicks(int seq) {
        int minutes = switch (seq) {
            case 0 -> 180;
            case 1 -> 120;
            case 2 -> 50;
            default -> 30;
        };
        return 20 * 60 * minutes;
    }

    private static SpatialCage cageAt(ServerLevel level, Vec3 pos, boolean openFront) {
        for (HashSet<SpatialCage> cages : activeCages.values()) {
            for (SpatialCage cage : cages) {
                if (cage.level == level && cage.isFrontOpen == openFront && cage.center.distanceTo(pos) <= cage.radius + 2.5) return cage;
            }
        }
        return null;
    }

    private static SpatialCage byId(UUID id) {
        for (HashSet<SpatialCage> cages : activeCages.values()) {
            for (SpatialCage cage : cages) {
                if (cage.effectId.equals(id)) return cage;
            }
        }
        return null;
    }

    private static boolean canDismantle(ServerPlayer player) {
        return "door".equals(BeyonderData.getPathway(player)) && BeyonderData.getSequence(player) <= 2;
    }

    private static int removeCost(ServerPlayer player, SpatialCage cage) {
        if (BeyonderData.getSequence(player) <= 0) return 0;
        return (int) Math.ceil(cage.secondsLeft() * REMOVE_COST_PER_SECOND);
    }

    public static void dismantle(ServerPlayer player, UUID id) {
        SpatialCage cage = byId(id);
        if (cage == null || cage.level != player.level() || !canDismantle(player)) return;
        if (player.position().distanceTo(cage.center) > cage.radius + 8) return;

        int cost = removeCost(player, cage);
        if (cost > 0) {
            if (BeyonderData.getSpirituality(player) < cost) {
                AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.spatial_cage.no_spirituality").withColor(BeyonderData.pathwayInfos.get("door").color()));
                return;
            }
            BeyonderData.reduceSpirituality(player, cost);
        }
        cage.removeCage();
        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.spatial_cage.removed").withColor(BeyonderData.pathwayInfos.get("door").color()));
    }

    private static boolean shielded(LivingEntity entity) {
        for (HashSet<SpatialCage> cages : activeCages.values()) {
            for (SpatialCage cage : cages) {
                if (!cage.isFrontOpen && cage.contains(entity)) return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (activeCages.isEmpty()) return;
        List<SpatialCage> all = new ArrayList<>();
        activeCages.values().forEach(all::addAll);
        for (SpatialCage cage : all) cage.tick();
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (activeCages.isEmpty() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (shielded(event.getEntity()) || (event.getSource().getEntity() instanceof LivingEntity attacker && shielded(attacker))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (activeCages.isEmpty() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !canDismantle(player)) return;
        for (HashSet<SpatialCage> cages : activeCages.values()) {
            for (SpatialCage cage : cages) {
                if (cage.level != player.level() || !cage.barrierBlocks.contains(event.getPos())) continue;
                event.setCanceled(true);
                PacketHandler.sendToPlayer(player, new OpenSpatialCageScreenPacket(cage.effectId, removeCost(player, cage), cage.secondsLeft()));
                return;
            }
        }
    }

    private static final class SpatialCage {
        final ServerLevel level;
        final Vec3 position;
        final boolean isFrontOpen;
        final int radius;
        final List<BlockPos> barrierBlocks = new ArrayList<>();
        final Direction frontDirection;
        final UUID owner;
        final int ownerSeq;
        final Vec3 center;
        final UUID effectId;
        double ticksLeft;
        int age;

        SpatialCage(ServerLevel level, Vec3 position, boolean isFrontOpen, int radius, Direction frontDirection, UUID owner, int ownerSeq, Vec3 center, UUID effectId, int ticks) {
            this.level = level;
            this.position = position;
            this.isFrontOpen = isFrontOpen;
            this.radius = radius;
            this.frontDirection = frontDirection;
            this.owner = owner;
            this.ownerSeq = ownerSeq;
            this.center = center;
            this.effectId = effectId;
            this.ticksLeft = ticks;
        }

        int secondsLeft() {
            return (int) Math.ceil(ticksLeft / 20.0);
        }

        boolean contains(LivingEntity entity) {
            return entity.level() == level && !entity.getUUID().equals(owner) && entity.position().distanceTo(position) <= radius;
        }

        void createCage() {
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
        }

        void extend(int ticks, int max) {
            ticksLeft = Math.max(ticksLeft, Math.min(ticksLeft + ticks, max));
            BlockExpiryData.get(level).scheduleAll(level, barrierBlocks, expiry());
        }

        long expiry() {
            return (long) ticksLeft + 100;
        }

        void tick() {
            LivingEntity livingOwner = level.getEntity(owner) instanceof LivingEntity living ? living : null;
            ticksLeft -= AbilityUtil.getTimeInArea(livingOwner, new Location(position, level));
            if (ticksLeft <= 0) {
                removeCage();
                return;
            }

            TheCorroderAbility.corrodeBlocks(level, barrierBlocks);
            updateCage();
            addSlownessToEntities();
            if (age++ % SEAL_INTERVAL == 0 && livingOwner != null) sealEntities(livingOwner);
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
                BlockExpiryData.get(level).schedule(level, pos, expiry());
            }
        }

        List<LivingEntity> inside() {
            List<LivingEntity> inside = new ArrayList<>();
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(position.x - radius, position.y - radius, position.z - radius, position.x + radius, position.y + radius, position.z + radius))) {
                if (contains(entity)) inside.add(entity);
            }
            return inside;
        }

        void addSlownessToEntities() {
            for (LivingEntity entity : inside()) {
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, this.isFrontOpen ? 3 : 9, false, false, false));
            }
        }

        void sealEntities(LivingEntity livingOwner) {
            for (LivingEntity entity : inside()) {
                if (BeyonderData.isBeyonder(entity) && BeyonderData.getSequence(entity) < ownerSeq) continue;
                AbilityUtil.sealAllTargetAbilities(livingOwner, entity, SEAL_CAUSE, SEAL_INTERVAL + 5);
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
