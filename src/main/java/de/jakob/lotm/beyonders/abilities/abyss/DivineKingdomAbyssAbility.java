package de.jakob.lotm.beyonders.abilities.abyss;

import de.jakob.lotm.attachments.BlockExpiryData;
import de.jakob.lotm.attachments.DisabledFlightComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.block.ModBlocks;
import de.jakob.lotm.block.custom.FilthBlock;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class DivineKingdomAbyssAbility extends Ability {

    private static final int RADIUS = 60;
    private static final int DEPTH = 3;
    private static final int VERTICAL_REACH = 16;          // how far above/below the caster it looks for ground
    private static final int DURATION_TICKS = 20 * 60;
    private static final int BUILD_WAVE_SIZE = 300;        // blocks converted per tick
    private static final int RESTORE_WAVE_SIZE = 600;      // blocks restored per tick
    // downward speed inside the filth
    private static final int DAMAGE_INTERVAL_TICKS = 20;
    private static final float DAMAGE_PER_PULSE = 1f;

    private static final int SINK_INTERVAL_TICKS = 20;   // one sink step per second
    private static final double SINK_IMPULSE = 0.3;      // downward push on each step (about 0.3 blocks)
    private static final double JUMP_STRENGTH = 0.25;       // first jump
    private static final double JUMP_FALLOFF = 0.6;         // each further jump is 60% of the last
    private static final double MIN_JUMP_STRENGTH = 0.05;   // below this a jump does nothing
    private static final int JUMP_COOLDOWN_TICKS = 10;
    private static final Map<UUID, Long> lastJump = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, Integer> jumpsInFilth = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int ENCASE_TICKS = 30;   // time fully encased before the teleport (2 seconds)
    private static final Map<UUID, Integer> encasedTicks = new java.util.concurrent.ConcurrentHashMap<>();

    private static final DustParticleOptions FILTH_DUST = new DustParticleOptions(new Vector3f(0.04f, 0.02f, 0.05f), 2f);

    private static class Kingdom {
        final UUID casterId;
        final ServerLevel level;
        final Vec3 center;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        boolean ended = false;
        Kingdom(UUID casterId, ServerLevel level, Vec3 center) {this.casterId = casterId;this.level = level;this.center = center;}
    }
    private static final List<Kingdom> ACTIVE = new CopyOnWriteArrayList<>();
    public DivineKingdomAbyssAbility(String id) {
        super(id, 120);
        canBeCopied = false;
        canBeReplicated = false;
        canBeUsedInArtifact = false;
        cannotBeStolen = true;
        canBeShared = false;
    }
    @Override
    public Map<String, Integer> getRequirements() {return new HashMap<>(Map.of("abyss", 0));}
    @Override
    protected float getSpiritualityCost() {return 15000;}
    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        if (ACTIVE.stream().anyMatch(k -> k.casterId.equals(entity.getUUID()))) return; // one kingdom per caster
        Kingdom kingdom = new Kingdom(entity.getUUID(), serverLevel, entity.position());
        ACTIVE.add(kingdom);
        buildFilth(kingdom);
        ServerScheduler.scheduleForDuration(0, 1, DURATION_TICKS, () -> tickKingdom(kingdom), () -> endKingdom(kingdom), serverLevel);
    }
    // Building
    private static void buildFilth(Kingdom k) {
        ServerLevel level = k.level;
        BlockPos c = BlockPos.containing(k.center);
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = c.getX() + dx, z = c.getZ() + dz;
                int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, c.getY() + VERTICAL_REACH);
                for (int y = top; y >= c.getY() - VERTICAL_REACH; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.blocksMotion() || !s.getFluidState().isEmpty()) continue;
                    for (int d = 0; d < DEPTH; d++) targets.add(p.below(d)); // the ground and the 2 blocks under it
                    break;
                }
            }
        }
        targets.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
        int wave = 0;
        for (int i = 0; i < targets.size(); i += BUILD_WAVE_SIZE, wave++) {
            List<BlockPos> chunk = new ArrayList<>(targets.subList(i, Math.min(i + BUILD_WAVE_SIZE, targets.size())));
            ServerScheduler.scheduleDelayed(wave, () -> placeWave(k, chunk), level);
        }
    }
    private static void placeWave(Kingdom k, List<BlockPos> wave) {
        if (k.ended || wave.isEmpty()) return;
        ServerLevel level = k.level;
        BlockState filth = ModBlocks.BLOCK_OF_FILTH.get().defaultBlockState();
        BlockExpiryData expiryData = BlockExpiryData.get(level);
        for (BlockPos p : wave) {
            BlockState originalState = level.getBlockState(p);
            if (originalState.isAir() || originalState.is(filth.getBlock()) || originalState.getDestroySpeed(level, p) < 0 || level.getBlockEntity(p) != null || !originalState.getFluidState().isEmpty()) {
                continue;
            }
            BlockPos pos = p.immutable();

            if (level.setBlock(pos, filth, Block.UPDATE_CLIENTS)) {
                k.original.put(pos, originalState);
                expiryData.schedule(level, pos, DURATION_TICKS, originalState);
            }
        }
        ParticleUtil.spawnParticles(level, FILTH_DUST, wave.getFirst().above().getCenter(), 6, 0.8, 0.05);
    }
    private static void tickKingdom(Kingdom k) {
        ServerLevel level = k.level;
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        LivingEntity caster = level.getEntity(k.casterId) instanceof LivingEntity l ? l : null;
        AABB box = new AABB(k.center.x - RADIUS, level.getMinBuildHeight(), k.center.z - RADIUS, k.center.x + RADIUS, level.getMaxBuildHeight(), k.center.z + RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !x.isSpectator() && horizontalDistSqr(x, k.center) <= RADIUS * RADIUS)) {
            boolean isCaster = e.getUUID().equals(k.casterId);
            // Abyss damage to everyone in range except the caster
            if (!isCaster && now % DAMAGE_INTERVAL_TICKS == 0) {
                Vec3 velocityBeforeDamage = e.getDeltaMovement();
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.source(level, ModDamageTypes.ABYSS, caster), DAMAGE_PER_PULSE);
                e.setDeltaMovement(velocityBeforeDamage);
            }
            // A standing seq 0 abyss is untouched. Sneaking makes them sink like everyone else.
            if (FilthBlock.isAbyss(e) && !e.isShiftKeyDown()) continue;
            BlockPos feet = BlockPos.containing(e.getX(), e.getY() + 0.05, e.getZ());
            BlockPos head = BlockPos.containing(e.getX(), e.getBoundingBox().maxY - 0.05, e.getZ());
            boolean feetIn = isFilth(level, feet);
            boolean encased = feetIn && isFilth(level, head);
            if (encased) {
                int t = encasedTicks.merge(e.getUUID(), 1, Integer::sum);
                if (t >= ENCASE_TICKS) {
                    encasedTicks.remove(e.getUUID());
                    sendToAbyss(server, e);
                    continue;
                }
            }
            else encasedTicks.remove(e.getUUID());
            Vec3 dm = e.getDeltaMovement();
            if (feetIn) {
                double y;
                if (dm.y > 0) y = dm.y;
                else if ((now + e.getId()) % SINK_INTERVAL_TICKS == 0) y = -SINK_IMPULSE;// the once-per-second sink step
                else y = 0;// cancels gravity between steps, so they hold still
                e.setDeltaMovement(dm.x * 0.6, y, dm.z * 0.6);
            } else {
                // Disable flight and cancel existing downward momentum once.
                if (e instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()) {
                    jumpsInFilth.remove(e.getUUID());
                    Abilities abilities = p.getAbilities();
                    if (abilities.flying) {
                        abilities.flying = false;
                        // Remove the sudden downward momentum when flight is disabled.
                        Vec3 velocity = e.getDeltaMovement();
                        e.setDeltaMovement(velocity.x, 0.0, velocity.z);
                        p.onUpdateAbilities();
                    }
                }
                lockFlight(e);
            }
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
    }
    private static boolean isFilth(ServerLevel level, BlockPos p) {return level.getBlockState(p).is(ModBlocks.BLOCK_OF_FILTH.get());}
    private static double horizontalDistSqr(LivingEntity e, Vec3 c) {
        double dx = e.getX() - c.x, dz = e.getZ() - c.z;
        return dx * dx + dz * dz;
    }
    private static void lockFlight(LivingEntity e) {
        DisabledFlightComponent disabled = e.getData(ModAttachments.FLIGHT_DISABLE_COMPONENT);
        // Refresh the lock while the entity is inside the kingdom.
        disabled.setCooldownTicks(Math.max(disabled.getCooldownTicks(), 2));
        if (e instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()) {
            Abilities abilities = p.getAbilities();
            if (abilities.flying || abilities.mayfly) {
                abilities.flying = false;
                p.onUpdateAbilities();
            }
        }
        if (!(e instanceof Player) && e.isNoGravity()) e.setNoGravity(false);
    }
    private static void sendToAbyss(MinecraftServer server, LivingEntity e) {
        ServerLevel abyss = server.getLevel(ModDimensions.ABYSS_DIMENSION_KEY);
        if (abyss == null || !e.isAlive()) return;
        double x = e.getX();
        double z = e.getZ();
        int startY = 100;
        int maxY = abyss.getMaxBuildHeight() - (int) Math.ceil(e.getBbHeight()) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Load the chunk containing the destination.
        int chunkX = Mth.floor(x) >> 4;
        int chunkZ = Mth.floor(z) >> 4;
        abyss.getChunk(chunkX, chunkZ);
        Vec3 safePosition = null;
        // Search upward from Y = 100.
        for (int y = startY; y <= maxY; y++) {
            AABB box = e.getBoundingBox().move(x - e.getX(), y - e.getY(), z - e.getZ());
            int minX = Mth.floor(box.minX + 1.0E-5);
            int maxX = Mth.floor(box.maxX - 1.0E-5);
            int minY = Mth.floor(box.minY + 1.0E-5);
            int maxBoxY = Mth.floor(box.maxY - 1.0E-5);
            int minZ = Mth.floor(box.minZ + 1.0E-5);
            int maxZ = Mth.floor(box.maxZ - 1.0E-5);
            boolean clear = true;
            for (int bx = minX; bx <= maxX && clear; bx++) {
                for (int bz = minZ; bz <= maxZ && clear; bz++) {
                    abyss.getChunk(bx >> 4, bz >> 4);
                    for (int by = minY; by <= maxBoxY; by++) {
                        pos.set(bx, by, bz);
                        BlockState state = abyss.getBlockState(pos);
                        if (!state.getCollisionShape(abyss, pos).isEmpty()) {
                            clear = false;
                            break;
                        }
                    }
                }
            }
            if (clear) {
                safePosition = new Vec3(x, y, z);
                break;
            }
        }
        // Teleport after the destination has been checked.
        assert safePosition != null;
        e.teleportTo(abyss, safePosition.x, safePosition.y, safePosition.z, Set.of(), e.getYRot(), e.getXRot());
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
    }
    // Ending
    private static void endKingdom(Kingdom k) {
        k.ended = true;
        ServerLevel level = k.level;
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(k.original.entrySet());
        int wave = 0;
        for (int i = 0; i < entries.size(); i += RESTORE_WAVE_SIZE, wave++) {
            List<Map.Entry<BlockPos, BlockState>> chunk = entries.subList(i, Math.min(i + RESTORE_WAVE_SIZE, entries.size()));
            ServerScheduler.scheduleDelayed(wave, () -> restore(level, chunk), level);
        }
        ServerScheduler.scheduleDelayed(wave + 1, () -> {rescueEntities(k); ACTIVE.remove(k);}, level);
        lastJump.clear();
    }
    private static void restore(ServerLevel level, List<Map.Entry<BlockPos, BlockState>> chunk) {
        BlockExpiryData expiryData = BlockExpiryData.get(level);
        for (Map.Entry<BlockPos, BlockState> entry : chunk) {
            BlockPos pos = entry.getKey();
            if (level.getBlockState(pos).is(ModBlocks.BLOCK_OF_FILTH.get())) {
                expiryData.cancel(pos);
                level.setBlock(pos, entry.getValue(), Block.UPDATE_ALL);
            }
        }
    }

    private static void rescueEntities(Kingdom k) {
        ServerLevel level = k.level;
        AABB box = new AABB(k.center.x - RADIUS, level.getMinBuildHeight(), k.center.z - RADIUS, k.center.x + RADIUS, level.getMaxBuildHeight(), k.center.z + RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (level.noCollision(e)) continue;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
            if (e instanceof ServerPlayer p) p.teleportTo(level, e.getX(), y, e.getZ(), Set.of(), p.getYRot(), p.getXRot());
            else e.teleportTo(e.getX(), y, e.getZ());
        }
    }
    public static void markJump(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = lastJump.get(player.getUUID());
        if (last != null && now - last < JUMP_COOLDOWN_TICKS) return;
        int count = jumpsInFilth.getOrDefault(player.getUUID(), 0);
        double strength = JUMP_STRENGTH * Math.pow(JUMP_FALLOFF, count);
        if (strength < MIN_JUMP_STRENGTH) return;
        lastJump.put(player.getUUID(), now);
        jumpsInFilth.put(player.getUUID(), count + 1);
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x, Math.max(dm.y, strength), dm.z);
        player.hurtMarked = true;
    }
}