package de.jakob.lotm.beyonders.abilities.justiciar;

import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LawAbility extends SelectableAbility {

    public static final Set<UUID>         SOLACE_KILLED      = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final DustParticleOptions GOLD_DUST      = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.0f), 1.4f);
    private static final DustParticleOptions PALE_GOLD_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.93f, 0.5f), 1.0f);
    private static final DustParticleOptions DIM_DUST       = new DustParticleOptions(new Vector3f(0.45f, 0.35f, 0.05f), 1.2f);
    private static final DustParticleOptions BRIGHT_DUST    = new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.6f), 1.6f);
    private static final DustParticleOptions HOLY_DUST      = new DustParticleOptions(new Vector3f(1.0f, 1.0f, 0.85f), 1.5f);

    public LawAbility(String id) {
        super(id, 5f, "law");
        interactionRadius = 40;
        hasOptimalDistance = false;
        postsUsedAbilityEventManually = true;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("justiciar", 4));
    }

    @Override
    protected float getSpiritualityCost() {
        return 800;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.law.weaken_mysticism",
                "ability.lotmcraft.law.enhance_mysticism",
                "ability.lotmcraft.law.solace"
        };
    }

    @Override
    public String[] getSequenceDependantAbilityNames(int sequence) {
        String[] names = getAllAbilityNames();
        return sequence >= 4 ? new String[]{names[0], names[1]} : names;
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (level.isClientSide) return;
        int sequence = BeyonderData.getSequence(entity);
        if (abilityIndex < 0 || abilityIndex >= getSequenceDependantAbilityNames(sequence).length) return;
        ServerLevel serverLevel = (ServerLevel) level;

        switch (abilityIndex) {
            case 0 -> weakenMysticism(serverLevel, entity);
            case 1 -> enhanceMysticism(serverLevel, entity);
            case 2 -> solace(serverLevel, entity);
        }

        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel, entity.position(), entity, this, interactionFlags, interactionRadius, 20 * 2));
    }

    private void weakenMysticism(ServerLevel serverLevel, LivingEntity entity) {
        int duration = (int) (20 * 30 * multiplier(entity));
        List<LivingEntity> nearby = AbilityUtil.getNearbyEntities(entity, serverLevel, entity.position(), 40);

        nearby.stream()
                .filter(BeyonderData::isBeyonder)
                .forEach(e -> e.getData(ModAttachments.MULTIPLIER_MODIFIER_COMPONENT)
                        .addMultiplierForTime("law_weaken", 0.25f, duration));
        entity.getData(ModAttachments.MULTIPLIER_MODIFIER_COMPONENT)
                .addMultiplierForTime("law_weaken", 0.25f, duration);

        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.BEACON_DEACTIVATE,   SoundSource.PLAYERS, 1.2f, 0.45f);
        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.7f, 0.6f);
        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.4f);

        Vec3 center = entity.position().add(0, 1, 0);
        ParticleUtil.spawnSphereParticles(serverLevel, DIM_DUST,  center, 1.5, 30);
        ParticleUtil.spawnSphereParticles(serverLevel, GOLD_DUST, center, 1.0, 18);
        serverLevel.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 20, 0.6, 0.6, 0.6, 0.03);

        Location casterLoc = new Location(entity.position(), serverLevel);
        ServerScheduler.scheduleForDuration(0, 6, 40, () -> {
            for (LivingEntity e : nearby) {
                if (!e.isAlive()) continue;
                Vec3 ep = e.position().add(0, 1, 0);
                serverLevel.sendParticles(DIM_DUST, ep.x, ep.y, ep.z, 2, 0.25, 0.3, 0.25, 0);
                serverLevel.sendParticles(ParticleTypes.SMOKE, ep.x, ep.y, ep.z, 1, 0.15, 0.2, 0.15, 0.01);
            }
        }, null, serverLevel, () -> AbilityUtil.getTimeInArea(entity, casterLoc));

        broadcastToNearby(serverLevel, entity, Component.translatable("ability.lotmcraft.law.weaken_declared").withStyle(ChatFormatting.GOLD));
    }

    private void enhanceMysticism(ServerLevel serverLevel, LivingEntity entity) {
        int duration = (int) (20 * 60 * multiplier(entity));
        List<LivingEntity> nearby = AbilityUtil.getNearbyEntities(entity, serverLevel, entity.position(), 40);

        nearby.stream()
                .filter(BeyonderData::isBeyonder)
                .forEach(e -> e.getData(ModAttachments.MULTIPLIER_MODIFIER_COMPONENT)
                        .addMultiplierForTime("law_enhance", 2.5f, duration));
        entity.getData(ModAttachments.MULTIPLIER_MODIFIER_COMPONENT)
                .addMultiplierForTime("law_enhance", 2.5f, duration);

        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE,      SoundSource.PLAYERS, 1.0f, 0.55f);
        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.TOTEM_USE,            SoundSource.PLAYERS, 0.6f, 0.5f);
        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.6f);

        Vec3 center = entity.position().add(0, 1, 0);
        ParticleUtil.spawnSphereParticles(serverLevel, BRIGHT_DUST, center, 2.0, 40);
        ParticleUtil.spawnSphereParticles(serverLevel, GOLD_DUST,   center, 1.3, 26);
        ParticleUtil.spawnCircleParticles(serverLevel, GOLD_DUST,   entity.position().add(0, 0.05, 0), 1.5, 28);
        serverLevel.sendParticles(ParticleTypes.ENCHANT, center.x, center.y, center.z, 50, 0.8, 0.8, 0.8, 0.15);

        Location casterLoc = new Location(entity.position(), serverLevel);
        ServerScheduler.scheduleForDuration(0, 5, 50, () -> {
            for (LivingEntity e : nearby) {
                if (!e.isAlive()) continue;
                Vec3 ep = e.position().add(0, 1, 0);
                serverLevel.sendParticles(BRIGHT_DUST,       ep.x, ep.y, ep.z, 2, 0.2, 0.35, 0.2, 0);
                serverLevel.sendParticles(ParticleTypes.ENCHANT, ep.x, ep.y, ep.z, 3, 0.3, 0.4, 0.3, 0.08);
            }
        }, null, serverLevel, () -> AbilityUtil.getTimeInArea(entity, casterLoc));

        broadcastToNearby(serverLevel, entity, Component.translatable("ability.lotmcraft.law.enhance_declared").withStyle(ChatFormatting.GOLD));
    }

    private void solace(ServerLevel serverLevel, LivingEntity caster) {
        broadcastToNearby(serverLevel, caster, Component.translatable("ability.lotmcraft.law.solace_declared").withStyle(ChatFormatting.GOLD));

        serverLevel.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE,       SoundSource.PLAYERS, 1.5f, 0.4f);
        serverLevel.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,  SoundSource.PLAYERS, 1.2f, 0.35f);
        serverLevel.playSound(null, caster.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,    SoundSource.PLAYERS, 0.6f, 1.1f);

        Vec3 casterCenter = caster.position().add(0, 1, 0);
        ParticleUtil.spawnSphereParticles(serverLevel, HOLY_DUST,  casterCenter, 2.0, 36);
        ParticleUtil.spawnSphereParticles(serverLevel, GOLD_DUST,  casterCenter, 1.2, 22);
        ParticleUtil.spawnCircleParticles(serverLevel, HOLY_DUST,  caster.position().add(0, 0.05, 0), 2.5, 32);
        serverLevel.sendParticles(ParticleTypes.ENCHANT, casterCenter.x, casterCenter.y, casterCenter.z, 60, 1.0, 0.8, 1.0, 0.18);

        List<LivingEntity> nearby = AbilityUtil.getNearbyEntities(caster, serverLevel, caster.position(), 40 * multiplier(caster));
        for (LivingEntity e : nearby) {
            if (!e.getType().is(EntityTypeTags.UNDEAD)) continue;

            Vec3 ep = e.position().add(0, 1, 0);
            ParticleUtil.spawnSphereParticles(serverLevel, HOLY_DUST, ep, 1.0, 24);
            ParticleUtil.spawnSphereParticles(serverLevel, GOLD_DUST, ep, 0.7, 16);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, ep.x, ep.y, ep.z, 30, 0.4, 0.4, 0.4, 0.14);
            serverLevel.sendParticles(ParticleTypes.FLASH, ep.x, ep.y, ep.z, 2, 0.2, 0.2, 0.2, 0);
            serverLevel.playSound(null, e.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.6f);

            SOLACE_KILLED.add(e.getUUID());
            if (e instanceof ServerPlayer) {
                e.hurt(serverLevel.damageSources().magic(), Float.MAX_VALUE);
            } else {
                e.kill();
            }
            UUID uuid = e.getUUID();
            ServerScheduler.scheduleDelayed(2, () -> SOLACE_KILLED.remove(uuid), serverLevel);
        }
    }

    private static void broadcastToNearby(ServerLevel level, LivingEntity source, Component msg) {
        level.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (p.level().equals(level) && p.distanceTo(source) <= 40) {
                p.sendSystemMessage(msg);
            }
        });
    }

}
