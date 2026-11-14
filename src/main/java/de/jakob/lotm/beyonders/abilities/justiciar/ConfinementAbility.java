package de.jakob.lotm.beyonders.abilities.justiciar;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.TheCorroderAbility;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ConfinementAbility extends Ability {

    public static final List<ConfinementZone> ACTIVE_ZONES = new CopyOnWriteArrayList<>();
    private static final Map<ServerLevel, Map<BlockPos, Integer>> BARRIER_REFERENCES = new HashMap<>();
    private static final Map<LivingEntity, Long> LAST_NOTICE = new WeakHashMap<>();

    private static final DustParticleOptions GOLD_DUST      = new DustParticleOptions(new Vector3f(1.0f, 0.80f, 0.0f), 1.3f);
    private static final DustParticleOptions PALE_GOLD_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.92f, 0.45f), 0.85f);
    private static final DustParticleOptions EMBER_DUST     = new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.05f), 1.0f);

    public ConfinementAbility(String id) {
        super(id, 30f, "confinement");
        interactionRadius = 15;
        hasOptimalDistance = false;
        postsUsedAbilityEventManually = true;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("justiciar", 6));
    }

    @Override
    protected float getSpiritualityCost() {
        return 600;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) level;

        ACTIVE_ZONES.stream()
                .filter(z -> z.ownerId.equals(entity.getUUID()))
                .findFirst()
                .ifPresent(ConfinementAbility::removeZone);

        int radius   = 6;
        int duration = (int) (1200 * multiplier(entity));
        Vec3 center  = AbilityUtil.getTargetLocation(entity, 12, 2f);
        if (center == null) return;

        List<BlockPos> placed = new ArrayList<>();
        Map<BlockPos, Integer> references = BARRIER_REFERENCES.computeIfAbsent(serverLevel, ignored -> new HashMap<>());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) continue;
                    BlockPos pos = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);
                    boolean shared = serverLevel.getBlockState(pos).is(Blocks.BARRIER) && references.containsKey(pos);
                    if (!serverLevel.getBlockState(pos).isAir() && !shared) continue;
                    if (!shared) serverLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                    references.merge(pos, 1, Integer::sum);
                    placed.add(pos);
                }
            }
        }

        ConfinementZone zone = new ConfinementZone(entity.getUUID(), center, radius, serverLevel, placed);
        ACTIVE_ZONES.add(zone);

        spawnSummonEffect(serverLevel, center, radius);

        Location centerLoc = new Location(center, serverLevel);

        zone.taskId = ServerScheduler.scheduleForDuration(0, 8, duration, () -> {
                    if (!zone.isActive()) return;
                    spawnEdgeParticles(serverLevel, center, radius);
                    spawnCornerFlares(serverLevel, center, radius);
                }, () -> removeZone(zone), serverLevel,
                () -> AbilityUtil.getTimeInArea(entity, centerLoc));

        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel, center, entity, this, interactionFlags, radius, 20 * 2));
    }

    private static void spawnSummonEffect(ServerLevel level, Vec3 center, int radius) {
        Location loc = new Location(center, level);

        ParticleUtil.createParticleSpirals(
                GOLD_DUST, loc,
                0.5, radius, radius, 3.0, 2.0, 60, 4, 3
        );

        ServerScheduler.scheduleForDuration(0, 1, 25, () -> {
            for (int corner = 0; corner < 8; corner++) {
                double cx = center.x + ((corner & 1) == 0 ? -radius : radius);
                double cy = center.y + ((corner & 2) == 0 ? -radius : radius);
                double cz = center.z + ((corner & 4) == 0 ? -radius : radius);
                level.sendParticles(GOLD_DUST, cx, cy, cz, 2, 0.15, 0.15, 0.15, 0);
                level.sendParticles(ParticleTypes.ENCHANT, cx, cy, cz, 3, 0.2, 0.2, 0.2, 0.08);
            }
        }, level);
    }

    private static void spawnEdgeParticles(ServerLevel level, Vec3 center, int r) {
        double cx = center.x, cy = center.y, cz = center.z;
        double step = 0.7;
        double pulse = Math.sin(System.currentTimeMillis() * 0.004) * 0.08;

        for (int sx : new int[]{-r, r}) {
            for (int sz : new int[]{-r, r}) {
                for (double dy = -r; dy <= r; dy += step) {
                    level.sendParticles(GOLD_DUST, cx + sx, cy + dy + pulse, cz + sz, 1, 0.03, 0.03, 0.03, 0);
                    if (Math.random() < 0.12) {
                        level.sendParticles(PALE_GOLD_DUST, cx + sx, cy + dy, cz + sz, 1, 0.05, 0.05, 0.05, 0);
                    }
                }
            }
        }
        for (int sy : new int[]{-r, r}) {
            for (int sz : new int[]{-r, r}) {
                for (double dx = -r; dx <= r; dx += step) {
                    level.sendParticles(GOLD_DUST, cx + dx, cy + sy + pulse, cz + sz, 1, 0.03, 0.03, 0.03, 0);
                }
            }
        }
        for (int sx : new int[]{-r, r}) {
            for (int sy : new int[]{-r, r}) {
                for (double dz = -r; dz <= r; dz += step) {
                    level.sendParticles(GOLD_DUST, cx + sx, cy + sy + pulse, cz + dz, 1, 0.03, 0.03, 0.03, 0);
                }
            }
        }
    }

    private static void spawnCornerFlares(ServerLevel level, Vec3 center, int r) {
        for (int corner = 0; corner < 8; corner++) {
            double cx = center.x + ((corner & 1) == 0 ? -r : r);
            double cy = center.y + ((corner & 2) == 0 ? -r : r);
            double cz = center.z + ((corner & 4) == 0 ? -r : r);
            if (Math.random() < 0.4) {
                level.sendParticles(EMBER_DUST, cx, cy, cz, 1, 0.1, 0.15, 0.1, 0);
            }
            if (Math.random() < 0.2) {
                level.sendParticles(ParticleTypes.ENCHANT, cx, cy, cz, 2, 0.15, 0.15, 0.15, 0.05);
            }
        }
    }

    private static void removeZone(ConfinementZone zone) {
        if (!zone.isActive()) return;
        zone.deactivate();
        ACTIVE_ZONES.remove(zone);
        if (zone.taskId != null) ServerScheduler.cancel(zone.taskId);
        ServerLevel level = zone.level;
        Map<BlockPos, Integer> references = BARRIER_REFERENCES.get(level);
        if (references == null) return;
        for (BlockPos pos : zone.claimed) {
            int remaining = references.getOrDefault(pos, 0) - 1;
            if (remaining > 0) { references.put(pos, remaining); continue; }
            references.remove(pos);
            if (level.getBlockState(pos).is(Blocks.BARRIER)) level.removeBlock(pos, false);
        }
        if (references.isEmpty()) BARRIER_REFERENCES.remove(level);
    }

    /** Used by teleport events and direct teleport entry points before any world/position mutation. */
    public static boolean canTeleport(LivingEntity entity, ServerLevel destinationLevel, Vec3 destination) {
        if (!(entity.level() instanceof ServerLevel sourceLevel)) return true;
        // Login, respawn and newly constructed replacement bodies are not teleporting out of a cell.
        if (!entity.isAlive() || entity.isRemoved() || sourceLevel.getEntity(entity.getId()) != entity) return true;
        for (ConfinementZone zone : ACTIVE_ZONES) {
            if (!zone.isActive()) continue;
            Vec3 source = entity.position();
            if (zone.bounds.blocks(sourceLevel == zone.level, source.x,source.y,source.z,
                    destinationLevel == zone.level,destination.x,destination.y,destination.z)) {
                if (entity instanceof ServerPlayer player) {
                    long now = sourceLevel.getServer().overworld().getGameTime();
                    if (now - LAST_NOTICE.getOrDefault(entity,-100L) >= 40) {
                        LAST_NOTICE.put(entity,now);
                        AbilityUtil.sendActionBar(player,Component.translatable("ability.lotmcraft.confinement.crossing_prohibited")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onEntityTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        Vec3 destination = new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ());

        if (!canTeleport(entity,serverLevel,destination)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCasterDeath(LivingDeathEvent event) {
        UUID id = event.getEntity().getUUID();
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        ACTIVE_ZONES.stream()
                .filter(z -> z.ownerId.equals(id))
                .findFirst()
                .ifPresent(ConfinementAbility::removeZone);
    }

    @SubscribeEvent
    public static void onCasterLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        ACTIVE_ZONES.stream()
                .filter(z -> z.ownerId.equals(id))
                .findFirst()
                .ifPresent(ConfinementAbility::removeZone);
    }
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ConfinementZone zone : ACTIVE_ZONES) {
            if (!zone.isActive()) continue;
            if (zone.level.getGameTime() % 10 == 0) zone.barriers.removeIf(pos -> !zone.level.getBlockState(pos).is(Blocks.BARRIER));
            TheCorroderAbility.corrodeBlocks(zone.level, zone.barriers);
            // Once the wall is fully eaten the zone ends and its teleport block lifts
            if (zone.barriers.isEmpty()) removeZone(zone);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ConfinementZone zone : ACTIVE_ZONES) removeZone(zone);
        BARRIER_REFERENCES.clear(); LAST_NOTICE.clear();
    }

    public static class ConfinementZone {
        public final UUID ownerId;
        public final Vec3 center;
        public final double radius;
        public final ServerLevel level;
        public final List<BlockPos> barriers;
        public final Set<BlockPos> claimed;
        public final ConfinementBounds bounds;
        private boolean active = true;
        private UUID taskId;

        public ConfinementZone(UUID ownerId, Vec3 center, double radius, ServerLevel level, List<BlockPos> barriers) {
            this.ownerId  = ownerId;
            this.center   = center;
            this.radius   = radius;
            this.level    = level;
            this.barriers = barriers;
            this.claimed = Set.copyOf(barriers);
            this.bounds = ConfinementBounds.around(center.x,center.y,center.z,(int) radius);
        }

        public boolean isActive() { return active; }
        public void deactivate()  { active = false; }
    }
}
