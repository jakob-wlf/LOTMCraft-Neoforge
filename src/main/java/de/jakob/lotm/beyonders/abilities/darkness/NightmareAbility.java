package de.jakob.lotm.beyonders.abilities.darkness;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.SealedDimensionData;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.entity.custom.ability_entities.ControlBodyDouble;
import de.jakob.lotm.entity.custom.ability_entities.darkness_pathway.NightmareBodyEntity;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.SyncNightmareAbilityPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.data.NightmareCenter;
import de.jakob.lotm.util.helper.*;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class NightmareAbility extends SelectableAbility {
    private static final HashMap<UUID, NightmareCenter> activeNightmaresServer = new HashMap<>();
    private static final HashMap<UUID, NightmareCenter> activeNightmaresClient = new HashMap<>();
    private static final HashSet<UUID> isReshaping = new HashSet<>();
    private static final HashMap<UUID, RegionSnapshot> storedRegions = new HashMap<>();
    private static final HashMap<ResourceKey<Level>, HashSet<BlockPos>> noDropPositions = new HashMap<>();
    private static final Map<UUID, Dream> dreams = new HashMap<>();
    private static final Set<UUID> ending = new HashSet<>();
    private static final int COPY_RADIUS = 25;
    private static final int SEQUENCE_SCALE = 5;
    private static final String RETURN_TAG = "lotm_nightmare_return";
    private static UUID loggingOut;

    public static HashMap<ResourceKey<Level>, HashSet<BlockPos>> getNoDropPositions() {
        return noDropPositions;
    }

    public NightmareAbility(String id) {
        super(id, 0.5f);
        canBeUsedByNPC = false;
        canBeCopied = false;
        autoClear = false;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(1, 1, 1, 2, 2, 3, 3, 3));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3000f, 2400f, 1900f, 900f, 600f, 400f, 250f, 100f));

        baseDamage = 10;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 7));
    }

    @Override
    protected float getSpiritualityCost() {
        return 40;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.nightmare.nightmare",
                //"ability.lotmcraft.nightmare.reshape",
                "ability.lotmcraft.nightmare.restrict",
                "ability.lotmcraft.nightmare.attack",
                "ability.lotmcraft.nightmare.teleport"};
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        switch(abilityIndex) {
            case 0 -> nightmare(level, entity);
            //case 1 -> reshape(level, entity);
            case 1 -> restrict(level, entity);
            case 2 -> attack(level, entity);
            case 3 -> teleport(level, entity);
        }

        if(abilityIndex != 2){
            clearArtifactScaling(entity);
        }
    }

    private void teleport(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        if(!inNightmare(entity)) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("You need to create a Nightmare first.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }

        Vec3 targetLoc = getTargetBlock(entity, 8).getCenter().add(0, 1, 0);
        if(!isBlockInRadius(targetLoc, entity.getUUID())) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("You cant teleport outside the nightmare.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }
        level.playSound(null, targetLoc.x, targetLoc.y, targetLoc.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, .5f, 1);

        var validatedPos = TeleportationUtil.clampToBorder( (ServerLevel) level, targetLoc);

        entity.teleportTo(validatedPos.x, validatedPos.y, validatedPos.z);
        ParticleUtil.spawnParticles((ServerLevel) level, dustSmall, validatedPos.add(0, .5, 0), 30, .4, 1, .4, 0);
    }

    private void attack(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        if(!inNightmare(entity)) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("You need to create a Nightmare first.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }

        Vec3 targetLoc = AbilityUtil.getTargetLocation(entity, baseDistance, 2);
        if(!isBlockInRadius(targetLoc, entity.getUUID())) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("You cant attack outside the nightmare.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }

        Vec3 startLoc = VectorUtil.getRelativePosition(targetLoc, entity.getLookAngle().normalize(), 0, random.nextDouble(-3, 3), random.nextDouble(1, 3));
        Vec3 dir = targetLoc.subtract(startLoc).normalize().scale(.6);

        AtomicReference<Vec3> currentPos = new AtomicReference<>(startLoc);

        AtomicBoolean hasHit = new AtomicBoolean(false);

        ServerScheduler.scheduleUntil((ServerLevel) level, () -> {
            Vec3 pos = currentPos.get();

            if(AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 1.2f, ModDamageTypes.IMAGINATION, baseDamage, pos, true, false, true, 0)) {
                hasHit.set(true);
                return;
            }
            BlockState state = level.getBlockState(BlockPos.containing(pos));
            if(!state.getCollisionShape(level, BlockPos.containing(pos)).isEmpty() || state.getBlock() == Blocks.VOID_AIR) {
                hasHit.set(true);
                return;
            }
            ParticleUtil.spawnParticles((ServerLevel) level, dustVerySmall, pos, 20, .25, 0);

            currentPos.set(pos.add(dir));
        }, () -> this.clearArtifactScaling(entity), hasHit);
    }

    private void restrict(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        if(!inNightmare(entity)) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("You need to create a Nightmare first.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }

        LivingEntity targetEntity = AbilityUtil.getTargetEntity(entity, baseDistance, 2);
        if(targetEntity == null || !isAffectedByNightmare(targetEntity)) {
            Vec3 targetPos = AbilityUtil.getTargetLocation(entity, baseDistance, 1.5f, true);
            ParticleUtil.createParticleSpirals((ServerLevel) level, dustSmall, targetPos, 2, 2, 2.5, .5, 8, 20 * 5, 11, 8);
            return;
        }

        Location loc = new Location(targetEntity.position(), level);
        ParticleUtil.createParticleSpirals(dustVerySmall, loc, 1.2, 1.2, 2.5, .5, 8, 20 * 20, 11, 8);

        ServerScheduler.scheduleForDuration(0, 2, 20 * 2, () -> {
            if(entity.level().isClientSide)
                return;
            loc.setPosition(targetEntity.position());
            loc.setLevel(entity.level());
            Vec3 startPos = targetEntity.getEyePosition().subtract(0, .15, 0);

            ParticleUtil.drawParticleLine((ServerLevel) level, dustSmall, startPos, startPos.add(2.75, -3, 0), .25, 1);
            ParticleUtil.drawParticleLine((ServerLevel) level, dustSmall, startPos, startPos.add(-2.75, -3, 0), .25, 1);
            ParticleUtil.drawParticleLine((ServerLevel) level, dustSmall, startPos, startPos.add(0, -3, -2.75), .25, 1);
            ParticleUtil.drawParticleLine((ServerLevel) level, dustSmall, startPos, startPos.add(0, -3, 2.75), .25, 1);

            targetEntity.setOnGround(true);
            var pos = targetEntity.position();
            targetEntity.setDeltaMovement(new Vec3(0, 0, 0));
            targetEntity.hurtMarked = true;

            targetEntity.teleportTo(pos.x, pos.y, pos.z);
            targetEntity.hurtMarked = true;
            targetEntity.addEffect(new MobEffectInstance(ModEffects.ASLEEP, 20, 10, false, false, false));
        }, (ServerLevel) level);
    }

    private void reshape(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        if(isReshaping.contains(entity.getUUID())) {
            isReshaping.remove(entity.getUUID());
            return;
        }

        if(!inNightmare(entity)) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("You need to create a Nightmare first.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }

        BlockPos targetLoc = AbilityUtil.getTargetBlock(entity, baseDistance, false);
        BlockState state = level.getBlockState(targetLoc);
        if(!isBlockInRadius(targetLoc.getCenter(), entity.getUUID()) || state.getCollisionShape(level, targetLoc).isEmpty()) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("Select a solid block inside the nightmare.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }

        isReshaping.add(entity.getUUID());

        double radius = entity.getEyePosition().distanceTo(targetLoc.getCenter());
        Block block = state.getBlock();

        AtomicBoolean shouldStop = new AtomicBoolean(false);

        ServerScheduler.scheduleUntil((ServerLevel) level, () -> {
            if(!activeNightmaresServer.containsKey(entity.getUUID())) {
                shouldStop.set(true);
                isReshaping.remove(entity.getUUID());
                return;
            }

            if(!isReshaping.contains(entity.getUUID())) {
                shouldStop.set(true);
                return;
            }

            BlockPos currentTarget = AbilityUtil.getTargetBlock(entity, radius - 2, radius, false);

            if(currentTarget.getCenter().distanceTo(entity.position()) < 5)
                return;

            createBlockSphere(block, (ServerLevel) level, currentTarget.getCenter(), entity.getUUID());
        }, 2, null, shouldStop);

    }

    public static void stopNightmare(UUID uuid) {
        stopNightmare(uuid, null, null, 0f);
    }

    private static boolean stopNightmare(UUID uuid, UUID owner, DamageSource source, float amount) {
        if (!ending.add(uuid)) return false;
        try {
            Dream dream = dreams.remove(uuid);
            activeNightmaresServer.remove(uuid);
            storedRegions.remove(uuid);
            if (dream == null) return false;
            MinecraftServer server = dream.maze.getServer();
            boolean hit = false;
            if (server != null && owner != null && source != null && amount > 0) {
                LivingEntity entity = find(server, owner);
                if (entity != null && entity.isAlive()) {
                    entity.invulnerableTime = 0;
                    hit = entity.hurt(source, amount);
                }
            }
            SealedDimensionData sealed = SealedDimensionData.get(dream.maze);
            String mazeId = dream.maze.dimension().location().toString();
            if (sealed.isActive() && mazeId.equalsIgnoreCase(sealed.getDimensionLocation())) sealed.setTicksRemaining(0);
            sendBack(server, uuid, dream.casterSpot);
            discard(server, dream.casterBody);
            for (Target target : dream.targets) {
                sendBack(server, target.id, target.spot);
                discard(server, target.body);
            }
            fill(dream.maze, dream.plot, dream.maze, dream.plot, dream.radius, true);
            HashSet<BlockPos> marked = noDropPositions.get(dream.maze.dimension());
            if (marked != null) {
                Vec3 center = Vec3.atBottomCenterOf(dream.plot);
                marked.removeIf(pos -> pos.getCenter().distanceToSqr(center) <= (dream.radius * dream.radius));
            }
            syncFog(server, uuid, dream.targets, false, Vec3.ZERO, 0);
            return hit;
        } finally {
            ending.remove(uuid);
        }
    }

    public BlockPos getTargetBlock(LivingEntity entity, double radius) {
        Vec3 lookDirection = entity.getLookAngle().normalize();
        Vec3 playerPosition = entity.position().add(0, entity.getEyeHeight(), 0);

        Vec3 targetPosition = playerPosition;

        for(int i = 0; i < radius; i++) {
            targetPosition = playerPosition.add(lookDirection.scale(i));

            BlockState block = entity.level().getBlockState(BlockPos.containing(targetPosition));

            if (!block.getCollisionShape(entity.level(), BlockPos.containing(targetPosition)).isEmpty()) {
                targetPosition = playerPosition.add(lookDirection.scale(i - 1));
            }
        }

        return BlockPos.containing(targetPosition);
    }

    private final DustParticleOptions dustSmall = new DustParticleOptions(new Vector3f(250 / 255f, 40 / 255f, 64 / 255f), 1.2f);
    private final DustParticleOptions dustVerySmall = new DustParticleOptions(new Vector3f(250 / 255f, 40 / 255f, 64 / 255f), .7f);

    private void nightmare(Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer caster)) return;

        if (activeNightmaresServer.containsKey(caster.getUUID())) {
            stopNightmare(caster.getUUID());
            return;
        }

        MinecraftServer server = caster.getServer();
        if (server == null) return;
        int playerSeq = BeyonderData.getSequence(caster);
        List<UUID> same = new ArrayList<>();
        for (Map.Entry<UUID, Dream> entry : dreams.entrySet()) {
            LivingEntity other = find(server, entry.getKey());
            int otherSeq = other == null ? entry.getValue().casterSeq : BeyonderData.getSequence(other);
            if (otherSeq == playerSeq) same.add(entry.getKey());
        }
        if (!same.isEmpty()) {
            bar(caster, "Both nightmares are broken.");
            for (UUID id : same) {
                LivingEntity other = find(server, id);
                if (other instanceof ServerPlayer player && player != caster) bar(player, "Both nightmares are broken.");
                stopNightmare(id);
            }
            return;
        }
        if (dreamOf(caster.getUUID()) != null) {
            bar(caster, "You are already dreaming.");
            return;
        }

        int seq = AbilityUtil.getSeqWithArt(caster, this);
        int reach = COPY_RADIUS + SEQUENCE_SCALE * Math.max(0, 7 - seq);
        List<LivingEntity> pool = new ArrayList<>();
        if (seq <= 4) {
            pool.addAll(AbilityUtil.getNearbyEntities(caster, (ServerLevel) caster.level(), caster.position(), reach, false, true));
        } else {
            LivingEntity looked = AbilityUtil.getTargetEntity(caster, reach, 2);
            if (looked != null && looked != caster) pool.add(looked);
        }
        pool.removeIf(candidate -> candidate instanceof NightmareBodyEntity || candidate instanceof ControlBodyDouble);
        if (pool.isEmpty()) {
            bar(caster, "No target in sight.");
            return;
        }

        float casterSpirit = BeyonderData.getSpirituality(caster);
        boolean dreaming = false;
        boolean tooStrong = false;
        List<LivingEntity> pulled = new ArrayList<>();
        float strongest = 0;
        for (LivingEntity candidate : pool) {
            if (dreamOf(candidate.getUUID()) != null) {
                dreaming = true;
                continue;
            }
            float spirit = BeyonderData.getSpirituality(candidate);
            if (spirit > casterSpirit) {
                tooStrong = true;
                continue;
            }
            strongest = Math.max(strongest, spirit);
            pulled.add(candidate);
        }
        if (pulled.isEmpty()) {
            bar(caster, tooStrong ? "Their spirituality is too high." : dreaming ? "They are already dreaming." : "No target in sight.");
            return;
        }

        ServerLevel maze = server.getLevel(ModDimensions.DREAM_MAZE_DIMENSION_KEY);
        if (maze == null) {
            bar(caster, "The dream has nowhere to open.");
            return;
        }

        ServerLevel source = (ServerLevel) caster.level();
        BlockPos anchor = caster.blockPosition();
        int minY = maze.getMinBuildHeight();
        int maxY = maze.getMaxBuildHeight() - 1;
        int margin = Math.min(reach, Math.max(1, (maxY - minY) / 2));
        int low = minY + margin;
        int high = maxY - margin;
        int shiftedY = low <= high ? Math.clamp(anchor.getY(), low, high) : Math.clamp(anchor.getY(), minY + 1, maxY - 1);
        int yShift = shiftedY - anchor.getY();
        BlockPos plot = anchor.offset(0, yShift, 0);
        Vec3 centerPos = caster.position().add(0, yShift, 0);

        int seconds = (int) Math.min(60f, 8 + (casterSpirit - strongest) / 150f);
        fill(source, anchor, maze, plot, reach, false);

        Spot casterSpot = spot(caster);
        remember(caster);
        ParticleUtil.spawnParticles((ServerLevel) caster.level(), dustSmall, caster.position().add(0, 1, 0), 30, .3, .5, .3, 0);
        UUID casterBody = leaveBody(caster, caster.getUUID());

        List<Target> targets = new ArrayList<>();
        for (LivingEntity target : pulled) {
            if (target instanceof ServerPlayer targetPlayer) remember(targetPlayer);
            ParticleUtil.spawnParticles((ServerLevel) target.level(), dustSmall, target.position().add(0, 1, 0), 30, .3, .5, .3, 0);
            UUID body = leaveBody(target, caster.getUUID());
            targets.add(new Target(target.getUUID(), spot(target), body));
        }

        place(caster, maze, new Vec3(casterSpot.x, casterSpot.y + yShift, casterSpot.z), casterSpot.yRot, casterSpot.xRot);
        for (int i = 0; i < pulled.size(); i++) {
            LivingEntity target = pulled.get(i);
            Spot at = targets.get(i).spot();
            LivingEntity moved = place(target, maze, new Vec3(at.x(), at.y() + yShift, at.z()), at.yRot(), at.xRot());
            if (!moved.getUUID().equals(targets.get(i).id())) {
                targets.set(i, new Target(moved.getUUID(), at, targets.get(i).body()));
            }
        }

        dreams.put(caster.getUUID(), new Dream(maze, casterSpot, plot, casterBody, targets, seq, reach, yShift));
        activeNightmaresServer.put(caster.getUUID(), new NightmareCenter(maze, centerPos, reach * reach));
        syncFog(maze.getServer(), caster.getUUID(), targets, true, centerPos, reach);

        UUID casterId = caster.getUUID();
        int[] remaining = {seconds * 20};
        AtomicBoolean shouldStop = new AtomicBoolean(false);
        ServerScheduler.scheduleUntil(maze, () -> {
            Dream dream = dreams.get(casterId);
            if (dream == null) {
                shouldStop.set(true);
                return;
            }
            if (escaped(maze, dream, casterId, centerPos, true)) {
                shouldStop.set(true);
                stopNightmare(casterId);
                return;
            }
            for (Target target : dream.targets) {
                if (escaped(maze, dream, target.id, centerPos, false)) {
                    shouldStop.set(true);
                    stopNightmare(casterId);
                    return;
                }
            }
            if (--remaining[0] <= 0) {
                shouldStop.set(true);
                stopNightmare(casterId);
            }
        }, 1, null, shouldStop);
    }

    public static boolean hasActiveNightmare(LivingEntity entity) {
        if(FMLEnvironment.dist == Dist.CLIENT)
            return activeNightmaresClient.containsKey(entity.getUUID());
        return activeNightmaresServer.containsKey(entity.getUUID());
    }

    public static boolean isAffectedByNightmare(LivingEntity entity) {
        if(FMLEnvironment.dist == Dist.CLIENT) {
            for(Map.Entry<UUID, NightmareCenter> entry : activeNightmaresClient.entrySet()) {
                if(entity.getUUID() == entry.getKey())
                    continue;
                if(entity.level() != entry.getValue().level())
                    continue;

                if(entity.position().distanceToSqr(entry.getValue().pos()) <= entry.getValue().radiusSquared())
                    return true;
            }

        }
        else {
            for(Map.Entry<UUID, NightmareCenter> entry : activeNightmaresServer.entrySet()) {
                if(entity.getUUID() == entry.getKey())
                    continue;
                if(entity.level() != entry.getValue().level())
                    continue;

                if(entity.position().distanceToSqr(entry.getValue().pos()) <= entry.getValue().radiusSquared())
                    return true;
            }

        }
        return false;
    }

    private boolean isBlockInRadius(Vec3 pos, UUID casterUUID) {
        if(!activeNightmaresServer.containsKey(casterUUID))
            return false;

        NightmareCenter center = activeNightmaresServer.get(casterUUID);
        return pos.distanceToSqr(center.pos()) <= center.radiusSquared();
    }

    private void createBlockSphere(Block block, ServerLevel level, Vec3 center, UUID casterUuid) {
        int radius = 3;
        BlockPos centerPos = BlockPos.containing(center);

        // Capture the sphere BEFORE modifying it so we can restore it later
        if (storedRegions.containsKey(casterUuid)) {
            storedRegions.get(casterUuid).captureSphere(level, centerPos, radius);
        }

        // Iterate through all positions in a cube around the center
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Calculate distance from center
                    double distance = Math.sqrt(x * x + y * y + z * z);

                    // Only place blocks within the sphere radius
                    if (distance <= radius) {
                        BlockPos pos = centerPos.offset(x, y, z);
                        BlockState currentState = level.getBlockState(pos);

                        // Only replace passable blocks (air, water, lava, etc.)
                        if (currentState.getCollisionShape(level, pos).isEmpty() && isBlockInRadius(pos.getCenter(), casterUuid)) {
                            level.setBlock(pos, block.defaultBlockState(), 3);
                            markNoDrop(level, pos);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        BlockPos pos = event.getPos();
        ResourceKey<Level> dimension = event.getLevel().dimension();

        if (isNoDropMarked(dimension, pos)) {
            event.getDrops().clear();
            unmarkNoDrop((ServerLevel) event.getLevel(), pos);
        }
    }

    private static void markNoDrop(ServerLevel level, BlockPos pos) {
        noDropPositions.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    private static void unmarkNoDrop(ServerLevel level, BlockPos pos) {
        HashSet<BlockPos> set = noDropPositions.get(level.dimension());
        if (set != null) set.remove(pos);
    }

    private static boolean isNoDropMarked(ResourceKey<Level> dimension, BlockPos pos) {
        HashSet<BlockPos> set = noDropPositions.get(dimension);
        return set != null && set.contains(pos);
    }

    @OnlyIn(Dist.CLIENT)
    public static void syncToClient(UUID uuid, double x, double y, double z, double radius, boolean active) {
        if(FMLEnvironment.dist != Dist.CLIENT)
            return;

        if(!active) {
            activeNightmaresClient.remove(uuid);
            return;
        }

        NightmareCenter center = new NightmareCenter(Minecraft.getInstance().level, new Vec3(x, y, z), radius * radius);
        activeNightmaresClient.put(uuid, center);
    }

    private static boolean inNightmare(LivingEntity entity) {
        NightmareCenter center = activeNightmaresServer.get(entity.getUUID());
        return center != null && entity.level() == center.level();
    }

    private static UUID dreamOf(UUID id) {
        if (dreams.containsKey(id)) return id;
        for (Map.Entry<UUID, Dream> entry : dreams.entrySet()) {
            for (Target target : entry.getValue().targets) {
                if (target.id.equals(id)) return entry.getKey();
            }
        }
        return null;
    }

    private static void syncFog(MinecraftServer server, UUID casterId, List<Target> targets, boolean active, Vec3 center, int radius) {
        if (server == null) return;
        SyncNightmareAbilityPacket packet = new SyncNightmareAbilityPacket(center.x, center.y, center.z, active ? radius : 0, active);
        ServerPlayer caster = server.getPlayerList().getPlayer(casterId);
        if (caster != null) PacketHandler.sendToPlayer(caster, packet);
        for (Target target : targets) {
            ServerPlayer pulled = server.getPlayerList().getPlayer(target.id);
            if (pulled != null && pulled != caster) PacketHandler.sendToPlayer(pulled, packet);
        }
    }

    public static boolean occupies(ServerLevel level) {
        for (Dream dream : dreams.values()) {
            if (dream.maze == level) return true;
        }
        return false;
    }

    public static boolean strikeBody(UUID session, UUID owner, DamageSource source, float amount) {
        if (owner == null || amount <= 0 || !dreams.containsKey(session)) return false;
        return stopNightmare(session, owner, source, amount);
    }

    private static boolean escaped(ServerLevel maze, Dream dream, UUID id, Vec3 center, boolean caster) {
        LivingEntity entity = find(maze.getServer(), id);
        if (entity == null) return false;
        if (!entity.isAlive() || entity.isRemoved()) return caster;
        if (entity.level() != maze) {
            Spot back = dream.casterSpot;
            for (Target target : dream.targets) {
                if (target.id.equals(id)) {
                    back = target.spot;
                    break;
                }
            }
            place(entity, maze, new Vec3(back.x, back.y + dream.yShift, back.z), back.yRot, back.xRot);
            return false;
        }
        boolean outside = entity.position().distanceToSqr(center) > (dream.radius * (double) dream.radius);
        if (!outside) return false;
        if (entity instanceof ServerPlayer && BeyonderData.getSequence(entity) <= dream.casterSeq - 1) return true;
        Vec3 back = center;
        Vec3 offset = entity.position().subtract(center);
        if (offset.lengthSqr() > 1.0E-4) back = center.add(offset.normalize().scale(dream.radius - 1));
        place(entity, maze, stand(maze, back), entity.getYRot(), entity.getXRot());
        return false;
    }

    private static void bar(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withColor(0xFFff124d)));
    }

    private static Spot spot(LivingEntity entity) {
        return new Spot(entity.level().dimension(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
    }

    private static void remember(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yrot", player.getYRot());
        tag.putFloat("xrot", player.getXRot());
        ListTag items = new ListTag();
        player.getInventory().save(items);
        tag.put("items", items);
        tag.putInt("selected", player.getInventory().selected);
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            CompoundTag carriedTag = new CompoundTag();
            carried.save(player.registryAccess(), carriedTag);
            tag.put("carried", carriedTag);
        }
        player.getPersistentData().put(RETURN_TAG, tag);
    }

    private static void restoreInventory(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(RETURN_TAG);
        if (!tag.contains("items")) return;
        player.getInventory().load(tag.getList("items", 10));
        player.getInventory().selected = tag.getInt("selected");
        ItemStack carried = tag.contains("carried")
                ? ItemStack.parse(player.registryAccess(), tag.getCompound("carried")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        player.containerMenu.setCarried(carried);
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static UUID leaveBody(LivingEntity entity, UUID session) {
        NightmareBodyEntity body = new NightmareBodyEntity(ModEntities.NIGHTMARE_BODY.get(), entity.level());
        body.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
        body.yBodyRot = entity.yBodyRot;
        body.yHeadRot = entity.yHeadRot;
        body.setSession(session);
        body.setOwner(entity.getUUID());
        if (entity instanceof ServerPlayer player) body.setSkin(player.getUUID());
        entity.level().addFreshEntity(body);
        body.holdChunk();
        return body.getUUID();
    }

    private static Vec3 stand(ServerLevel level, Vec3 preferred) {
        BlockPos pos = BlockPos.containing(preferred);
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos feet = pos.above(dy);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return dy == 0 ? preferred : new Vec3(preferred.x, feet.getY(), preferred.z);
            }
        }
        return preferred;
    }

    private static LivingEntity place(LivingEntity entity, ServerLevel level, Vec3 pos, float yRot, float xRot) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(level, pos.x, pos.y, pos.z, yRot, xRot);
            return player;
        }
        entity.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yRot, xRot);
        return entity;
    }

    private static void sendBack(MinecraftServer server, UUID id, Spot spot) {
        LivingEntity entity = find(server, id);
        if (entity == null) return;
        ServerLevel level = server.getLevel(spot.dimension);
        if (level == null) return;
        place(entity, level, new Vec3(spot.x, spot.y, spot.z), spot.yRot, spot.xRot);
        if (entity instanceof ServerPlayer player) {
            restoreInventory(player);
            if (!player.getUUID().equals(loggingOut) && player.level().dimension().equals(spot.dimension)) {
                player.getPersistentData().remove(RETURN_TAG);
            }
        }
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    private static void discard(MinecraftServer server, UUID id) {
        Entity entity = find(server, id);
        if (entity != null) entity.discard();
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        if (server == null) return null;
        LivingEntity gone = null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof LivingEntity living)) continue;
            if (!living.isRemoved() && living.isAlive()) return living;
            if (gone == null) gone = living;
        }
        return gone;
    }

    private static void fill(ServerLevel from, BlockPos anchor, ServerLevel to, BlockPos plot, int radius, boolean clear) {
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > r2) continue;
                    BlockPos dest = plot.offset(x, y, z);
                    if (dest.getY() < to.getMinBuildHeight() || dest.getY() >= to.getMaxBuildHeight()) continue;
                    if (clear) {
                        if (!to.getBlockState(dest).isAir()) to.setBlock(dest, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        continue;
                    }
                    BlockPos src = anchor.offset(x, y, z);
                    BlockState state = src.getY() < from.getMinBuildHeight() || src.getY() >= from.getMaxBuildHeight()
                            ? Blocks.AIR.defaultBlockState()
                            : from.getBlockState(src);
                    to.setBlock(dest, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        loggingOut = event.getEntity().getUUID();
        try {
            UUID caster = dreamOf(loggingOut);
            if (caster != null) stopNightmare(caster);
        } finally {
            loggingOut = null;
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RETURN_TAG) || dreamOf(player.getUUID()) != null) return;
        CompoundTag tag = data.getCompound(RETURN_TAG);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim")));
        MinecraftServer server = player.getServer();
        ServerLevel destination = server == null ? null : server.getLevel(dimension);
        if (destination != null) {
            player.teleportTo(destination, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yrot"), tag.getFloat("xrot"));
        }
        restoreInventory(player);
        data.remove(RETURN_TAG);
    }

    @SubscribeEvent
    public static void onDreamDeath(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer) || hasActiveNightmare(entity)) return;
        UUID caster = dreamOf(entity.getUUID());
        if (caster == null || event.getAmount() < entity.getHealth()) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.getUUID().equals(caster)) return;
        event.setCanceled(true);
        stopNightmare(caster);
    }

    private record Spot(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {}

    private record Target(UUID id, Spot spot, UUID body) {}

    private record Dream(ServerLevel maze, Spot casterSpot, BlockPos plot, UUID casterBody, List<Target> targets, int casterSeq, int radius, int yShift) {}

}