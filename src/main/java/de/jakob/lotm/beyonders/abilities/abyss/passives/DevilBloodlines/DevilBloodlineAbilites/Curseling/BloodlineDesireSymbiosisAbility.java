package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.Curseling;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class BloodlineDesireSymbiosisAbility extends Ability {
    private static final String LANG = "ability.lotmcraft.desire_symbiosis.";
    private static final int MESSAGE_COLOR = 0x6d32a8;
    private static final int FAIL_COLOR = 0xbf3232;
    private static final double MIN_RANGE = 5.0D;
    private static final double MAX_RANGE = 10.0D;
    private static final long HOLD_TICKS = 20L * 5L;
    private static final long HOLD_TIMEOUT_TICKS = 15L;
    private static final long FAIL_LOCKOUT_TICKS = 20L * 60L * 10L;   // per linker and target
    private static final int LINK_MAX_TICKS = 20 * 60 * 20;           // safety cap for a link
    private static final float MIN_HEALTH = 1.0F;
    private static final double APPROACH_DISTANCE = 2.5D;
    private enum DesireType { KILL_MOB, GAIN_RESOURCE, APPROACH_PLAYER }
    private static final List<EntityType<?>> KILL_TARGETS = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.ENDERMAN);
    private static final List<Item> RESOURCES = List.of(Items.DIAMOND, Items.GOLD_INGOT, Items.EMERALD, Items.NETHERITE_SCRAP, Items.LAPIS_LAZULI);
    private static final class Desire {
        final DesireType type;
        final EntityType<?> mob;   // KILL_MOB
        final Item item;           // GAIN_RESOURCE
        int baseline;              // GAIN_RESOURCE: item count when linked
        final Component text;
        Desire(DesireType type, EntityType<?> mob, Item item, int baseline, Component text) {
            this.type = type;
            this.mob = mob;
            this.item = item;
            this.baseline = baseline;
            this.text = text;
        }
    }
    private static final class Link {
        final UUID target;
        final Desire desire;
        final int linkerSeq;       // captured at cast time, because artifact scaling is cleared after the cast
        volatile UUID taskId;
        Link(UUID target, Desire desire, int linkerSeq) {
            this.target = target;
            this.desire = desire;
            this.linkerSeq = linkerSeq;
        }
    }
    private static final class Channel {
        final UUID target;
        final int linkerSeq;
        final long start;
        volatile long lastHold;
        Channel(UUID target, int linkerSeq, long start) {
            this.target = target;
            this.linkerSeq = linkerSeq;
            this.start = start;
            this.lastHold = start;
        }
    }
    private static final Map<UUID, Link> links = new ConcurrentHashMap<>();
    private static final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Long>> lockouts = new ConcurrentHashMap<>();

    public BloodlineDesireSymbiosisAbility(String id) {
        super(id, 100f);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        cannotBeStolen = true;
        canBeShared = false;
        canBeUsedInArtifact = true;
    }
    @Override
    public Map<String, Integer> getRequirements() {return new HashMap<>(Map.of("abyss", 5));}
    @Override
    public float getSpiritualityCost() {return 300;}
    @Override
    public boolean meetsBloodlineRequirement(LivingEntity entity) {return BloodlineCurselingAbility.isValidCurseling(entity);}
    //Messages
    private static MutableComponent tr(String key, Object... args) {return Component.translatable(LANG + key, args);}
    private static void bar(ServerPlayer player, String key, Object... args) {AbilityUtil.sendActionBar(player, tr(key, args).withColor(MESSAGE_COLOR));}
    private static void failBar(ServerPlayer player, String key, Object... args) {AbilityUtil.sendActionBar(player, tr(key, args).withColor(FAIL_COLOR));}
    //Linking: hold the ability for 5 seconds
    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) return;
        UUID linkerId = player.getUUID();
        if (channels.containsKey(linkerId)) return;
        if (links.containsKey(linkerId)) {
            bar(player, "already_linked");
            return;
        }
        LivingEntity found = AbilityUtil.getTargetEntity(player, (int) MAX_RANGE, 2f);
        if (!(found instanceof ServerPlayer target) || target == player) {
            bar(player, "players_only");
            return;
        }
        double distance = player.distanceTo(target);
        if (distance < MIN_RANGE || distance > MAX_RANGE) {
            bar(player, "out_of_range");
            return;
        }
        int linkerSeq = AbilityUtil.getSeqWithArt(player, this);
        int targetSeq = BeyonderData.isBeyonder(target) ? BeyonderData.getSequence(target) : 10;
        if (targetSeq < linkerSeq) {
            bar(player, "too_strong");
            return;
        }
        long now = serverLevel.getGameTime();
        long lockedUntil = lockouts.getOrDefault(linkerId, Map.of()).getOrDefault(target.getUUID(), 0L);
        if (now < lockedUntil) {
            failBar(player, "locked_out", (lockedUntil - now) / 20L);
            return;
        }
        channels.put(linkerId, new Channel(target.getUUID(), linkerSeq, now));
        bar(player, "hold");
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.5f);
        AtomicReference<UUID> taskRef = new AtomicReference<>();
        UUID taskId = ServerScheduler.scheduleForDuration(0, 5, (int) (HOLD_TICKS + 40), () -> channelTick(serverLevel, linkerId, taskRef), null, serverLevel, () -> AbilityUtil.getTimeInArea(player, new Location(player.position(), serverLevel)));
        taskRef.set(taskId);
    }
    @Override
    public void onHold(Level level, LivingEntity entity) {
        if (level.isClientSide()) return;
        Channel channel = channels.get(entity.getUUID());
        if (channel != null) channel.lastHold = level.getGameTime();
    }
    private void channelTick(ServerLevel level, UUID linkerId, AtomicReference<UUID> taskRef) {
        Channel channel = channels.get(linkerId);
        MinecraftServer server = level.getServer();
        ServerPlayer linker = server.getPlayerList().getPlayer(linkerId);
        if (channel == null || linker == null) {
            stopChannel(linkerId, taskRef);
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(channel.target);
        long now = level.getGameTime();
        // The target died or left: nothing to blame the linker for
        if (target == null || !target.isAlive()) {
            bar(linker, "lost");
            stopChannel(linkerId, taskRef);
            return;
        }
        if (now - channel.lastHold > HOLD_TIMEOUT_TICKS) {
            failLink(level, linker, target, "fail_released");
            stopChannel(linkerId, taskRef);
            return;
        }
        double distance = linker.distanceTo(target);
        if (distance < MIN_RANGE || distance > MAX_RANGE) {
            failLink(level, linker, target, "fail_range");
            stopChannel(linkerId, taskRef);
            return;
        }
        long held = now - channel.start;
        if (held >= HOLD_TICKS) {
            stopChannel(linkerId, taskRef);
            startLink(level, linker, target, channel.linkerSeq);
            return;
        }
        if (held % 20L < 5L) bar(linker, "progress", held * 100L / HOLD_TICKS);
    }
    private static void stopChannel(UUID linkerId, AtomicReference<UUID> taskRef) {
        channels.remove(linkerId);
        UUID id = taskRef.get();
        if (id != null) ServerScheduler.cancel(id);
    }
    private static void failLink(ServerLevel level, ServerPlayer linker, ServerPlayer target, String reasonKey) {
        long now = level.getGameTime();
        Map<UUID, Long> perTarget = lockouts.computeIfAbsent(linker.getUUID(), id -> new ConcurrentHashMap<>());
        perTarget.values().removeIf(until -> until <= now);
        perTarget.put(target.getUUID(), now + FAIL_LOCKOUT_TICKS);
        failBar(linker, reasonKey);
        level.playSound(null, linker.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
    }
    //The link
    private void startLink(ServerLevel level, ServerPlayer linker, ServerPlayer target, int linkerSeq) {
        Desire desire = rollDesire(target);
        Link link = new Link(target.getUUID(), desire, linkerSeq);
        links.put(linker.getUUID(), link);
        bar(linker, "linked", target.getName(), desire.text);
        ParticleUtil.spawnParticles(level, ParticleTypes.SOUL, linker.position().add(0, 1, 0), 40, .5, .02);
        level.playSound(null, linker.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
        // The target is told what their desire is, but not who linked it
        target.sendSystemMessage(tr("target_notice", desire.text).withColor(MESSAGE_COLOR));
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.4f);

        UUID linkerId = linker.getUUID();
        AtomicReference<UUID> taskRef = new AtomicReference<>();
        UUID taskId = ServerScheduler.scheduleForDuration(0, 20, LINK_MAX_TICKS,
                () -> linkTick(level, linkerId, taskRef),
                () -> endLink(level.getServer(), linkerId, link, "ended_faded"),
                level, () -> AbilityUtil.getTimeInArea(linker, new Location(linker.position(), level)));
        taskRef.set(taskId);
        link.taskId = taskId;
    }
    private Desire rollDesire(ServerPlayer target) {
        return switch (DesireType.values()[random.nextInt(DesireType.values().length)]) {
            case KILL_MOB -> {
                EntityType<?> mob = KILL_TARGETS.get(random.nextInt(KILL_TARGETS.size()));
                yield new Desire(DesireType.KILL_MOB, mob, null, 0, tr("desire_kill_mob", mob.getDescription()));
            }
            case GAIN_RESOURCE -> {
                Item item = RESOURCES.get(random.nextInt(RESOURCES.size()));
                int baseline = target.getInventory().countItem(item);
                yield new Desire(DesireType.GAIN_RESOURCE, null, item, baseline, tr("desire_gain_resource", item.getDescription()));
            }
            case APPROACH_PLAYER -> new Desire(DesireType.APPROACH_PLAYER, null, null, 0, tr("desire_approach_player"));
        };
    }
    private static void linkTick(ServerLevel level, UUID linkerId, AtomicReference<UUID> taskRef) {
        Link link = links.get(linkerId);
        if (link == null) {
            UUID id = taskRef.get();
            if (id != null) ServerScheduler.cancel(id);
            return;
        }
        MinecraftServer server = level.getServer();
        ServerPlayer target = server.getPlayerList().getPlayer(link.target);
        if (server.getPlayerList().getPlayer(linkerId) == null) {
            endLink(server, linkerId, link, null);
            return;
        }
        if (target == null || !target.isAlive()) {
            endLink(server, linkerId, link, "ended_gone");
            return;
        }
        // Cleansing or purification around the target lifts the desire
        Location loc = new Location(target.position(), target.level());
        if (InteractionHandler.isInteractionPossible(loc, "cleansing", link.linkerSeq) || InteractionHandler.isInteractionPossible(loc, "purification", link.linkerSeq)) {
            endLink(server, linkerId, link, "ended_lifted");
            return;
        }
        if (isFulfilled(link.desire, target, linkerId)) endLink(server, linkerId, link, "ended_fulfilled");
    }
    private static boolean isFulfilled(Desire desire, ServerPlayer target, UUID linkerId) {
        return switch (desire.type) {
            case GAIN_RESOURCE -> {
                int count = target.getInventory().countItem(desire.item);
                if (count < desire.baseline) desire.baseline = count;
                yield count > desire.baseline;
            }
            // The linker standing next to them does not count
            case APPROACH_PLAYER -> target.level().players().stream().anyMatch(p -> p != target && !p.getUUID().equals(linkerId) && !p.isSpectator() && p.distanceTo(target) <= APPROACH_DISTANCE);
            case KILL_MOB -> false;
        };
    }
    private static void endLink(MinecraftServer server, UUID linkerId, Link link, String reasonKey) {
        if (!links.remove(linkerId, link)) return;
        UUID task = link.taskId;
        if (task != null) ServerScheduler.cancel(task);
        if (reasonKey == null) return;
        ServerPlayer linker = server.getPlayerList().getPlayer(linkerId);
        if (linker != null) {
            bar(linker, reasonKey);
            linker.level().playSound(null, linker.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.5f);
        }
    }
    //Events
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        Link link = links.get(victim.getUUID());
        if (link == null) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || !attacker.getUUID().equals(link.target)) return;
        // Absorption soaks damage first, then health. Never let the health go below MIN_HEALTH.
        float allowed = victim.getHealth() + victim.getAbsorptionAmount() - MIN_HEALTH;
        if (event.getNewDamage() > allowed) {
            event.setNewDamage(Math.max(0.0F, allowed));
            ParticleUtil.spawnParticles(level, ParticleTypes.SOUL, victim.position().add(0, 1, 0), 15, .4, .02);
        }
    }
    // Deaths: kill desires, and links that lose a side
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Entity killer = event.getSource().getEntity();
        for (Map.Entry<UUID, Link> entry : new ArrayList<>(links.entrySet())) {
            UUID linkerId = entry.getKey();
            Link link = entry.getValue();
            if (linkerId.equals(dead.getUUID())) {
                endLink(server, linkerId, link, null);
            } else if (link.target.equals(dead.getUUID())) {
                endLink(server, linkerId, link, "ended_died");
            } else if (link.desire.type == DesireType.KILL_MOB
                    && killer != null && killer.getUUID().equals(link.target)
                    && dead.getType() == link.desire.mob) {
                endLink(server, linkerId, link, "ended_fulfilled");
            }
        }
    }
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        channels.remove(id);
        Link link = links.get(id);
        MinecraftServer server = event.getEntity().getServer();
        if (link != null && server != null) endLink(server, id, link, null);
    }
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        channels.clear();
        links.clear();
        lockouts.clear();
    }
}