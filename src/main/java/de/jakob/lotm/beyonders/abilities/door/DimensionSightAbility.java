package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAbility;
import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAuthorityAbility;
import de.jakob.lotm.beyonders.abilities.demoness.InvisibilityAbility;
import de.jakob.lotm.beyonders.abilities.demoness.ShadowConcealmentAbility;
import de.jakob.lotm.beyonders.abilities.error.DeceitAbility;
import de.jakob.lotm.beyonders.abilities.error.ParasitationAbility;
import de.jakob.lotm.beyonders.abilities.twilight_giant.LightConcealmentAbility;
import de.jakob.lotm.beyonders.abilities.twilight_giant.MercuryArmoryAbility;
import de.jakob.lotm.beyonders.abilities.twilight_giant.MindConcealmentAbility;
import de.jakob.lotm.beyonders.abilities.visionary.handlers.VisionaryHandler;
import de.jakob.lotm.entity.custom.ability_entities.door_pathway.DimensionSightEntity;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.OpenCoordinateScreenPacket;
import de.jakob.lotm.network.packets.toClient.OpenPlayerDivinationScreenPacket;
import de.jakob.lotm.network.packets.toClient.SyncDimensionSightPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.PlayerInfo;
import de.jakob.lotm.util.data.PlayerSelectionWorkType;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DimensionSightAbility extends SelectableAbility {
    private static final int RADIUS = 12;
    private static final int DOWN = 8;
    private static final int UP = 12;
    private static final int MAX_BLOCKS = 6000;
    private static final int MAX_SIGHTED = 64;
    private static final int TICKET = 3;
    private static final float UPKEEP = 40f;
    private static final int COLOR = 0xa0e2fa;

    private static final Map<UUID, Integer> pending = new HashMap<>();
    private static final Map<UUID, Sight> sights = new HashMap<>();
    private static final Map<String, Integer> held = new HashMap<>();

    private static final class Sight {
        final DimensionSightEntity veil;
        final BlockPos spot;
        final UUID target;
        final Set<Integer> known = new HashSet<>();
        ChunkPos ticket;
        ServerLevel level;
        BlockPos center;
        int age;
        int terrainAge = -100;

        Sight(DimensionSightEntity veil, ServerLevel level, BlockPos spot, UUID target) {
            this.veil = veil;
            this.level = level;
            this.spot = spot;
            this.target = target;
        }
    }

    public DimensionSightAbility(String id) {
        super(id, 3);

        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeShared = false;

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(1500f, 1500f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 2));
    }

    @Override
    public float getSpiritualityCost() {
        return 1500f;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.dimension_sight.location",
                "ability.lotmcraft.dimension_sight.player"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (stop(player.getUUID())) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.dimension_sight.closed").withColor(COLOR));
            return;
        }
        switch (selectedAbility) {
            case 0 -> {
                pending.put(player.getUUID(), 0);
                PacketHandler.sendToPlayer(player, new OpenCoordinateScreenPacket("dimension_sight"));
            }
            case 1 -> {
                List<PlayerInfo> players = new ArrayList<>();
                for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
                    if (other == player || concealed(player, other)) continue;
                    players.add(new PlayerInfo(other.getUUID(), other.getGameProfile().getName()));
                }
                if (players.isEmpty()) {
                    AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.dimension_sight.none").withColor(COLOR));
                    return;
                }
                pending.put(player.getUUID(), 1);
                PacketHandler.sendToPlayer(player, new OpenPlayerDivinationScreenPacket(players, PlayerSelectionWorkType.DIMENSION_SIGHT));
            }
        }
    }

    public static void watchLocation(ServerPlayer player, BlockPos pos) {
        Integer mode = pending.remove(player.getUUID());
        if (mode == null || mode != 0) return;
        ServerLevel level = player.serverLevel();
        if (!level.getWorldBorder().isWithinBounds(pos)) return;
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        BlockPos spot = new BlockPos(pos.getX(), Math.clamp(Math.min(pos.getY(), surface), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), pos.getZ());
        if (SpaceConcealmentAbility.isInsideConcealedSpace(Vec3.atCenterOf(spot))) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.dimension_sight.hidden").withColor(COLOR));
            return;
        }
        start(player, level, spot, null);
    }

    public static void watchPlayer(ServerPlayer player, UUID uuid) {
        Integer mode = pending.remove(player.getUUID());
        if (mode == null || mode != 1) return;
        ServerPlayer target = player.server.getPlayerList().getPlayer(uuid);
        if (target == null || target == player || !target.isAlive() || concealed(player, target)) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.dimension_sight.lost").withColor(COLOR));
            return;
        }
        start(player, target.serverLevel(), null, uuid);
    }

    private static void start(ServerPlayer player, ServerLevel level, BlockPos spot, UUID target) {
        stop(player.getUUID());
        Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot()).scale(2.5);
        DimensionSightEntity veil = new DimensionSightEntity(player.level(), player.getEyePosition().add(ahead), player.getYRot());
        player.level().addFreshEntity(veil);

        Sight sight = new Sight(veil, level, spot, target);
        if (spot != null) {
            sight.ticket = new ChunkPos(spot);
            hold(level, sight.ticket);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) level.getChunk(sight.ticket.x + x, sight.ticket.z + z);
            }
        }
        sights.put(player.getUUID(), sight);
        player.level().playSound(null, veil.getX(), veil.getY(), veil.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    private static boolean stop(UUID caster) {
        Sight sight = sights.remove(caster);
        if (sight == null) return false;
        sight.veil.discard();
        if (sight.ticket != null) release(sight.level, sight.ticket);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (sights.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (UUID caster : new ArrayList<>(sights.keySet())) {
            Sight sight = sights.get(caster);
            ServerPlayer player = server.getPlayerList().getPlayer(caster);
            String reason = check(server, player, sight);
            if (reason != null) {
                stop(caster);
                if (player != null && !reason.isEmpty()) {
                    AbilityUtil.sendActionBar(player, Component.translatable(reason).withColor(COLOR));
                }
                continue;
            }
            update(player, sight);
        }
    }

    private static String check(MinecraftServer server, ServerPlayer player, Sight sight) {
        if (player == null || !player.isAlive() || sight.veil.isRemoved()) return "";
        if (player.level() != sight.veil.level() || player.distanceTo(sight.veil) > 48) return "ability.lotmcraft.dimension_sight.closed";
        if (++sight.age % 20 == 0) {
            if (BeyonderData.getSpirituality(player) < UPKEEP) return "ability.lotmcraft.dimension_sight.exhausted";
            BeyonderData.reduceSpirituality(player, UPKEEP);
        }
        if (sight.target != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(sight.target);
            if (target == null || !target.isAlive() || concealed(player, target)) return "ability.lotmcraft.dimension_sight.lost";
            sight.level = target.serverLevel();
        }
        return null;
    }

    private static void update(ServerPlayer player, Sight sight) {
        BlockPos focus = sight.spot;
        if (sight.target != null) {
            ServerPlayer target = player.server.getPlayerList().getPlayer(sight.target);
            if (target != null) focus = target.blockPosition();
        }
        if (focus == null) return;

        int since = sight.age - sight.terrainAge;
        boolean terrain = sight.center == null || sight.age <= 5 || since >= 40
                || (since >= 10 && sight.center.distManhattan(focus) >= 3);
        if (terrain) {
            sight.center = focus;
            sight.terrainAge = sight.age;
        }

        ServerLevel level = sight.level;
        BlockPos center = sight.center;
        int[] blocks = terrain ? terrain(level, center) : new int[0];

        AABB box = new AABB(center.getX() - RADIUS, center.getY() - DOWN, center.getZ() - RADIUS,
                center.getX() + RADIUS + 1, center.getY() + UP + 1, center.getZ() + RADIUS + 1);
        List<SyncDimensionSightPacket.Sighted> sighted = new ArrayList<>();
        Set<Integer> present = new HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isInvisible())) {
            if (sighted.size() >= MAX_SIGHTED) break;
            double x = entity.getX() - (center.getX() + 0.5);
            double y = entity.getY() - center.getY();
            double z = entity.getZ() - (center.getZ() + 0.5);
            if (x * x + z * z > (RADIUS + 0.5) * (RADIUS + 0.5) || concealed(player, entity)) continue;

            present.add(entity.getId());
            boolean fresh = sight.known.add(entity.getId());
            boolean isPlayer = entity instanceof Player;
            CompoundTag data = !isPlayer && (terrain || fresh) ? snapshot(entity) : null;
            sighted.add(new SyncDimensionSightPacket.Sighted(
                    entity.getId(),
                    BuiltInRegistries.ENTITY_TYPE.getId(entity.getType()),
                    isPlayer ? entity.getUUID() : null,
                    isPlayer ? ((Player) entity).getGameProfile().getName() : "",
                    (float) x, (float) y, (float) z,
                    entity.yBodyRot, entity.getYHeadRot(), entity.getXRot(),
                    data));
        }
        sight.known.retainAll(present);

        PacketHandler.sendToTracking(sight.veil, new SyncDimensionSightPacket(sight.veil.getId(), center, terrain, blocks, sighted));
    }

    private static int[] terrain(ServerLevel level, BlockPos center) {
        int[] out = new int[MAX_BLOCKS * 2];
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                for (int dy = -DOWN; dy <= UP; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !exposed(level, state, pos, near, dx, dy, dz)) continue;
                    out[count++] = (dx + 64) | (dy + 64) << 7 | (dz + 64) << 14;
                    out[count++] = Block.getId(state);
                    if (count >= out.length) return out;
                }
            }
        }
        return Arrays.copyOf(out, count);
    }

    private static boolean exposed(ServerLevel level, BlockState state, BlockPos pos, BlockPos.MutableBlockPos near, int dx, int dy, int dz) {
        for (Direction direction : Direction.values()) {
            int nx = dx + direction.getStepX();
            int ny = dy + direction.getStepY();
            int nz = dz + direction.getStepZ();
            if (ny < -DOWN) continue;
            if (ny > UP || nx * nx + nz * nz > RADIUS * RADIUS) return true;
            BlockState other = level.getBlockState(near.setWithOffset(pos, direction));
            if (other.canOcclude() || other.getBlock() == state.getBlock()) continue;
            if (!state.getFluidState().isEmpty() && other.getFluidState().getType().isSame(state.getFluidState().getType())) continue;
            return true;
        }
        return false;
    }

    private static CompoundTag snapshot(LivingEntity entity) {
        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        tag.remove("Brain");
        tag.remove("Passengers");
        tag.remove("Attributes");
        return tag;
    }

    private static boolean concealed(ServerPlayer viewer, LivingEntity target) {
        if (target == viewer) return false;
        int seq = BeyonderData.getSequence(viewer);
        UUID uuid = target.getUUID();
        return ConcealmentAbility.veiled(viewer, target)
                || ConcealmentAuthorityAbility.undivinable(target)
                || SpaceConcealmentAbility.isInsideConcealedSpace(target.position())
                || VisionaryHandler.shouldStayInvisible(seq, target)
                || MindConcealmentAbility.isHiddenFrom(viewer, target)
                || LightConcealmentAbility.isHidden(target)
                || MercuryArmoryAbility.isHidden(target)
                || ParasitationAbility.isConcealed(uuid)
                || ShadowConcealmentAbility.invisiblePlayers.contains(uuid)
                || InvisibilityAbility.invisiblePlayers.contains(uuid)
                || (DeceitAbility.cannotBeTargeted.contains(uuid) && seq > BeyonderData.getSequence(target));
    }

    private static void hold(ServerLevel level, ChunkPos pos) {
        String key = level.dimension().location() + "|" + pos.toLong();
        int count = held.getOrDefault(key, 0);
        if (count == 0) level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, TICKET, pos);
        held.put(key, count + 1);
    }

    private static void release(ServerLevel level, ChunkPos pos) {
        String key = level.dimension().location() + "|" + pos.toLong();
        int count = held.getOrDefault(key, 0) - 1;
        if (count <= 0) {
            held.remove(key);
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, TICKET, pos);
        } else {
            held.put(key, count);
        }
    }
}
