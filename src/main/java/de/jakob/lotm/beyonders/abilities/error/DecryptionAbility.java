package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.darkness.NightmareAbility;
import de.jakob.lotm.beyonders.abilities.demoness.InvisibilityAbility;
import de.jakob.lotm.beyonders.abilities.demoness.ShadowConcealmentAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.ObservationHandler;
import de.jakob.lotm.beyonders.abilities.visionary.handlers.VisionaryHandler;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.SyncDecryptionLookedAtEntitiesAbilityPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DecryptionAbility extends ToggleAbility {

    private static final Random RANDOM = new Random();

    private static final Map<UUID, Map.Entry<UUID, Integer>> heldMarkedItems = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> traceCooldown = new ConcurrentHashMap<>();

    private static final int TRACE_TICKS = 20 * 5;

    public DecryptionAbility(String id) {
        super(id);

        canBeUsedByNPC = false;
        autoClear = false;
        doesNotIncreaseDigestion = true;
        canBeShared = false;
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.decryptionActive = true;
            entity.playSound(SoundEvents.BELL_RESONATE, 1, 1);
            return;
        }

        if (entity instanceof ServerPlayer player) {
            PacketHandler.sendToPlayer(player, new SyncDecryptionLookedAtEntitiesAbilityPacket(true, -1));
        }
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel))
            return;

        int seq = AbilityUtil.getSeqWithArt(entity, this);

        LivingEntity lookedAt = AbilityUtil.getTargetEntity(entity, 40, 1.2f, true, true, true, false);

        if (lookedAt != null && VisionaryHandler.shouldStayInvisible(seq, lookedAt)) {
            lookedAt = null;
        }

        PacketHandler.sendToPlayer(player, new SyncDecryptionLookedAtEntitiesAbilityPacket(true, lookedAt == null ? -1 : lookedAt.getId()));

        if (lookedAt != null) {
            float progress = ObservationHandler.advanceDecryption(player, lookedAt, seq, tickRate);
            sendInfo(serverLevel, player, lookedAt, progress);
        }

        revealLowLevelIllusions(serverLevel, player, seq);
        traceMarkedItem(serverLevel, player, seq);

        entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 17, 1, false, false, false));
    }

    private void sendInfo(ServerLevel level, ServerPlayer player, LivingEntity target, float progress) {
        int decryptionLevel = ObservationHandler.getDecryptionLevel(progress);
        float[] thresholds = ObservationHandler.DECRYPTION_THRESHOLDS;
        float previous = decryptionLevel == 0 ? 0 : thresholds[decryptionLevel - 1];
        float next = decryptionLevel >= thresholds.length ? previous : thresholds[decryptionLevel];
        float fraction = next <= previous ? 1f : (progress - previous) / (next - previous);

        CompoundTag data = new CompoundTag();
        data.putInt("id", target.getId());
        data.putInt("level", decryptionLevel);
        data.putFloat("fraction", Math.max(0, Math.min(1, fraction)));
        ListTag lines = new ListTag();
        for (String line : ObservationHandler.buildDecryptionLines(level, target, decryptionLevel)) {
            lines.add(StringTag.valueOf(line));
        }
        data.put("lines", lines);
        ErrorUtil.send(player, "decryption_info", data);
    }

    private void revealLowLevelIllusions(ServerLevel level, ServerPlayer player, int seq) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24), e -> e != player && e.isAlive())) {
            boolean hidden = entity.isInvisible()
                    || InvisibilityAbility.invisiblePlayers.contains(entity.getUUID())
                    || ShadowConcealmentAbility.invisiblePlayers.contains(entity.getUUID());
            if (!hidden) continue;

            int targetSeq = ErrorUtil.seqOf(entity);
            float chance = targetSeq >= seq ? 0.35f : (targetSeq == seq - 1 ? 0.1f : 0f);
            if (RANDOM.nextFloat() >= chance) continue;

            CompoundTag data = new CompoundTag();
            data.putInt("id", entity.getId());
            data.putInt("ticks", 40);
            ErrorUtil.send(player, "reveal", data);
        }
    }

    private void traceMarkedItem(ServerLevel level, ServerPlayer player, int seq) {
        ItemStack held = player.getMainHandItem();
        UUID owner = ErrorUtil.getMarkedOwner(held);

        if (owner == null || owner.equals(player.getUUID())) {
            heldMarkedItems.remove(player.getUUID());
            return;
        }

        Map.Entry<UUID, Integer> entry = heldMarkedItems.get(player.getUUID());
        int ticks = entry != null && entry.getKey().equals(owner) ? entry.getValue() + tickRate : tickRate;
        heldMarkedItems.put(player.getUUID(), new AbstractMap.SimpleEntry<>(owner, ticks));

        if (ticks < TRACE_TICKS) {
            if (ticks % 20 < tickRate) {
                AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.decryption.tracing", (TRACE_TICKS - ticks) / 20 + 1).withColor(ErrorUtil.ERROR_COLOR));
            }
            return;
        }

        long now = level.getGameTime();
        if (traceCooldown.getOrDefault(player.getUUID(), 0L) > now) return;
        traceCooldown.put(player.getUUID(), now + 20 * 30);
        heldMarkedItems.remove(player.getUUID());

        LivingEntity ownerEntity = ErrorUtil.findLiving(level, owner);
        if (ownerEntity == null) {
            player.sendSystemMessage(Component.translatable("ability.lotmcraft.decryption.trace_lost").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }

        if (ErrorUtil.seqOf(ownerEntity) <= seq) {
            player.sendSystemMessage(Component.translatable("ability.lotmcraft.decryption.trace_too_strong").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }

        player.sendSystemMessage(Component.translatable("ability.lotmcraft.decryption.trace_found",
                ownerEntity.getName(),
                ownerEntity.blockPosition().getX(), ownerEntity.blockPosition().getY(), ownerEntity.blockPosition().getZ(),
                ownerEntity.level().dimension().location().getPath()).withColor(ErrorUtil.ERROR_COLOR));
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.decryptionActive = false;
            return;
        }

        if (entity instanceof ServerPlayer player) {
            PacketHandler.sendToPlayer(player, new SyncDecryptionLookedAtEntitiesAbilityPacket(false, -1));
            heldMarkedItems.remove(player.getUUID());
        }

        clearArtifactScaling(entity);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of(
                "error", 7
        ));
    }

    @Override
    public float getSpiritualityCost() {
        return .25f;
    }

    private static boolean isUnderIllusion(ServerPlayer player) {
        return player.hasEffect(ModEffects.ASLEEP)
                || player.hasEffect(ModEffects.LOOSING_CONTROL);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!ErrorUtil.isErrorOfSequence(player, 7)) return;
        if (!isUnderIllusion(player)) return;

        DecryptionAbility ability = (DecryptionAbility) LOTMCraft.abilityHandler.getById("decryption_ability");
        if (ability == null) return;

        ServerLevel level = player.serverLevel();
        if (!ability.isActiveForEntity(player)) {
            ability.useAbility(level, player, false, true, false, false);
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.decryption.auto").withColor(ErrorUtil.ERROR_COLOR));
        }

        int seq = BeyonderData.getSequence(player);
        float breakChance = Math.max(0.15f, 0.45f - seq * 0.03f);

        MobEffectInstance asleep = player.getEffect(ModEffects.ASLEEP);
        if (asleep != null) {
            if (RANDOM.nextFloat() < breakChance) {
                player.removeEffect(ModEffects.ASLEEP);
            } else if (asleep.getDuration() > 40) {
                player.removeEffect(ModEffects.ASLEEP);
                player.addEffect(new MobEffectInstance(ModEffects.ASLEEP, asleep.getDuration() / 2, asleep.getAmplifier(), asleep.isAmbient(), asleep.isVisible(), asleep.showIcon()));
            }
        }

        MobEffectInstance dream = player.getEffect(ModEffects.LOOSING_CONTROL);
        if (dream != null && RANDOM.nextFloat() < breakChance) {
            player.removeEffect(ModEffects.LOOSING_CONTROL);
        }
    }

    public static boolean isDecrypting(Player player) {
        DecryptionAbility ability = (DecryptionAbility) LOTMCraft.abilityHandler.getById("decryption_ability");
        return ability != null && ability.isActiveForEntity(player);
    }
}
