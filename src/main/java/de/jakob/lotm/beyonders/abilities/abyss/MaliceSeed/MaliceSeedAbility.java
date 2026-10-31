package de.jakob.lotm.beyonders.abilities.abyss.MaliceSeed;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.EntityControllingComponent;
import de.jakob.lotm.attachments.MarionetteComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.SanityComponent;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.ControllingUtils;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.MarionetteUtils;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.gui.custom.malice_seed.MaliceSeedMenu;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AbilityWheelHelper;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class MaliceSeedAbility extends SelectableAbility {
    private static final String MALICE_IDENTITY_KEY = "lotm_malice_identity";
    private static final int SCAN_INTERVAL_TICKS = 29;          // seeds are processed about once per second
    private static final int TICKS_PER_PHASE = 20 * 60 * 10;     // 10 minutes per phase
    private static final int MAX_IDENTITY_DEPTH = 8;
    public record SeedInfo(UUID hostUUID, String hostName, int phase, int progressPercent, String dimension, boolean isPlayer) {}
    private static final Set<String> PURIFICATION_PATHWAYS = Set.of("tyrant", "sun", "twilight_giant", "abyss");
    private static final Set<String> SEED_SIGHT_PATHWAYS = Set.of("sun", "visionary", "twilight_giant");
    private static final int RED = 0xFFa3070c;
    private static final Map<UUID, UUID> whisperTarget = new ConcurrentHashMap<>(); // caster -> host
    private static final Map<UUID, UUID> speakAsTarget = new ConcurrentHashMap<>(); // caster -> host
    private static final Map<UUID, UUID> possessingHost = new ConcurrentHashMap<>(); // caster -> host, while taken over
    private static final Set<UUID> detonatingSeed = ConcurrentHashMap.newKeySet();
    private static final DustParticleOptions maliceDust = new DustParticleOptions(new Vector3f(0.4f, 0f, 0.1f), 2f);
    // Caches, rebuilt from MaliceSeedData on server start
    private static final Set<UUID> knownCasters = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> identityPhase = new ConcurrentHashMap<>(); // identity -> highest phase
    private static int scanCounter = 0;

    public MaliceSeedAbility(String id) {
        super(id, 100);
        this.canBeCopied = false;
        this.canBeReplicated = false;
        this.canBeUsedInArtifact = true;
        this.cannotBeStolen = true;
        canBeShared = false;
    }
    @Override
    public Map<String, Integer> getRequirements() {return new HashMap<>(Map.of("abyss", 2));}
    @Override
    public float getSpiritualityCost() {return 750;}
    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.malice_seed.plant",
                "ability.lotmcraft.malice_seed.view_seeds",
                "ability.lotmcraft.malice_seed.detonate"};
    }
    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        boolean possessing = entity instanceof ServerPlayer p && possessingHost.containsKey(p.getUUID());
        if (possessing && abilityIndex != 2) return;
        switch (abilityIndex) {
            case 0 -> plantSeed(level, entity);
            case 1 -> openSeedMenu(entity);
            case 2 -> {
                if (entity instanceof ServerPlayer p) detonateSeed(p);
            }
        }
    }

    //Shared helpers
    private static int maxPhase(int casterSeq) {return casterSeq <= 0 ? 4 : 3;}
    private static int highestPhase(CompoundTag seeds) {
        int highest = 0;
        for (String key : seeds.getAllKeys()) highest = Math.max(highest, seeds.getCompound(key).getInt("phase"));
        return highest;
    }
    private static void actionBar(LivingEntity entity, String key) {
        AbilityUtil.sendActionBar(entity, Component.translatable(key).withColor(RED));
    }

    //Planting
    private void plantSeed(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        LivingEntity target = AbilityUtil.getTargetEntity(entity, 25, 2.5f);
        if (target == null || target == entity || getSeeds(target).contains(entity.getUUID().toString())) {
            actionBar(entity, "ability.lotmcraft.malice_seed.no_target");
            return;
        }
        if (BeyonderData.isBeyonder(target) && "sun".equals(BeyonderData.getPathway(target)) && BeyonderData.getSequence(target) <= 4) {
            actionBar(entity, "ability.lotmcraft.malice_seed.sun");
            return;
        }
        if (AbilityUtil.isTargetSignificantlyStronger(entity, target)) {
            entity.addEffect(new MobEffectInstance(ModEffects.LOOSING_CONTROL, 20 * 5, 3));
            entity.hurt(entity.damageSources().generic(), 10);
            actionBar(entity, "ability.lotmcraft.malice_seed.backlash");
            return;
        }
        int casterSeq = AbilityUtil.getSeqWithArt(entity, this);
        int targetSeq = BeyonderData.isBeyonder(target) ? BeyonderData.getSequence(target) : 10;
        if (random.nextFloat() >= getInfectionChance(casterSeq, targetSeq, target)) {
            actionBar(entity, "ability.lotmcraft.malice_seed.no_purchase");
            return;
        }
        UUID identity = getMaliceIdentity(target);
        MinecraftServer server = serverLevel.getServer();
        CompoundTag seeds = findSeedsForIdentity(server, identity);
        String casterKey = entity.getUUID().toString();
        // One seed per caster per target identity.
        if (seeds.contains(casterKey)) {
            actionBar(entity, "ability.lotmcraft.malice_seed.no_target");
            return;
        }
        CompoundTag seed = new CompoundTag();
        seed.putInt("phase", 1);
        seed.putInt("progress", 0);
        seed.putInt("casterSeq", casterSeq);
        seeds.put(casterKey, seed);
        saveSeeds(server, identity, seeds);
        ParticleUtil.spawnParticles(serverLevel, maliceDust, target.getEyePosition(), 40, .75, 0);
        level.playSound(null, BlockPos.containing(entity.position()), SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1, 1);
    }
    private static float getInfectionChance(int casterSeq, int targetSeq, LivingEntity target) {
        float chance = Math.clamp(0.25f + (targetSeq - casterSeq) * 0.25f, 0f, 1f);
        String pathway = BeyonderData.isBeyonder(target) ? BeyonderData.getPathway(target) : null;
        if (pathway != null && PURIFICATION_PATHWAYS.contains(pathway)) chance *= 0.5f;
        return chance;
    }
    private void openSeedMenu(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        List<SeedInfo> seeds = collectSeeds(player);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new MaliceSeedMenu(id, inv, seeds), Component.translatable("gui.lotm.malice_seed.title")), buf -> MaliceSeedMenu.writeSeeds(buf, seeds));
    }
    //Seed storage
    private static CompoundTag getSeeds(LivingEntity host) {
        if (host == null) return new CompoundTag();
        MinecraftServer server = host.getServer();
        if (server == null) return new CompoundTag();
        return findSeedsForIdentity(server, getMaliceIdentity(host));
    }
    private static CompoundTag findSeedsForIdentity(MinecraftServer server, UUID identity) {
        if (server == null || identity == null) return new CompoundTag();
        return MaliceSeedData.get(server).getSeeds(identity);
    }
    private static void saveSeeds(MinecraftServer server, UUID identity, CompoundTag seeds) {
        if (server == null || identity == null) return;
        MaliceSeedData.get(server).setSeeds(identity, seeds);
        if (seeds.isEmpty()) {
            identityPhase.remove(identity);
            return;
        }
        identityPhase.put(identity, highestPhase(seeds));
        for (String key : seeds.getAllKeys()) {
            try {knownCasters.add(UUID.fromString(key));} catch (IllegalArgumentException ignored) {}
        }
    }
    private static LivingEntity findLivingEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }
    private static UUID resolveIdentity(UUID entityUUID) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        LivingEntity entity = server == null ? null : findLivingEntity(server, entityUUID);
        return entity != null ? getMaliceIdentity(entity) : entityUUID;
    }
    public static boolean hasSeed(UUID entityUUID) {return getGrowth(entityUUID) > 0;}
    public static int getGrowth(UUID entityUUID) {return identityPhase.getOrDefault(resolveIdentity(entityUUID), 0);}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        knownCasters.clear();
        identityPhase.clear();
        MaliceSeedData data = MaliceSeedData.get(server);
        for (UUID identity : data.identities()) {
            CompoundTag seeds = data.getSeeds(identity);
            if (seeds.isEmpty()) continue;
            identityPhase.put(identity, highestPhase(seeds));
            for (String key : seeds.getAllKeys()) {
                try {knownCasters.add(UUID.fromString(key));} catch (IllegalArgumentException ignored) {}
            }
        }
    }
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        whisperTarget.clear();
        speakAsTarget.clear();
        possessingHost.clear();
        detonatingSeed.clear();
        knownCasters.clear();
        identityPhase.clear();
        scanCounter = 0;
    }
    //Phase progression
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++scanCounter < SCAN_INTERVAL_TICKS) return;
        scanCounter = 0;
        MinecraftServer server = event.getServer();
        MaliceSeedData data = MaliceSeedData.get(server);
        // One tick per identity, so shared infections (avatars, marionettes) advance only once
        for (UUID identity : data.identities()) {
            LivingEntity host = findLivingEntity(server, identity);
            if (host == null || !host.isAlive() || !(host.level() instanceof ServerLevel hostLevel)) continue;
            CompoundTag seeds = data.getSeeds(identity);
            tickSeeds(hostLevel, host, seeds);
            saveSeeds(server, identity, seeds);
        }
    }
    private static void tickSeeds(ServerLevel level, LivingEntity host, CompoundTag seeds) {
        // Sanity loss on the host speeds the timer up: 1x at full sanity, up to 3x at none
        float sanity = host.getData(ModAttachments.SANITY_COMPONENT).getSanity();
        float speed = 1f + 2f * (1f - Math.clamp(sanity, 0f, 1f));
        int gained = Math.round(SCAN_INTERVAL_TICKS * speed);
        Location loc = new Location(host.position(), level);
        for (String casterKey : new ArrayList<>(seeds.getAllKeys())) {
            CompoundTag seed = seeds.getCompound(casterKey);
            int casterSeq = seed.getInt("casterSeq");
            if (InteractionHandler.isInteractionPossible(loc, "purification", casterSeq)) {
                seeds.remove(casterKey);
                notifyCaster(casterKey, "cleansed");
                continue;
            }
            int phase = seed.getInt("phase");
            if (phase < maxPhase(casterSeq)) {
                int progress = seed.getInt("progress") + gained;
                if (progress >= TICKS_PER_PHASE) {
                    phase++;
                    progress = 0;
                    seed.putInt("phase", phase);
                    notifyCaster(casterKey, "grew");
                }
                seed.putInt("progress", progress);
            }
            seeds.put(casterKey, seed);
        }
    }
    //Resurrection
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCasterDeath(LivingDeathEvent event) {
        LivingEntity caster = event.getEntity();
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();
        // Corruption, information destruction and losing control permanently bypass the resurrection
        String damageId = event.getSource().getMsgId().toLowerCase(Locale.ROOT);
        if (damageId.contains("corruption") || damageId.contains("information_destruction") || damageId.contains("loosing_control")) {
            removeAllSeedsFromCaster(caster.getUUID(), server);
            return;
        }
        if (!knownCasters.contains(caster.getUUID())) return;
        String casterKey = caster.getUUID().toString();
        MaliceSeedData data = MaliceSeedData.get(server);
        LivingEntity nearest = null;
        UUID nearestIdentity = null;
        double best = Double.MAX_VALUE;
        for (UUID identity : data.identities()) {
            if (!data.getSeeds(identity).contains(casterKey)) continue;
            LivingEntity host = findLivingEntity(server, identity);
            if (host == null || host == caster || !host.isAlive()) continue;
            double dist = host.level() == serverLevel ? host.distanceToSqr(caster) : Double.MAX_VALUE / 2;
            if (dist < best) {
                best = dist;
                nearest = host;
                nearestIdentity = identity;
            }
        }
        if (nearest == null) {
            removeAllSeedsFromCaster(caster.getUUID(), server);
            return;
        }
        CompoundTag seeds = data.getSeeds(nearestIdentity);
        CompoundTag seed = seeds.getCompound(casterKey).copy();
        float damage = calculateSeedDamage(nearest, seed);
        event.setCanceled(true);
        caster.setHealth(caster.getMaxHealth() * 0.5f);
        if (nearest.level() instanceof ServerLevel hostLevel) {
            if (caster instanceof ServerPlayer player) player.teleportTo(hostLevel, nearest.getX(), nearest.getY(), nearest.getZ(), Set.of(), player.getYRot(), player.getXRot());
            else if (hostLevel == serverLevel) caster.teleportTo(nearest.getX(), nearest.getY(), nearest.getZ());
            ModDamageTypes.trueDamage(nearest, damage, serverLevel, caster);
            ParticleUtil.spawnParticles(hostLevel, new DustParticleOptions(new Vector3f(0.4f, 0f, 0.1f), 2.5f), nearest.getEyePosition(), 60, 2.0, 0);
            hostLevel.playSound(null, nearest.blockPosition(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.5f, 0.6f);
        }
        seeds.remove(casterKey);
        saveSeeds(server, nearestIdentity, seeds);
    }
    private static float calculateSeedDamage(LivingEntity host, CompoundTag seed) {
        int phase = seed.getInt("phase");
        int casterSeq = seed.getInt("casterSeq");
        int maxPhase = maxPhase(casterSeq);
        int hostSeq = BeyonderData.isBeyonder(host) ? BeyonderData.getSequence(host) : 10;
        // 50% at equal sequence, +25% per sequence the host is weaker, -25% per sequence the host is stronger
        float cap = Math.clamp(0.5f + 0.25f * (hostSeq - casterSeq), 0f, 1f);
        int effectivePhase = Math.min(phase, maxPhase);
        return host.getMaxHealth() * cap * ((float) effectivePhase / maxPhase);
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHostDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity host = event.getEntity();
        if (!(host.level() instanceof ServerLevel level)) return;
        // Players keep their seeds across respawn. A dead mob that is its own identity can never be infected again, so drop its entry.
        UUID identity = getMaliceIdentity(host);
        if (!(host instanceof ServerPlayer) && identity.equals(host.getUUID()) && identityPhase.containsKey(identity)) {
            saveSeeds(level.getServer(), identity, new CompoundTag());
        }
    }
    //Seed menu
    public static List<SeedInfo> collectSeeds(ServerPlayer caster) {
        String casterKey = caster.getUUID().toString();
        MinecraftServer server = caster.serverLevel().getServer();
        MaliceSeedData data = MaliceSeedData.get(server);
        List<SeedInfo> result = new ArrayList<>();
        for (UUID identity : data.identities()) {
            CompoundTag seed = data.getSeeds(identity).getCompound(casterKey);
            if (seed.isEmpty()) continue;
            LivingEntity host = findLivingEntity(server, identity);
            if (host == null || !host.isAlive() || host.hasEffect(ModEffects.CONCEALMENT)) continue;
            result.add(new SeedInfo(host.getUUID(), host.getName().getString(), seed.getInt("phase"), seed.getInt("progress") * 100 / TICKS_PER_PHASE, host.level().dimension().location().toString(), host instanceof ServerPlayer));
        }
        return result;
    }
    //Whisper and speak-as
    public static void setChannel(ServerPlayer caster, UUID hostUUID, int mode) {
        UUID casterUUID = caster.getUUID();
        UUID previousSpeakAsHost = speakAsTarget.get(casterUUID);
        UUID previousWhisperHost = whisperTarget.get(casterUUID);
        boolean wasSpeakingAs = previousSpeakAsHost != null;
        whisperTarget.remove(casterUUID);
        speakAsTarget.remove(casterUUID);
        if (previousWhisperHost != null && mode != 1) actionBar(caster, "ability.lotmcraft.malice_seed.whisper_off");
        boolean switchingHost = mode == 2 && wasSpeakingAs && !hostUUID.equals(previousSpeakAsHost);
        if (mode == 0) return;
        LivingEntity host = findHostEntity(caster, hostUUID);
        if (host == null) return;
        if (mode == 1 && !(host instanceof ServerPlayer)) return;
        CompoundTag seed = getSeeds(host).getCompound(casterUUID.toString());
        int requiredPhase = mode == 1 ? 2 : 3;
        if (seed.isEmpty() || seed.getInt("phase") < requiredPhase) return;
        if (mode == 1) {
            if (hostUUID.equals(previousWhisperHost)) {
                actionBar(caster, "ability.lotmcraft.malice_seed.whisper_off");
            } else {
                whisperTarget.put(casterUUID, hostUUID);
                actionBar(caster, "ability.lotmcraft.malice_seed.whisper_on");
            }
        } else if (mode == 2) {
            if (!wasSpeakingAs || switchingHost) startSpeakAs(caster, host);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true) // let CorruptingVoiceAbility's NORMAL-priority handler process damage first
    public static void onChatMessage(ServerChatEvent event) {
        ServerPlayer caster = event.getPlayer();
        UUID whisperHost = whisperTarget.get(caster.getUUID());
        if (whisperHost != null) {
            handleWhisper(event, caster, whisperHost);
            return;
        }
        UUID speakAsHost = speakAsTarget.get(caster.getUUID());
        if (speakAsHost != null) handleSpeakAs(event, caster, speakAsHost);
    }
    private static void handleWhisper(ServerChatEvent event, ServerPlayer caster, UUID hostUUID) {
        LivingEntity hostEntity = findHostEntity(caster, hostUUID);
        if (!(hostEntity instanceof ServerPlayer hostPlayer)) {
            whisperTarget.remove(caster.getUUID());
            return;
        }
        CompoundTag seed = getSeeds(hostPlayer).getCompound(caster.getUUID().toString());
        if (seed.isEmpty() || seed.getInt("phase") < 2) {
            whisperTarget.remove(caster.getUUID());
            return;
        }
        event.setCanceled(true);
        String message = event.getRawText();
        ServerPlayer candidate = findRandomPlayerInBand(hostPlayer);
        String speakerName = candidate != null ? candidate.getName().getString() : "";
        hostPlayer.sendSystemMessage(Component.literal("<" + speakerName + "> " + message));
        advancePhaseFromChat(hostPlayer, caster, message.length());
    }
    private static void handleSpeakAs(ServerChatEvent event, ServerPlayer caster, UUID hostUUID) {
        event.setCanceled(true);
        LivingEntity host = findHostEntity(caster, hostUUID);
        if (host == null || !host.isAlive()) {
            endSpeakAs(caster);
            return;
        }
        CompoundTag seed = getSeeds(host).getCompound(caster.getUUID().toString());
        if (seed.isEmpty() || seed.getInt("phase") < 3) {
            endSpeakAs(caster);
            return;
        }
        String message = event.getRawText();
        Component spoofed = Component.literal("<" + host.getName().getString() + "> " + message);
        for (ServerPlayer online : caster.server.getPlayerList().getPlayers()) {
            if (host instanceof ServerPlayer hostPlayer && online.getUUID().equals(hostPlayer.getUUID())) continue;
            online.sendSystemMessage(spoofed);
        }
        advancePhaseFromChat(host, caster, message.length());
    }
    private static ServerPlayer findRandomPlayerInBand(ServerPlayer host) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : host.server.getPlayerList().getPlayers()) {
            if (p == host || p.level() != host.level()) continue;
            double d = p.distanceTo(host);
            if (d >= 20 && d <= 90) candidates.add(p);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(new Random().nextInt(candidates.size()));
    }
    private static void advancePhaseFromChat(LivingEntity host, ServerPlayer caster, int charCount) {
        MinecraftServer server = host.getServer();
        if (server == null) return;
        UUID identity = getMaliceIdentity(host);
        CompoundTag seeds = findSeedsForIdentity(server, identity);
        String casterKey = caster.getUUID().toString();
        CompoundTag seed = seeds.getCompound(casterKey);
        if (seed.isEmpty()) return;
        int phase = seed.getInt("phase");
        if (phase >= maxPhase(seed.getInt("casterSeq"))) return;
        double reductionFraction = 0.0001 * phase * charCount;
        int progress = seed.getInt("progress") + (int) Math.round(TICKS_PER_PHASE * reductionFraction);
        if (progress >= TICKS_PER_PHASE) {
            phase++;
            progress = 0;
            seed.putInt("phase", phase);
            notifyCaster(casterKey, "grew");
        }
        seed.putInt("progress", progress);
        seeds.put(casterKey, seed);
        saveSeeds(server, identity, seeds);
    }
    private static LivingEntity findHostEntity(ServerPlayer caster, UUID hostUUID) {return findLivingEntity(caster.server, hostUUID);}
    @SubscribeEvent
    public static void onCasterViewTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (!(p instanceof ServerPlayer caster)) return;
        UUID casterUUID = caster.getUUID();
        UUID hostUUID = speakAsTarget.get(casterUUID);
        if (hostUUID == null) return;
        LivingEntity host = findHostEntity(caster, hostUUID);
        boolean stillValid = host != null && host.isAlive();
        // The seed lookup copies NBT, so only re-validate once per second
        if (stillValid && caster.tickCount % 20 == 0) {
            CompoundTag seed = getSeeds(host).getCompound(casterUUID.toString());
            stillValid = !seed.isEmpty() && seed.getInt("phase") >= 3;
        }
        if (!stillValid) {
            endSpeakAs(caster);
            return;
        }
        if (caster.getCamera() != host) caster.setCamera(host);
    }
    public static void cleanupOnLogout(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        whisperTarget.remove(playerUUID);
        if (speakAsTarget.remove(playerUUID) != null) ControllingUtils.endSpiritProjection(player);
        possessingHost.remove(playerUUID);
        detonatingSeed.remove(playerUUID);
    }
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {if (event.getEntity() instanceof ServerPlayer player) cleanupOnLogout(player);}
    static void startSpeakAs(ServerPlayer caster, LivingEntity host) {
        if (host == null || !host.isAlive()) return;
        if (speakAsTarget.containsKey(caster.getUUID())) return;
        if (!ControllingUtils.startSpiritProjection(caster, host)) return;
        speakAsTarget.put(caster.getUUID(), host.getUUID());
    }
    public static void endSpeakAs(ServerPlayer caster) {
        speakAsTarget.remove(caster.getUUID());
        ControllingUtils.endSpiritProjection(caster);
    }
    public static boolean isSpeakAsHost(UUID casterUUID, UUID entityUUID) {
        UUID host = speakAsTarget.get(casterUUID);
        return host != null && host.equals(entityUUID);
    }
    public static LivingEntity getSpeakAsHost(UUID casterUUID) {
        UUID hostUUID = speakAsTarget.get(casterUUID);
        if (hostUUID == null) return null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        LivingEntity living = findLivingEntity(server, hostUUID);
        return living != null && living.isAlive() ? living : null;
    }
    //Takeover and detonation (phase 4, seq 0)
    public static void takeOverHost(ServerPlayer caster, UUID hostUUID) {
        if (ControllingUtils.isControlling(caster)) return;
        LivingEntity host = findHostEntity(caster, hostUUID);
        if (host == null) return;
        CompoundTag seed = getSeeds(host).getCompound(caster.getUUID().toString());
        if (seed.isEmpty() || seed.getInt("phase") < 4) return;
        if (BeyonderData.getSequence(caster) != 0) return;
        if (!ControllingUtils.startControlling(caster, host, false, true)) return;
        possessingHost.put(caster.getUUID(), hostUUID);
        MaliceSeedAbility instance = (MaliceSeedAbility) LOTMCraft.abilityHandler.getById("malice_seed_ability");
        ControllingUtils.setAllowedAbilitiesWhileControlling(caster, List.of(instance.getId()));
        AbilityWheelHelper.setAbilities(caster, new ArrayList<>(List.of(instance.getId())));
        instance.selectedAbilities.put(caster.getUUID(), 2);
        AbilityWheelHelper.setSelectedAbility(caster, 0);
    }
    private static void detonateSeed(ServerPlayer caster) { // doesn't work on non player mobs
        UUID hostUUID = possessingHost.get(caster.getUUID());
        if (hostUUID == null) return;
        if (!ControllingUtils.isControlling(caster)) {
            possessingHost.remove(caster.getUUID());
            return;
        }
        EntityControllingComponent component = caster.getData(ModAttachments.ENTITY_CONTROLLING_COMPONENT);
        LivingEntity host = component.getControlledEntity();
        if (host == null) return;
        CompoundTag seed = getSeeds(host).getCompound(caster.getUUID().toString());
        if (seed.isEmpty()) return;
        float damage = calculateSeedDamage(host, seed);
        if (!(caster.level() instanceof ServerLevel hostLevel)) return;
        Vec3 explosionPos = caster.position();
        detonatingSeed.add(caster.getUUID());
        ControllingUtils.cancel(caster, 0, true, false); // triggers cleanupTakeover(...) internally
        ModDamageTypes.trueDamage(host, damage, hostLevel, caster);
        caster.teleportTo(hostLevel, explosionPos.x, explosionPos.y, explosionPos.z, Set.of(), caster.getYRot(), caster.getXRot());
        ParticleUtil.spawnParticles(hostLevel, ParticleTypes.SOUL, explosionPos, 80, 1.0, 1.0);
        hostLevel.playSound(null, BlockPos.containing(explosionPos), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 2f, 0.6f);
        AbilityUtil.getNearbyEntities(caster, hostLevel, explosionPos, 6).forEach(e -> {
            SanityComponent sanity = e.getData(ModAttachments.SANITY_COMPONENT);
            sanity.increaseSanityAndSync(-0.05f, e);
        });
    }
    public static void cleanupTakeover(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        UUID hostUUID = possessingHost.remove(playerUUID);
        if (hostUUID == null) return;
        if (!detonatingSeed.remove(playerUUID)) return;
        LivingEntity host = findHostEntity(player, hostUUID);
        if (host != null && host.isAlive()) removeSpecificSeed(host, playerUUID);
    }
    private static void removeSpecificSeed(LivingEntity host, UUID casterUUID) {
        MinecraftServer server = host.getServer();
        if (server == null) return;
        UUID identity = getMaliceIdentity(host);
        CompoundTag seeds = findSeedsForIdentity(server, identity);
        seeds.remove(casterUUID.toString());
        saveSeeds(server, identity, seeds);
    }
    //Purification
    @SubscribeEvent
    public static void onPurificationUsed(AbilityUsedEvent event) {
        ArrayList<String> flags = event.getInteractionFlags();
        if (flags == null || !flags.contains("purification")) return;
        LivingEntity purifier = event.getEntity();
        if (!(purifier.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        int purifierSeq = BeyonderData.isBeyonder(purifier) ? BeyonderData.getSequence(purifier) : 10;
        boolean purifierIsPurificationPathway = BeyonderData.isBeyonder(purifier) && PURIFICATION_PATHWAYS.contains(BeyonderData.getPathway(purifier));
        int leniency = purifierIsPurificationPathway ? 1 : 0;
        Set<UUID> processed = new HashSet<>();
        for (LivingEntity host : level.getEntitiesOfClass(LivingEntity.class, purifier.getBoundingBox().inflate(event.getInteractionRadius()))) {
            if (!processed.add(getMaliceIdentity(host))) continue;
            if (purifyHost(server, host, purifierSeq, leniency, 0)) {
                SanityComponent sanity = host.getData(ModAttachments.SANITY_COMPONENT);
                sanity.increaseSanityAndSync(-0.10f, host);
            }
        }
    }
    //Entry point for abilities that cleanse seeds without the "purification" interaction flag
    public static void purify(LivingEntity purifier, LivingEntity host, int purifierSeq, int margin) {
        if (!(host.level() instanceof ServerLevel level)) return;
        purifyHost(level.getServer(), host, purifierSeq, purificationLeniency(purifier), margin);
    }
    private static int purificationLeniency(LivingEntity purifier) {return BeyonderData.isBeyonder(purifier) && PURIFICATION_PATHWAYS.contains(BeyonderData.getPathway(purifier)) ? 1 : 0;}
    private static boolean purifyHost(MinecraftServer server, LivingEntity host, int purifierSeq, int leniency, int margin) {
        UUID identity = getMaliceIdentity(host);
        if (!identityPhase.containsKey(identity)) return false;
        CompoundTag seeds = findSeedsForIdentity(server, identity);
        int threshold = leniency - margin;
        boolean anyFailedAttempt = false;
        for (String casterKey : new ArrayList<>(seeds.getAllKeys())) {
            CompoundTag seed = seeds.getCompound(casterKey);
            int diff = purifierSeq - seed.getInt("casterSeq"); // <= 0 means equal or stronger
            if (diff <= threshold) {
                seeds.remove(casterKey);
                notifyCaster(casterKey, "cleansed");
            } else if (diff == threshold + 1) {
                int phase = seed.getInt("phase");
                if (phase <= 1) {
                    seeds.remove(casterKey);
                    notifyCaster(casterKey, "cleansed");
                } else {
                    seed.putInt("phase", phase - 1);
                    seed.putInt("progress", 0);
                    seeds.put(casterKey, seed);
                    notifyCaster(casterKey, "lowered");
                }
            } else {
                anyFailedAttempt = true;
            }
        }
        saveSeeds(server, identity, seeds);
        return anyFailedAttempt;
    }
    private static void notifyCaster(String casterKey, String outcome) {
        UUID casterUUID;
        try {casterUUID = UUID.fromString(casterKey);} catch (IllegalArgumentException e) {return;}
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer caster = server.getPlayerList().getPlayer(casterUUID);
        if (caster == null) return;
        String key = switch (outcome) {
            case "cleansed" -> "ability.lotmcraft.malice_seed.purified";
            case "lowered" -> "ability.lotmcraft.malice_seed.weakened";
            default -> "ability.lotmcraft.malice_seed.grew";
        };
        AbilityUtil.sendActionBar(caster, Component.translatable(key).withColor(0xFFff124d));
    }
    private static void removeAllSeedsFromCaster(UUID casterUUID, MinecraftServer server) {
        if (server == null) return;
        String casterKey = casterUUID.toString();
        MaliceSeedData data = MaliceSeedData.get(server);
        for (UUID identity : data.identities()) {
            CompoundTag seeds = data.getSeeds(identity);
            if (!seeds.contains(casterKey)) continue;
            seeds.remove(casterKey);
            saveSeeds(server, identity, seeds);
        }
        knownCasters.remove(casterUUID);
    }
    //Identity
    public static boolean canObserverSeeSeed(LivingEntity observer, LivingEntity host) {
        if (observer == host || host.hasEffect(ModEffects.CONCEALMENT)) return false;
        if (!identityPhase.containsKey(getMaliceIdentity(host))) return false;
        CompoundTag seeds = getSeeds(host);
        if (seeds.isEmpty()) return false;
        boolean isBeyonder = BeyonderData.isBeyonder(observer);
        int observerSeq = isBeyonder ? BeyonderData.getSequence(observer) : 10;
        int leniency = (isBeyonder && SEED_SIGHT_PATHWAYS.contains(BeyonderData.getPathway(observer))) ? 1 : 0;
        for (String casterKey : seeds.getAllKeys()) {
            int casterSeq = seeds.getCompound(casterKey).getInt("casterSeq");
            if (observerSeq <= casterSeq + leniency) return true;
        }
        return false;
    }
    public static UUID getMaliceIdentity(LivingEntity entity) {
        return resolveIdentity(entity, 0);
    }
    private static UUID resolveIdentity(LivingEntity entity, int depth) {
        if (entity instanceof AvatarEntity avatar) {
            UUID owner = avatar.getOriginalOwner();
            if (owner != null) return owner;
        }
        if (depth < MAX_IDENTITY_DEPTH && MarionetteUtils.isMarionette(entity)) {
            MarionetteComponent component = entity.getData(ModAttachments.MARIONETTE_COMPONENT);
            try {
                UUID owner = UUID.fromString(component.getControllerUUID());
                MinecraftServer server = entity.getServer();
                if (server != null) {
                    LivingEntity controller = findLivingEntity(server, owner);
                    if (controller != null && controller != entity) return resolveIdentity(controller, depth + 1);
                }
                return owner;
            } catch (IllegalArgumentException ignored) {}
        }
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(MALICE_IDENTITY_KEY) ? data.getUUID(MALICE_IDENTITY_KEY) : entity.getUUID();
    }
    public static void registerManifestation(UUID identity, LivingEntity manifestation) {
        if (identity == null || manifestation == null) return;
        manifestation.getPersistentData().putUUID(MALICE_IDENTITY_KEY, identity);
    }
    public static void copyInfectionsToPersonaRecipient(LivingEntity owner, LivingEntity recipient) {
        if (owner == null || recipient == null || owner == recipient) return;
        MinecraftServer server = owner.getServer();
        if (server == null || recipient.getServer() != server) return;
        UUID ownerIdentity = getMaliceIdentity(owner);
        UUID recipientIdentity = getMaliceIdentity(recipient);
        if (ownerIdentity.equals(recipientIdentity)) return;
        CompoundTag ownerSeeds = findSeedsForIdentity(server, ownerIdentity);
        if (ownerSeeds.isEmpty()) return;
        CompoundTag recipientSeeds = findSeedsForIdentity(server, recipientIdentity);
        for (String casterKey : ownerSeeds.getAllKeys()) if (!recipientSeeds.contains(casterKey)) recipientSeeds.put(casterKey, ownerSeeds.getCompound(casterKey).copy());
        saveSeeds(server, recipientIdentity, recipientSeeds);
    }
}