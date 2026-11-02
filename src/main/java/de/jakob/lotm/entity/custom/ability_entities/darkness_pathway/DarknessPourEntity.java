package de.jakob.lotm.entity.custom.ability_entities.darkness_pathway;

import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.CameraShakePacket;
import de.jakob.lotm.network.packets.toClient.DarknessEffectPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class DarknessPourEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DarknessPourEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DESTINATION = SynchedEntityData.defineId(DarknessPourEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int BAND = 8;

    private static final List<DarknessPourEntity> active = new ArrayList<>();

    private final Map<UUID, Integer> stage = new HashMap<>();
    private final Set<UUID> sealed = new HashSet<>();
    private final Set<UUID> loose = new HashSet<>();
    private final Map<UUID, Vec3> anchor = new HashMap<>();
    private final Set<UUID> physics = new HashSet<>();
    private final Set<Long> darkened = new HashSet<>();
    private final List<BlockPos> darkenedBlocks = new ArrayList<>();
    private float shown;
    private int age;
    private boolean restored;
    private boolean listed;

    public DarknessPourEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public DarknessPourEntity(Level level, double x, double y, double z, LivingEntity owner, boolean destination) {
        this(ModEntities.DARKNESS_POUR.get(), level);
        setPos(x, y, z);
        entityData.set(OWNER, Optional.ofNullable(owner == null ? null : owner.getUUID()));
        entityData.set(DESTINATION, destination);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (!listed) {
            active.add(this);
            listed = true;
        }
        age++;
        boolean destination = entityData.get(DESTINATION);
        int life = destination ? 20 * 18 + 1 : 20 * 12 + 1;
        if (age > life) {
            discard();
            return;
        }
        float max = destination ? 50f : 100f;
        float radius = max * Math.min(1f, age / (destination ? 320f : 160f));
        spread(serverLevel, radius);
        LivingEntity owner = owner(serverLevel);
        AABB box = new AABB(getX() - radius, getY() - radius, getZ() - radius, getX() + radius, getY() + radius, getZ() + radius);
        Set<UUID> sinking = new HashSet<>();
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box, living -> living.isAlive() && living.distanceToSqr(this) <= radius * radius)) {
            if (owner != null && target.getUUID().equals(owner.getUUID())) continue;
            if (!destination && owner != null && AllyUtil.areAllies(owner, target)) continue;
            int seq = BeyonderData.getSequence(target);
            String pathway = BeyonderData.getPathway(target);
            if (seq == 0 && ("death".equals(pathway) || "mother".equals(pathway))) continue;
            if (destination ? seq != 0 : seq >= 3) {
                shake(target, 1f);
                target.kill();
                continue;
            }
            int delay = destination ? 20 * 2 : seq == 0 ? 20 * 4 : seq == 1 ? 20 * 3 : 20 * 2;
            int step = stage.getOrDefault(target.getUUID(), 0);
            if (step == 0) {
                if (target instanceof Mob mob) mob.setNoAi(true);
                step = age;
                stage.put(target.getUUID(), age);
            }
            int elapsed = age - step;
            float progress = Math.min(1f, elapsed / (float) delay);
            shake(target, progress);
            if (!loose.contains(target.getUUID())) {
                sink(target, progress);
                sinking.add(target.getUUID());
            }
            if (!sealed.contains(target.getUUID()) && delay - elapsed <= 40) {
                AbilityUtil.sealAllTargetAbilities(owner == null ? target : owner, target, "dissolve", 40);
                sealed.add(target.getUUID());
            }
            if (elapsed >= delay) {
                target.kill();
                stage.put(target.getUUID(), age + life);
            } else if (elapsed >= 40) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2, false, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 2, false, false, false));
            }
        }
        physics.removeIf(id -> {
            if (sinking.contains(id)) return false;
            anchor.remove(id);
            if (serverLevel.getEntity(id) instanceof LivingEntity living) living.noPhysics = false;
            return true;
        });
    }

    public static void releasePull(LivingEntity target) {
        UUID id = target.getUUID();
        for (DarknessPourEntity pour : active) {
            if (!pour.loose.add(id)) continue;
            pour.anchor.remove(id);
            pour.physics.remove(id);
        }
        target.noPhysics = false;
    }

    private void sink(LivingEntity target, float progress) {
        Vec3 spot = anchor.computeIfAbsent(target.getUUID(), id -> target.position());
        double y = spot.y - target.getBbHeight() * progress;
        target.noPhysics = true;
        target.fallDistance = 0;
        target.setDeltaMovement(Vec3.ZERO);
        target.setOnGround(false);
        physics.add(target.getUUID());
        if (target instanceof ServerPlayer player) {
            player.connection.teleport(spot.x, y, spot.z, player.getYRot(), player.getXRot());
        } else {
            target.setPos(spot.x, y, spot.z);
            target.hurtMarked = true;
        }
    }

    private void spread(ServerLevel level, float radius) {
        if (radius <= shown) return;
        float previous = shown;
        shown = radius;
        double prevSqr = previous <= 0f ? -1 : (previous - 0.01) * (previous - 0.01);
        double radSqr = (double) radius * radius;
        int minX = (int) Math.floor(getX() - radius);
        int maxX = (int) Math.ceil(getX() + radius);
        int minZ = (int) Math.floor(getZ() - radius);
        int maxZ = (int) Math.ceil(getZ() + radius);
        int baseY = (int) Math.floor(getY());
        List<BlockPos> wave = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - getX();
                double dz = z + 0.5 - getZ();
                double flat = dx * dx + dz * dz;
                if (flat > radSqr || flat <= prevSqr) continue;
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
                if (Math.abs(surface.getY() - baseY) <= 24) mark(level, surface, wave);
                if (Math.abs(surface.getY() - baseY) <= BAND) continue;
                for (int y = baseY + BAND; y >= baseY - BAND; y--) {
                    mark(level, new BlockPos(x, y, z), wave);
                }
            }
        }
        send(level, wave, false);
    }

    private void mark(ServerLevel level, BlockPos pos, List<BlockPos> wave) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        BlockPos above = pos.above();
        if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) return;
        if (!darkened.add(pos.asLong())) return;
        BlockPos copy = pos.immutable();
        darkenedBlocks.add(copy);
        wave.add(copy);
    }

    private void send(ServerLevel level, List<BlockPos> blocks, boolean restore) {
        if (blocks.isEmpty()) return;
        float reach = (entityData.get(DESTINATION) ? 50f : 100f) + 64f;
        for (int i = 0; i < blocks.size(); i += 400) {
            List<BlockPos> slice = new ArrayList<>(blocks.subList(i, Math.min(blocks.size(), i + 400)));
            PacketHandler.sendToNearbyPlayers(new DarknessEffectPacket(slice, restore, (int) shown), level, position(), reach);
        }
    }

    private void restore(ServerLevel level) {
        if (restored) return;
        restored = true;
        for (BlockPos pos : darkenedBlocks) {
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        darkenedBlocks.clear();
        darkened.clear();
    }

    private static void shake(LivingEntity target, float progress) {
        if (!(target instanceof ServerPlayer player)) return;
        float felt = 0.35f + progress * progress * 7.5f;
        PacketHandler.sendToPlayer(player, new CameraShakePacket(felt * 4f, 5));
    }

    private LivingEntity owner(ServerLevel level) {
        UUID id = entityData.get(OWNER).orElse(null);
        if (id == null) return null;
        Entity entity = level.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    public void remove(RemovalReason reason) {
        active.remove(this);
        if (level() instanceof ServerLevel serverLevel) {
            for (UUID id : physics) {
                if (serverLevel.getEntity(id) instanceof LivingEntity living) living.noPhysics = false;
            }
            physics.clear();
            restore(serverLevel);
        }
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(DESTINATION, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(DESTINATION, tag.getBoolean("Destination"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER).ifPresent(id -> tag.putUUID("Owner", id));
        tag.putBoolean("Destination", entityData.get(DESTINATION));
    }
}
