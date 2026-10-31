package de.jakob.lotm.beyonders.abilities.darkness;

import com.google.common.util.concurrent.AtomicDouble;
import com.zigythebird.playeranimcore.math.Vec3f;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.FogComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbilityHandler;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.ControllingUtils;
import de.jakob.lotm.beyonders.abilities.fool.passives.DangerPremonitionAbility;
import de.jakob.lotm.beyonders.abilities.visionary.PsychologicalInvisibilityAbility;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.OpenConcealSecretScreenPacket;
import de.jakob.lotm.network.packets.toClient.SyncConcealmentVeilsPacket;
import de.jakob.lotm.rendering.effectRendering.EffectIds;
import de.jakob.lotm.rendering.effectRendering.EffectManager;
import de.jakob.lotm.sound.ModSounds;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ConcealmentAbility extends SelectableAbility {
    public ConcealmentAbility(String id) {
        super(id, 5);
        this.canBeCopied = false;
        canBeReplicated = false;
        canBeUsedInArtifact = false;
        autoClear = false;
        cannotBeStolen = true;
        canBeShared = false;
        canBeUsedByNPC = false;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(3, 4, 6));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(18000f, 10000f, 6500f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 2));
    }

    @Override
    protected float getSpiritualityCost() {
        return 6000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.concealment.conceal_others",
                "ability.lotmcraft.concealment.conceal_area",
                "ability.lotmcraft.concealment.conceal_ground",
                "ability.lotmcraft.concealment.conceal_secrets",
                "ability.lotmcraft.concealment.reverse",
                "ability.lotmcraft.concealment.presence_of_peace",
                "ability.lotmcraft.concealment.possess_projection"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (!(entity instanceof Player)) abilityIndex = 0;
        switch (abilityIndex) {
            case 0 -> concealOthers(level, entity);
            case 1 -> concealArea(level, entity, false);
            case 2 -> concealArea(level, entity, true);
            case 3 -> concealSecrets(entity);
            case 4 -> reverse(level, entity);
            case 5 -> presenceOfPeace(entity);
            case 6 -> possessProjection(entity);
        }
    }

    public void enterConcealedArea(Level level, LivingEntity entity) {
        if (BeyonderData.getSequence(entity) > 0) return;
        if(!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Only works for server players
        if(!(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if(BeyonderData.getSpirituality(entity) <= BeyonderData.
                getMaxSpirituality(BeyonderData.getPathway(entity),
                        BeyonderData.getSequence(entity)) * 0.4f){
            return;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "concealment_world"));
        ServerLevel concealedLevel = serverLevel.getServer().getLevel(dimension);
        if (concealedLevel == null) {
            return;
        }

        // Check if player is currently in the concealed world
        boolean isInConcealedWorld = serverLevel.dimension().equals(dimension);

        ServerLevel targetLevel;
        if (isInConcealedWorld) {
            // Teleport back to overworld
            targetLevel = serverLevel.getServer().getLevel(Level.OVERWORLD);
        } else {
            // Teleport to concealed world
            targetLevel = concealedLevel;
        }

        if (targetLevel == null) {
            return;
        }

        BlockPos currentPos = entity.blockPosition();
        double x = entity.getX();
        double z = entity.getZ();
        int startY = currentPos.getY();

        BlockPos safePos = findSafePosition(targetLevel, x, startY, z, isInConcealedWorld);

        serverPlayer.teleportTo(targetLevel,
                safePos.getX() + 0.5,
                safePos.getY(),
                safePos.getZ() + 0.5,
                serverPlayer.getYRot(),
                serverPlayer.getXRot()
        );

        this.clearArtifactScaling(entity);
    }

    private BlockPos findSafePosition(ServerLevel level, double x, int startY, double z, boolean searchUpFirst) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        int searchY = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 3, startY));

        if (searchUpFirst) {
            for (int y = searchY; y < level.getMaxBuildHeight() - 2; y++) {
                BlockPos checkPos = new BlockPos(blockX, y, blockZ);
                BlockPos belowPos = checkPos.below();
                BlockPos abovePos = checkPos.above();

                if (isSafePosition(level, checkPos, belowPos, abovePos)) {
                    return checkPos;
                }
            }

            for (int y = searchY - 1; y >= level.getMinBuildHeight() + 1; y--) {
                BlockPos checkPos = new BlockPos(blockX, y, blockZ);
                BlockPos belowPos = checkPos.below();
                BlockPos abovePos = checkPos.above();

                if (isSafePosition(level, checkPos, belowPos, abovePos)) {
                    return checkPos;
                }
            }
        } else {
            for (int y = searchY; y >= level.getMinBuildHeight() + 1; y--) {
                BlockPos checkPos = new BlockPos(blockX, y, blockZ);
                BlockPos belowPos = checkPos.below();
                BlockPos abovePos = checkPos.above();

                if (isSafePosition(level, checkPos, belowPos, abovePos)) {
                    return checkPos;
                }
            }

            for (int y = searchY + 1; y < level.getMaxBuildHeight() - 2; y++) {
                BlockPos checkPos = new BlockPos(blockX, y, blockZ);
                BlockPos belowPos = checkPos.below();
                BlockPos abovePos = checkPos.above();

                if (isSafePosition(level, checkPos, belowPos, abovePos)) {
                    return checkPos;
                }
            }
        }

        return findHighestSafePosition(level, blockX, blockZ);
    }

    private boolean isSafePosition(ServerLevel level, BlockPos pos, BlockPos below, BlockPos above) {
        BlockState belowState = level.getBlockState(below);
        BlockState currentState = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(above);

        // Need solid ground below
        boolean hasSolidGround = !belowState.isAir() &&
                belowState.isSolidRender(level, below) &&
                belowState.isCollisionShapeFullBlock(level, below);

        // Need air at position and above (2 blocks of air for player)
        boolean hasSpace = currentState.isAir() && aboveState.isAir();

        return hasSolidGround && hasSpace;
    }

    private BlockPos findHighestSafePosition(ServerLevel level, int x, int z) {
        // Search from top down for the first solid block
        for (int y = level.getMaxBuildHeight() - 3; y >= level.getMinBuildHeight(); y--) {
            BlockPos checkPos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(checkPos);

            if (!state.isAir() && state.isSolidRender(level, checkPos)) {
                // Found solid ground, check if there's space above
                BlockPos teleportPos = checkPos.above();
                BlockPos twoAbove = teleportPos.above();

                if (level.getBlockState(teleportPos).isAir() &&
                        level.getBlockState(twoAbove).isAir()) {
                    return teleportPos;
                }
            }
        }

        return new BlockPos(x, 65, z);
    }

    public void concealSurroundings(Level level, LivingEntity entity) {
        if (BeyonderData.getSequence(entity) > 0) return;
        if(!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean isInConcealmentWorld = serverLevel.dimension().equals(ModDimensions.CONCEALMENT_WORLD_DIMENSION_KEY);

        ServerLevel destinationLevel;

        if (isInConcealmentWorld) {
            destinationLevel = serverLevel.getServer().getLevel(Level.OVERWORLD);
        } else {
            destinationLevel = serverLevel.getServer().getLevel(ModDimensions.CONCEALMENT_WORLD_DIMENSION_KEY);
        }

        if(destinationLevel == null) {
            return;
        }

        if(BeyonderData.getSpirituality(entity) <= BeyonderData.
                getMaxSpirituality(BeyonderData.getPathway(entity),
                        BeyonderData.getSequence(entity)) * 0.4f){
            return;
        }

        EffectManager.playEffect(EffectIds.CONCEALMENT, entity.getX(), entity.getY(), entity.getZ(), serverLevel, entity);

        AtomicDouble radius = new AtomicDouble(2 * (int) (Math.max(multiplier(entity)/2,1)));
        Vec3 finalTargetLoc = entity.position();

        boolean isDarkness0 = BeyonderData.getSequence(entity) == 0 && BeyonderData.getPathway(entity).equals("darkness");

        final HashSet<BlockPos> processedBlocks = new HashSet<>();

        ServerScheduler.scheduleForDuration(0, 2, 20 * 5* (int) (Math.max(multiplier(entity)/2,1)), () -> {
            if(BeyonderData.isGriefingEnabled(entity)) {
                AbilityUtil.getBlocksInSphereRadius(serverLevel, finalTargetLoc, radius.get(), true, false, false).forEach(blockPos -> {
                    if(blockPos.getY() < entity.position().y) return;

                    if(processedBlocks.contains(blockPos)) return;

                    BlockState blockState = serverLevel.getBlockState(blockPos);

                    if(blockState.getDestroySpeed(serverLevel, blockPos) < 0) {
                        processedBlocks.add(blockPos);
                        return;
                    }

                    if(blockState.isAir()) {
                        processedBlocks.add(blockPos);
                        return;
                    }

                    destinationLevel.setBlockAndUpdate(blockPos, blockState);

                    serverLevel.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());

                    processedBlocks.add(blockPos);
                });
            }

            AbilityUtil.getNearbyEntities(entity, serverLevel, finalTargetLoc, radius.get()).forEach(targetEntity -> {
                int entitySeq = AbilityUtil.getSeqWithArt(entity, this);

                if(AbilityUtil.isTargetSignificantlyStronger(entitySeq, BeyonderData.getSequence(targetEntity))) return;

                if(targetEntity.getPersistentData().contains("recently_concealed") && targetEntity.getPersistentData().getBoolean("recently_concealed")) return;

                Vec3 originalPos = targetEntity.position();

                BlockPos safePos = findSafePosition(destinationLevel, targetEntity.getX(), targetEntity.blockPosition().getY(), targetEntity.getZ(), false);

                //TemporaryChunkLoader.forceChunksTemporarily(destinationLevel, safePos.getX(), safePos.getZ(), 10, 20 * 10);

                targetEntity.teleportTo(destinationLevel,
                        safePos.getX() + 0.5,
                        safePos.getY(),
                        safePos.getZ() + 0.5,
                        Set.of(),
                        targetEntity.getYRot(),
                        targetEntity.getXRot()
                );

                targetEntity.getPersistentData().putBoolean("recently_concealed", true);

                LivingEntity teleportedEntity = (LivingEntity) destinationLevel.getEntity(targetEntity.getUUID());

                if(teleportedEntity == null) return;

                if(AbilityUtil.isTargetSignificantlyWeaker(entitySeq, BeyonderData.getSequence(teleportedEntity))) {
                    teleportedEntity.setHealth(1);
                    if(!(targetEntity instanceof Player))
                        return;
                }

//                if(targetEntity instanceof ServerPlayer playerTarget){
//                    if(BeyonderData.getPathway(playerTarget).equals("door") && BeyonderData.getSequence(playerTarget) == 1){
//                        if(isDarkness0){
//                            Seq0.affected.add(playerTarget.getUUID());
//                        }
//                    }
//                }

                int returnTime = AbilityUtil.isTargetSignificantlyWeaker(entitySeq, BeyonderData.getSequence(teleportedEntity)) ? 20 * 60 * 2 :
                        BeyonderData.getSequence(teleportedEntity) < entitySeq ? 20 * 5 : 20 * 25;

                ServerScheduler.scheduleDelayed(returnTime, () -> {
                    teleportedEntity.teleportTo(serverLevel,
                            originalPos.x() + 0.5,
                            originalPos.y(),
                            originalPos.z() + 0.5,
                            Set.of(),
                            teleportedEntity.getYRot(),
                            teleportedEntity.getXRot()
                    );
                });

                ServerScheduler.scheduleDelayed(returnTime + 20 * 8, () -> {
                    if (teleportedEntity.getPersistentData().contains("recently_concealed"))
                        teleportedEntity.getPersistentData().putBoolean("recently_concealed", false);
                });
            });

            radius.addAndGet(0.5);
        }, () -> this.clearArtifactScaling(entity), serverLevel, () -> AbilityUtil.getTimeInArea(entity, new Location(finalTargetLoc, serverLevel)));
    }

    private static final HashSet<UUID> thoughtconcealedEntities = new HashSet<>();
    public void concealThoughts(Level level, LivingEntity entity) {
        if (BeyonderData.getSequence(entity) > 0) return;
        if(!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if(level.isClientSide)
            return;

        float multiplier = multiplier(entity);
        level.playSound(null, BlockPos.containing(entity.position()), ModSounds.MIDNIGHT_POEM.get(), SoundSource.BLOCKS, 1, 1);
        LivingEntity targetEntity = AbilityUtil.getTargetEntity(entity, 16*(int) Math.max(multiplier/2,1), 2);
        if(targetEntity == null)
            return;

        if(thoughtconcealedEntities.contains(targetEntity.getUUID())) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("Thoughts of the target are already concealed.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }
        thoughtconcealedEntities.add(targetEntity.getUUID());
        int duration = 0;

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this);
        int targetEntitySeq = BeyonderData.getSequence(targetEntity);
        if(entitySeq < targetEntitySeq && !BeyonderData.getPathway(targetEntity).equals("darkness")) {
            duration = 20;
        }else if (entitySeq > targetEntitySeq){
            duration = 20 * 10;
        }else{
            duration = 20 * 4;
        }

        if(!BeyonderData.isBeyonder(targetEntity) || targetEntitySeq > entitySeq-1 ) {
            if(targetEntity instanceof Mob) {
                ((Mob) targetEntity).setNoAi(true);
                ServerScheduler.scheduleDelayed(duration, () -> ((Mob) targetEntity).setNoAi(false));
            }
            if(BeyonderData.isBeyonder(targetEntity)) {
                AbilityUtil.sealAllTargetAbilities(entity, targetEntity, "cogitation", duration);
            }
        }

        Location loc = new Location(targetEntity.position(), targetEntity.level());

        int finalDuration = duration;

        ServerScheduler.scheduleForDuration(0, 5,duration, () -> {
            targetEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 10, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 10, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 4, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 5, false, false, false));
            targetEntity.setOnGround(true);
            var pos = targetEntity.position();
            targetEntity.setDeltaMovement(new Vec3(0, 0, 0));
            targetEntity.hurtMarked = true;

            targetEntity.teleportTo(pos.x, pos.y, pos.z);

            loc.setLevel(targetEntity.level());
            loc.setPosition(targetEntity.position());
        }, null, (ServerLevel) level, () -> AbilityUtil.getTimeInArea(entity, new Location(entity.position(), level)));


        ServerScheduler.scheduleDelayed(duration, () -> thoughtconcealedEntities.remove(targetEntity.getUUID()));
    }

    private record Veil(UUID caster, ResourceKey<Level> dimension, double x, double y, double z, double radius, int casterSeq, boolean ground, long until) {}

    private record Secret(UUID caster, int casterSeq, ResourceKey<Level> dimension, double x, double z, String word) {}

    private record Mark(UUID caster, int casterSeq, long until) {}

    private static final List<Veil> veils = new ArrayList<>();
    private static final Map<UUID, Mark> concealedOthers = new HashMap<>();
    private static final Set<UUID> peaceful = new HashSet<>();
    private static final List<Secret> secrets = new ArrayList<>();
    private static final Map<UUID, UUID> pendingProjections = new HashMap<>();
    private static final int GROUND_DROP = 16;
    private static final int GROUND_TICKS = 20 * 30;
    private static final int SECRET_REACH = 500;
    private static final int SECRET_REACH_SEQ1 = 5000;

    public static boolean sees(LivingEntity entity) {
        PassiveAbility sense = PassiveAbilityHandler.getById("concealment_sense_ability");
        if (sense != null && sense.shouldApplyTo(entity)) return true;
        if (hiddenFromHarm(entity)) return true;
        for (Veil veil : veils) {
            if (veil.caster.equals(entity.getUUID())) return true;
        }
        return false;
    }

    public static boolean peaceful(LivingEntity entity) {
        return entity != null && peaceful.contains(entity.getUUID());
    }

    public static boolean hiddenFromHarm(LivingEntity entity) {
        Mark mark = concealedOthers.get(entity.getUUID());
        if (mark == null || entity.level().getServer() == null) return false;
        return entity.level().getServer().overworld().getGameTime() < mark.until;
    }

    public static void sealSecret(ServerPlayer player, String word) {
        if (word == null) return;
        String trimmed = word.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) return;
        secrets.add(new Secret(player.getUUID(), BeyonderData.getSequence(player), player.level().dimension(), player.getX(), player.getZ(), trimmed));
        bar(player, "That word is concealed.");
    }

    public static void releaseSecret(ServerPlayer player, int index) {
        if (index < 0) return;
        int seen = 0;
        for (int i = 0; i < secrets.size(); i++) {
            if (!secrets.get(i).caster.equals(player.getUUID())) continue;
            if (seen == index) {
                secrets.remove(i);
                bar(player, "That secret is revealed.");
                return;
            }
            seen++;
        }
    }

    public static void projectionSummoned(ServerLevel level, ServerPlayer summoner, BeyonderNPCEntity npc) {
        UUID ownerId = npc.getTargetPlayerUUID().orElse(null);
        if (ownerId == null || level.getServer() == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return;
        PassiveAbility sense = PassiveAbilityHandler.getById("concealment_sense_ability");
        if (sense == null || !sense.shouldApplyTo(owner)) return;
        pendingProjections.put(ownerId, npc.getUUID());
        owner.sendSystemMessage(Component.translatable("lotmcraft.concealment.projection_prayer", summoner.getName()).withColor(0xFF3300b5));
    }

    private void concealOthers(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        LivingEntity target = AbilityUtil.getTargetEntity(entity, 16 * (int) Math.max(multiplier(entity) / 2, 1), 2);
        if (target == null) {
            if (entity instanceof ServerPlayer player) bar(player, "No target in sight.");
            return;
        }
        int casterSeq = AbilityUtil.getSeqWithArt(entity, this);
        int targetSeq = BeyonderData.getSequence(target);
        int duration;
        if (casterSeq < targetSeq && !"darkness".equals(BeyonderData.getPathway(target))) duration = 20;
        else if (casterSeq > targetSeq) duration = 20 * 10;
        else duration = 20 * 4;
        long until = serverLevel.getServer().overworld().getGameTime() + duration;
        concealedOthers.put(target.getUUID(), new Mark(entity.getUUID(), casterSeq, until));
        if (target instanceof ServerPlayer playerTarget) {
            playerTarget.getData(ModAttachments.FOG_COMPONENT.get()).concealFor(duration * 50L, playerTarget);
            bar(playerTarget, "Darkness covers your eyes.");
        }
        if (entity instanceof ServerPlayer player) bar(player, "They are concealed.");
    }

    private void concealArea(Level level, LivingEntity entity, boolean ground) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel)) return;
        boolean removed = veils.removeIf(veil -> veil.caster.equals(player.getUUID()) && veil.ground == ground);
        if (removed) {
            syncVeils(player.server);
            bar(player, "The concealment lifts.");
            return;
        }
        int seq = AbilityUtil.getSeqWithArt(entity, this);
        double radius = ground ? 4 : 10 + Math.max(0, 2 - seq) * 4.0;
        long until = ground ? ((ServerLevel) level).getServer().overworld().getGameTime() + GROUND_TICKS : Long.MAX_VALUE;
        veils.add(new Veil(player.getUUID(), level.dimension(), player.getX(), player.getY(), player.getZ(), radius, seq, ground, until));
        syncVeils(player.server);
        bar(player, ground ? "The ground gives way." : "The area slips out of sight.");
    }

    private void concealSecrets(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        ArrayList<String> words = new ArrayList<>();
        for (Secret secret : secrets) {
            if (secret.caster.equals(player.getUUID())) words.add(secret.word);
        }
        PacketHandler.sendToPlayer(player, new OpenConcealSecretScreenPacket(words));
    }

    private void reverse(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        int seq = AbilityUtil.getSeqWithArt(entity, this);
        LivingEntity target = AbilityUtil.getTargetEntity(entity, 16, 2);
        if (target != null && hiddenFromHarm(target)) {
            Mark mark = concealedOthers.get(target.getUUID());
            if (mark != null && !canErase(player, seq, mark.caster, mark.casterSeq)) {
                bar(player, "Their concealment holds.");
                return;
            }
            clearOthers(target);
            bar(player, "Their concealment is gone.");
            return;
        }
        boolean erased = false;
        boolean held = false;
        var veilsLeft = veils.iterator();
        while (veilsLeft.hasNext()) {
            Veil veil = veilsLeft.next();
            if (!veil.dimension.equals(player.level().dimension()) || !inside(player, veil)) continue;
            if (!canErase(player, seq, veil.caster, veil.casterSeq)) {
                held = true;
                continue;
            }
            veilsLeft.remove();
            erased = true;
        }
        var secretsLeft = secrets.iterator();
        while (secretsLeft.hasNext()) {
            Secret secretEntry = secretsLeft.next();
            if (!secretEntry.caster.equals(player.getUUID()) || !secretCovers(secretEntry, player)) continue;
            if (!canErase(player, seq, secretEntry.caster, secretEntry.casterSeq)) {
                held = true;
                continue;
            }
            secretsLeft.remove();
            erased = true;
        }
        if (erased) bar(player, "The concealment is erased.");
        else if (held) bar(player, "Their concealment holds.");
        else bar(player, "Nothing here is concealed.");
    }

    private static boolean canErase(ServerPlayer player, int seq, UUID caster, int casterSeq) {
        return caster.equals(player.getUUID()) || casterSeq >= seq;
    }

    private void presenceOfPeace(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (peaceful.contains(player.getUUID())) {
            endPeace(player);
            return;
        }
        peaceful.add(player.getUUID());
        PsychologicalInvisibilityAbility.addInvisFromOtherSkills(player, BeyonderData.getSequence(player));
        bar(player, "A quiet presence settles.");
    }

    private static void endPeace(LivingEntity entity) {
        if (!peaceful.remove(entity.getUUID())) return;
        PsychologicalInvisibilityAbility.removeInvisFromOtherSkills(entity);
        entity.setInvisible(false);
        if (entity instanceof ServerPlayer player) bar(player, "The quiet presence fades.");
    }

    private void possessProjection(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || player.getServer() == null) return;
        UUID npcId = pendingProjections.get(player.getUUID());
        if (npcId == null) {
            bar(player, "No projection answers.");
            return;
        }
        BeyonderNPCEntity npc = findProjection(player.getServer(), npcId);
        if (npc == null || !npc.isAlive()) {
            pendingProjections.remove(player.getUUID());
            bar(player, "No projection answers.");
            return;
        }
        if (npc.level() != player.level() && npc.level() instanceof ServerLevel npcLevel) {
            player.teleportTo(npcLevel, npc.getX(), npc.getY(), npc.getZ(), player.getYRot(), player.getXRot());
        }
        if (!ControllingUtils.startControlling(player, npc, true, true)) {
            bar(player, "The projection does not answer.");
            return;
        }
        pendingProjections.remove(player.getUUID());
    }

    private static BeyonderNPCEntity findProjection(MinecraftServer server, UUID npcId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity found = level.getEntity(npcId);
            if (found instanceof BeyonderNPCEntity npc) return npc;
        }
        return null;
    }

    private static void clearOthers(LivingEntity target) {
        concealedOthers.remove(target.getUUID());
        if (target instanceof ServerPlayer player) {
            player.getData(ModAttachments.FOG_COMPONENT.get()).clearFog(player);
        }
    }

    private static boolean canPass(LivingEntity entity, Veil veil) {
        if (entity.getUUID().equals(veil.caster)) return true;
        if (!veil.ground) {
            return "darkness".equals(BeyonderData.getPathway(entity)) && BeyonderData.getSequence(entity) < veil.casterSeq;
        }
        if (sees(entity)) return true;
        return BeyonderData.getSequence(entity) <= veil.casterSeq - 1;
    }

    private static boolean inside(LivingEntity entity, Veil veil) {
        if (!veil.dimension.equals(entity.level().dimension())) return false;
        double dx = entity.getX() - veil.x;
        double dy = entity.getY() - veil.y;
        double dz = entity.getZ() - veil.z;
        return dx * dx + dy * dy + dz * dz <= veil.radius * veil.radius;
    }

    private static boolean locked(Player player) {
        return player instanceof LivingEntity living && hiddenFromHarm(living);
    }

    private static void bar(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withColor(0xFF3300b5)));
    }

    private static void holdBack(LivingEntity entity) {
        for (Veil veil : veils) {
            if (veil.ground || !inside(entity, veil) || canPass(entity, veil)) continue;
            double dx = entity.getX() - veil.x;
            double dy = entity.getY() - veil.y;
            double dz = entity.getZ() - veil.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double scale = dist < 0.01 ? veil.radius + 0.6 : (veil.radius + 0.6) / dist;
            if (dist < 0.01) entity.teleportTo(veil.x + veil.radius + 0.6, entity.getY(), veil.z);
            else entity.teleportTo(veil.x + dx * scale, veil.y + dy * scale, veil.z + dz * scale);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;
            return;
        }
    }

    private static void dropFloor(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (Veil veil : veils) {
            if (!veil.ground || canPass(entity, veil)) continue;
            double dx = entity.getX() - veil.x;
            double dz = entity.getZ() - veil.z;
            if (!veil.dimension.equals(level.dimension()) || dx * dx + dz * dz > veil.radius * veil.radius) continue;
            if (Math.abs(entity.getY() - veil.y) > 1.5) continue;
            int x = entity.getBlockX();
            int z = entity.getBlockZ();
            int y = entity.getBlockY() - GROUND_DROP;
            int min = Math.max(level.getMinBuildHeight() + 1, entity.getBlockY() - 12);
            boolean landed = false;
            for (; y >= min; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && !level.getBlockState(feet.below()).isAir()) {
                    landed = true;
                    break;
                }
            }
            if (!landed) y = entity.getBlockY() - GROUND_DROP;
            entity.teleportTo(entity.getX(), y, entity.getZ());
            entity.setDeltaMovement(0, -0.4, 0);
            entity.hurtMarked = true;
            return;
        }
    }

    private static void calm(ServerPlayer player) {
        if (!peaceful.contains(player.getUUID()) || player.tickCount % 10 != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        List<LivingEntity> nearby = AbilityUtil.getNearbyEntities(player, level, player.position(), 24);
        int lookers = 0;
        for (LivingEntity other : nearby) {
            if (other != player && Boolean.TRUE.equals(DangerPremonitionAbility.isThreatLookingAtUser(player, other, 24))) lookers++;
        }
        if (lookers <= 0) return;
        float chance = Math.min(0.85f, lookers * 0.2f);
        for (LivingEntity other : nearby) {
            if (other instanceof Mob mob && mob.getTarget() == player && mob.getRandom().nextFloat() < chance) {
                mob.setTarget(null);
            }
        }
    }

    private static void syncVeils(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ArrayList<Double> packed = new ArrayList<>();
            boolean darkness = "darkness".equals(BeyonderData.getPathway(player));
            int seq = BeyonderData.getSequence(player);
            boolean blind = !sees(player);
            for (Veil veil : veils) {
                if (!veil.dimension.equals(player.level().dimension())) continue;
                boolean own = veil.caster.equals(player.getUUID());
                boolean higher = !veil.ground && darkness && seq < veil.casterSeq;
                boolean lower = !veil.ground && darkness && seq > veil.casterSeq;
                boolean hide = veil.ground || !darkness ? blind : lower;
                double look = !veil.ground && own ? 0.5 : lower ? 1.0 : higher ? 0.5 : 0.0;
                packed.add(veil.x);
                packed.add(veil.y);
                packed.add(veil.z);
                packed.add(veil.radius);
                packed.add(hide ? 1.0 : 0.0);
                packed.add(veil.ground ? 1.0 : 0.0);
                packed.add(look);
            }
            PacketHandler.sendToPlayer(player, new SyncConcealmentVeilsPacket(packed));
        }
    }

    private static void shoveMobs(MinecraftServer server) {
        for (Veil veil : List.copyOf(veils)) {
            ServerLevel level = server.getLevel(veil.dimension);
            if (level == null) continue;
            AABB box = new AABB(veil.x - veil.radius, veil.y - veil.radius, veil.z - veil.radius, veil.x + veil.radius, veil.y + veil.radius, veil.z + veil.radius);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, living -> !(living instanceof Player))) {
                if (veil.ground) dropFloor(entity);
                else holdBack(entity);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        holdBack(player);
        dropFloor(player);
        calm(player);
        if (peaceful.contains(player.getUUID())) {
            player.setInvisible(true);
            if (!PsychologicalInvisibilityAbility.finalInvisiblePlayers.containsKey(player.getUUID())) {
                PsychologicalInvisibilityAbility.addInvisFromOtherSkills(player, BeyonderData.getSequence(player));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getServer() == null) return;
        if (level != level.getServer().overworld()) return;
        long time = level.getGameTime();
        veils.removeIf(veil -> {
            if (!veil.ground || veil.until > time) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(veil.caster);
            if (caster != null) bar(caster, "The ground settles.");
            return true;
        });
        concealedOthers.entrySet().removeIf(entry -> {
            if (entry.getValue().until > time) return false;
            ServerPlayer concealed = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (concealed != null) concealed.getData(ModAttachments.FOG_COMPONENT.get()).clearFog(concealed);
            return true;
        });
        if (time % 5 == 0) shoveMobs(level.getServer());
        if (time % 20 == 0) syncVeils(level.getServer());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && locked(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeft(PlayerInteractEvent.LeftClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRight(PlayerInteractEvent.RightClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (hiddenFromHarm(event.getEntity())) {
            event.setCanceled(true);
            event.setAmount(0);
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == event.getEntity()) return;
        if (crossesArea(attacker, event.getEntity())) {
            event.setCanceled(true);
            event.setAmount(0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPeaceStrike(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker) endPeace(attacker);
    }

    private static boolean secretCovers(Secret secret, ServerPlayer player) {
        if (secret.casterSeq <= 0) return true;
        if (!secret.dimension.equals(player.level().dimension())) return false;
        int reach = secret.casterSeq == 1 ? SECRET_REACH_SEQ1 : SECRET_REACH;
        return Math.abs(player.getX() - secret.x) <= reach && Math.abs(player.getZ() - secret.z) <= reach;
    }

    private static boolean crossesArea(LivingEntity attacker, LivingEntity victim) {
        for (Veil veil : veils) {
            if (veil.ground) continue;
            if (inside(attacker, veil) != inside(victim, veil)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String raw = event.getMessage().getString();
        String next = raw;
        ServerPlayer speaker = event.getPlayer();
        for (Secret secret : secrets) {
            if (!secretCovers(secret, speaker)) continue;
            next = next.replaceAll("(?i)" + Pattern.quote(secret.word), "█████");
        }
        if (!next.equals(raw)) event.setMessage(Component.literal(next));
    }

    @SubscribeEvent
    public static void onPeaceParticles(EffectParticleModificationEvent event) {
        if (peaceful(event.getEntity())) event.setVisible(false);
    }

    @SubscribeEvent
    public static void onPeaceTarget(LivingChangeTargetEvent event) {
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next != null && peaceful(next)) event.setCanceled(true);
    }

}