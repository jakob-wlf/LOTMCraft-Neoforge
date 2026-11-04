package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.CopiedAbilityComponent;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.ReplicationAnalysisPacket;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.CopiedAbilityHelper;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ReplicatingAbility extends ToggleAbility {

    private static final double RADIUS = 20;
    private static final int REPEAT_TICKS = 40;

    private static final int[][] USES = {{1, 1}, {2, 3}, {4, 6}, {7, 10}, {15, 15}, {20, 20}};

    private record Understanding(int uses, int needed) {}

    private static final Map<UUID, Map<String, Understanding>> understanding = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> lastSeen = new ConcurrentHashMap<>();

    public ReplicatingAbility(String id) {
        super(id);

        canBeCopied = false;
        canBeUsedByNPC = false;
        cannotBeStolen = true;
        canBeReplicated = false;
        autoClear = false;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 2));
    }

    @Override
    public float getSpiritualityCost() {
        return 10;
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (level.isClientSide) return;
        level.playSound(null, entity.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1, 1);
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel) {
            ParticleUtil.spawnParticles(serverLevel, ParticleTypes.ENCHANT, entity.getEyePosition(), 2, .4, .3);
        }
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide) return;
        clearArtifactScaling(entity);
    }

    private static int difficulty(int observerSeq, int abilitySeq, boolean concealed) {
        int level;
        if (abilitySeq >= 7)      level = 0;
        else if (abilitySeq >= 5) level = 1;
        else if (abilitySeq >= 3) level = 2;
        else if (abilitySeq >= 1) level = 3;
        else                      level = 4;

        if (observerSeq < 2) level -= 2 - observerSeq;
        if (concealed) level++;
        return Math.min(level, USES.length - 1);
    }

    @SubscribeEvent
    public static void onAbilityUsed(AbilityUsedEvent event) {
        Ability used = event.getAbility();
        LivingEntity user = event.getEntity();
        if (used == null || user == null || used instanceof ReplicatingAbility) return;
        if (!(LOTMCraft.abilityHandler.getById("replicating_ability") instanceof ReplicatingAbility replicating)) return;

        ServerLevel level = event.getLevel();
        long now = level.getGameTime();
        Map<String, Long> seen = lastSeen.computeIfAbsent(user.getUUID(), k -> new ConcurrentHashMap<>());
        Long previous = seen.put(used.getId(), now);
        if (previous != null && now - previous < REPEAT_TICKS) return;

        for (ServerPlayer observer : level.players()) {
            if (observer == user || observer.distanceToSqr(event.getPosition()) > RADIUS * RADIUS) continue;
            if (!replicating.isActiveForEntity(observer)) continue;
            replicating.observe(level, observer, user, used);
        }
    }

    private void observe(ServerLevel level, ServerPlayer observer, LivingEntity user, Ability used) {
        if (CopiedAbilityHelper.hasCopiedAbility(observer, used.getId())) return;
        if (!used.canBeReplicated) {
            sendAnalysis(observer, used, 0, 0, ReplicationAnalysisPacket.SEALED);
            return;
        }

        int observerSeq = AbilityUtil.getSeqWithArt(observer, this);
        boolean concealed = user.hasEffect(ModEffects.CONCEALMENT);
        int difficulty = difficulty(observerSeq, used.lowestSequenceUsable(), concealed);

        Map<String, Understanding> known = understanding.computeIfAbsent(observer.getUUID(), k -> new ConcurrentHashMap<>());
        Understanding current = known.get(used.getId());
        if (current == null) {
            int[] range = USES[Math.max(0, difficulty)];
            int needed = difficulty < 0 ? 1 : range[0] + random.nextInt(range[1] - range[0] + 1);
            current = new Understanding(0, needed);
        }
        current = new Understanding(current.uses() + 1, current.needed());

        if (current.uses() < current.needed()) {
            known.put(used.getId(), current);
            sendAnalysis(observer, used, current.uses(), current.needed(), ReplicationAnalysisPacket.ANALYZING);
            return;
        }

        known.remove(used.getId());
        CopiedAbilityHelper.addAbility(observer, new CopiedAbilityComponent.CopiedAbilityData(used.getId(), "replicated", -1, null));
        sendAnalysis(observer, used, current.needed(), current.needed(), ReplicationAnalysisPacket.REPLICATED);
        level.playSound(null, observer.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1, 1.4f);
        ParticleUtil.spawnParticles(level, ParticleTypes.END_ROD, observer.getEyePosition(), 30, .4, .08);
    }

    private static void sendAnalysis(ServerPlayer observer, Ability used, int uses, int needed, int state) {
        PacketHandler.sendToPlayer(observer, new ReplicationAnalysisPacket(used.getId(), uses, needed, state));
    }
}
