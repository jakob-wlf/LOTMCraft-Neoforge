package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.MarionetteComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.ParasitationComponent;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.demoness.InvisibilityAbility;
import de.jakob.lotm.beyonders.abilities.visionary.handlers.VisionaryHandler;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.entity.custom.ability_entities.ControlBodyDouble;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ObservationHandler {

    private static final Map<UUID, Map<UUID, Integer>> faceLookTicks = new ConcurrentHashMap<>();

    private static final Map<UUID, Map<UUID, Float>> decryptionProgress = new ConcurrentHashMap<>();

    private static final Map<UUID, UUID> lastDeepDecrypted = new ConcurrentHashMap<>();

    private static final Map<UUID, Float> exposure = new ConcurrentHashMap<>();

    public static final int SANITY_LOOK_TICKS = 20 * 10;

    public static final float[] DECRYPTION_THRESHOLDS = {60, 140, 240, 360, 500};

    private static final Set<String> SENSED_PATHWAYS = Set.of("justiciar", "black_emperor", "error", "wheel_of_fortune");

    public static int getValuableRadius(int sequence) {
        return switch (sequence) {
            case 9 -> 20;
            case 8 -> 24;
            case 7 -> 28;
            case 6 -> 32;
            default -> 40;
        };
    }

    public static void scanValuables(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int seq = BeyonderData.getSequence(player);
        int radius = getValuableRadius(seq);

        record Hint(Vec3 pos, float strength) {}
        List<Hint> hints = new ArrayList<>();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand))) {
            if (entity.distanceToSqr(player) > radius * radius) continue;
            if (AllyUtil.areAllies(player, entity)) continue;
            if (!canSenseThroughConcealment(player, entity, seq)) continue;

            int value = ErrorUtil.getCarriedValue(entity);
            if (value < 40 && !ErrorUtil.carriesValuables(entity)) continue;

            hints.add(new Hint(entity.position().add(0, entity.getBbHeight() * 0.5, 0), Math.min(1f, 0.35f + value / 1500f)));
        }

        int chunkRadius = (radius >> 4) + 1;
        int centerX = player.blockPosition().getX() >> 4;
        int centerZ = player.blockPosition().getZ() >> 4;
        for (int cx = centerX - chunkRadius; cx <= centerX + chunkRadius; cx++) {
            for (int cz = centerZ - chunkRadius; cz <= centerZ + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) continue;
                    if (container.getLootTable() == null) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.distSqr(player.blockPosition()) > (double) radius * radius) continue;
                    hints.add(new Hint(pos.getCenter(), 0.8f));
                }
            }
        }

        hints.sort(Comparator.comparingDouble(h -> h.pos.distanceToSqr(player.position())));

        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(12, hints.size()); i++) {
            Hint hint = hints.get(i);
            CompoundTag tag = new CompoundTag();
            tag.putDouble("x", hint.pos.x);
            tag.putDouble("y", hint.pos.y);
            tag.putDouble("z", hint.pos.z);
            tag.putFloat("s", hint.strength);
            list.add(tag);
        }

        CompoundTag data = new CompoundTag();
        data.put("hints", list);
        ErrorUtil.send(player, "valuables", data);
    }

    private static boolean canSenseThroughConcealment(ServerPlayer observer, LivingEntity target, int observerSeq) {
        boolean concealed = target.isInvisible() || target.hasEffect(MobEffects.INVISIBILITY)
                || InvisibilityAbility.invisiblePlayers.contains(target.getUUID());
        if (!concealed && !VisionaryHandler.shouldStayInvisible(observerSeq, target)) return true;

        int targetSeq = ErrorUtil.seqOf(target);
        return targetSeq >= observerSeq - 1;
    }

    public static int tickFaceLook(ServerPlayer observer, int deltaTicks, double range) {
        LivingEntity target = getLookedAtFace(observer, range);
        Map<UUID, Integer> map = faceLookTicks.computeIfAbsent(observer.getUUID(), k -> new ConcurrentHashMap<>());

        if (target == null) {
            map.replaceAll((k, v) -> Math.max(0, v - deltaTicks * 2));
            map.values().removeIf(v -> v <= 0);
            return 0;
        }

        map.replaceAll((k, v) -> k.equals(target.getUUID()) ? v : Math.max(0, v - deltaTicks));
        int ticks = map.getOrDefault(target.getUUID(), 0) + deltaTicks;
        map.put(target.getUUID(), Math.min(ticks, SANITY_LOOK_TICKS * 3));
        return ticks;
    }

    @Nullable
    public static LivingEntity getLookedAtFace(LivingEntity observer, double range) {
        LivingEntity target = AbilityUtil.getTargetEntity(observer, (int) range, 0.6f, true, true);
        if (target == null) return null;

        Vec3 toEyes = target.getEyePosition().subtract(observer.getEyePosition());
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, toEyes.normalize().dot(observer.getLookAngle())))));
        double tolerance = Math.max(4, Math.min(18, 30 / Math.max(1, toEyes.length())));
        return angle <= tolerance ? target : null;
    }

    @Nullable
    public static UUID getLastDeepDecrypted(LivingEntity observer) {
        return lastDeepDecrypted.get(observer.getUUID());
    }

    public static float getDecryptionProgress(LivingEntity observer, UUID target) {
        Map<UUID, Float> map = decryptionProgress.get(observer.getUUID());
        return map == null ? 0 : map.getOrDefault(target, 0f);
    }

    public static float getDecryptionProgress(LivingEntity observer, LivingEntity target) {
        Map<UUID, Float> map = decryptionProgress.get(observer.getUUID());
        return map == null ? 0 : map.getOrDefault(target.getUUID(), 0f);
    }

    public static int getDecryptionLevel(LivingEntity observer, LivingEntity target) {
        return getDecryptionLevel(getDecryptionProgress(observer, target));
    }

    public static int getDecryptionLevel(float progress) {
        int level = 0;
        for (float threshold : DECRYPTION_THRESHOLDS) {
            if (progress >= threshold) level++;
        }
        return level;
    }

    public static float advanceDecryption(LivingEntity observer, LivingEntity target, int observerSeq, float baseAmount) {
        int targetSeq = ErrorUtil.seqOf(target);
        float seqFactor;
        if (AbilityUtil.isTargetSignificantlyStronger(observerSeq, targetSeq)) {
            seqFactor = 0.08f;
        } else if (targetSeq < observerSeq) {
            seqFactor = (float) Math.pow(0.6, observerSeq - targetSeq);
        } else if (targetSeq == observerSeq) {
            seqFactor = 1f;
        } else {
            seqFactor = 1.5f;
        }

        float exposureFactor = 1f + Math.min(8f, exposure.getOrDefault(target.getUUID(), 0f)) * 0.25f;
        float amount = baseAmount * seqFactor * exposureFactor;

        Map<UUID, Float> map = decryptionProgress.computeIfAbsent(observer.getUUID(), k -> new ConcurrentHashMap<>());
        float progress = Math.min(DECRYPTION_THRESHOLDS[DECRYPTION_THRESHOLDS.length - 1] + 100, map.getOrDefault(target.getUUID(), 0f) + amount);
        map.put(target.getUUID(), progress);
        if (getDecryptionLevel(progress) >= 3) {
            lastDeepDecrypted.put(observer.getUUID(), target.getUUID());
        }
        return progress;
    }

    public static List<String> buildDecryptionLines(ServerLevel level, LivingEntity target, int decryptionLevel) {
        List<String> lines = new ArrayList<>();
        boolean beyonder = BeyonderData.isBeyonder(target);

        if (decryptionLevel >= 1) {
            lines.add(beyonder ? "§bBeyonder" : "§7Not a Beyonder");
        }
        if (decryptionLevel >= 2 && beyonder) {
            lines.add("§3Sequence §f" + BeyonderData.getSequence(target));
        }
        if (decryptionLevel >= 3 && beyonder) {
            String pathway = BeyonderData.getPathway(target);
            String seqName = BeyonderData.getSequenceName(pathway, BeyonderData.getSequence(target));
            lines.add("§3Pathway §f" + prettify(pathway) + " §8(" + prettify(seqName) + ")");
        }
        if (decryptionLevel >= 4) {
            LivingEntity main = ErrorUtil.getMainBody(level, target);
            if (main == null) {
                lines.add("§aTrue Body");
            } else {
                String kind = target instanceof AvatarEntity ? "Avatar" :
                        target instanceof ControlBodyDouble ? "Abandoned Body" :
                        target.getPersistentData().getBoolean("VoidSummoned") ? "Historical Projection" : "Marionette / Second Body";
                lines.add("§cNot a true body §8(" + kind + ")");
            }
        }
        if (decryptionLevel >= 5) {
            String trueName = target instanceof ServerPlayer player ? player.getGameProfile().getName() : target.getType().getDescription().getString();
            lines.add("§dTrue name §f" + trueName);
            if (target instanceof ServerPlayer player) {
                BeyonderData.getHonorificName(player).ifPresent(name -> {
                    if (!name.lines().isEmpty()) lines.add("§5Honorific §7" + String.join(" ", name.lines()));
                });
            }
        }
        return lines;
    }

    public static String prettify(String id) {
        if (id == null || id.isEmpty()) return "?";
        StringBuilder builder = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return builder.toString().trim();
    }

    @SubscribeEvent
    public static void onAbilityUsed(AbilityUsedEvent event) {
        LivingEntity user = event.getEntity();
        if (user == null) return;
        exposure.merge(user.getUUID(), 1f, Float::sum);
    }

    public static void sendConnections(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ListTag list = new ListTag();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64), Entity::isAlive)) {
            Entity controller = null;
            String type = null;

            if (entity instanceof AvatarEntity avatar && avatar.getOriginalOwner() != null) {
                controller = level.getEntity(avatar.getOriginalOwner());
                type = "avatar";
            } else if (entity instanceof ControlBodyDouble bodyDouble && bodyDouble.getOwnerUUID() != null) {
                controller = level.getEntity(bodyDouble.getOwnerUUID());
                type = "body";
            } else {
                MarionetteComponent marionette = entity.getData(ModAttachments.MARIONETTE_COMPONENT);
                if (marionette.isMarionette() && marionette.getControllerUUID() != null && !marionette.getControllerUUID().isEmpty()) {
                    try {
                        controller = level.getEntity(UUID.fromString(marionette.getControllerUUID()));
                    } catch (IllegalArgumentException ignored) {
                    }
                    type = "marionette";
                }
                ParasitationComponent parasite = entity.getData(ModAttachments.PARASITE_COMPONENT);
                if (controller == null && parasite.isParasited() && parasite.getParasiteUUID() != null) {
                    controller = level.getEntity(parasite.getParasiteUUID());
                    type = "parasite";
                }
                if (controller == null && entity.getPersistentData().getBoolean("VoidSummoned") && entity.getPersistentData().hasUUID("VoidSummonOwner")) {
                    controller = level.getEntity(entity.getPersistentData().getUUID("VoidSummonOwner"));
                    type = "summon";
                }
            }

            if (type == null) continue;

            CompoundTag tag = new CompoundTag();
            tag.putInt("a", entity.getId());
            tag.putInt("b", controller == null ? -1 : controller.getId());
            tag.putString("t", type);
            list.add(tag);
        }

        CompoundTag data = new CompoundTag();
        data.put("links", list);
        ErrorUtil.send(player, "connections", data);
    }

    public static void sendApproachingThreats(ServerPlayer player) {
        ListTag list = new ListTag();
        double range = 160;

        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !BeyonderData.isBeyonder(other)) continue;
            if (AllyUtil.areAllies(player, other)) continue;
            String pathway = BeyonderData.getPathway(other);
            if (!SENSED_PATHWAYS.contains(pathway)) continue;
            if (BeyonderData.getSequence(other) > 4) continue;

            double distance = other.distanceTo(player);
            if (distance > range) continue;

            CompoundTag tag = new CompoundTag();
            tag.putString("p", pathway);
            tag.putFloat("d", (float) (distance / range));
            list.add(tag);
        }

        CompoundTag data = new CompoundTag();
        data.put("threats", list);
        ErrorUtil.send(player, "threats", data);
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer().level().isClientSide) return;
        ErrorUtil.markOwner(event.getEntity().getItem(), event.getPlayer());
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity.level().isClientSide) return;
        Entity owner = itemEntity.getOwner();
        if (owner instanceof LivingEntity living && living != event.getPlayer()) {
            ErrorUtil.markOwner(itemEntity.getItem(), living);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        exposure.replaceAll((k, v) -> v * 0.85f);
        exposure.values().removeIf(v -> v < 0.05f);
    }

    public static void clear(UUID observer) {
        faceLookTicks.remove(observer);
    }
}
