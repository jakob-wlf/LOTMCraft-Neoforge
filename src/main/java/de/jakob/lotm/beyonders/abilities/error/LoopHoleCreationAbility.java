package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.*;
import de.jakob.lotm.entity.custom.ability_entities.TimeChangeEntity;
import de.jakob.lotm.util.helper.FxHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import de.jakob.lotm.beyonders.abilities.visionary.VirtualPersonaAbility;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.rendering.effectRendering.EffectIds;
import de.jakob.lotm.rendering.effectRendering.EffectManager;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class LoopHoleCreationAbility extends SelectableAbility {

    private static final Map<UUID, LoopholeData> activeLoopholes = new ConcurrentHashMap<>();

    private static final ThreadLocal<Boolean> isRedirecting = ThreadLocal.withInitial(() -> false);

    private static final Map<UUID, UUID> entityToLoophole = new ConcurrentHashMap<>();

    public LoopHoleCreationAbility(String id) {
        super(id, 16f);
        autoClear = false;
        canBeShared = false;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(7, 13, 16));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(24000f, 10000f, 6700f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    public float getSpiritualityCost() {
        return 6000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.loophole.create",
                "ability.lotmcraft.loophole.find",
                "ability.lotmcraft.loophole.exploit",
                "ability.lotmcraft.loophole.reality",
                "ability.lotmcraft.loophole.dimension",
                "ability.lotmcraft.loophole.time"
        };
    }

    @Override
    public String[] getSequenceDependantAbilityNames(int sequence) {
        String[] all = getAllAbilityNames();
        return sequence <= 1 ? all : Arrays.copyOf(all, 5);
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        switch (selectedAbility) {
            case 0 -> createLoophole(level, entity);
            case 1 -> findLoophole(serverLevel, entity);
            case 2 -> exploitLoophole(serverLevel, entity);
            case 3 -> realityLoophole(serverLevel, entity);
            case 4 -> dimensionLoophole(serverLevel, entity);
            case 5 -> timeLoophole(serverLevel, entity);
        }
    }

    private void message(LivingEntity entity, String key, Object... args) {
        AbilityUtil.sendActionBar(entity, Component.translatable(key, args).withColor(ErrorUtil.FATE_COLOR));
    }

    @Nullable
    private LivingEntity getLoopholeTarget(LivingEntity entity) {
        return AbilityUtil.getTargetEntity(entity, Math.max(40, baseDistance), 2, false, false, true, false);
    }

    private void findLoophole(ServerLevel level, LivingEntity entity) {
        clearArtifactScaling(entity);
        LivingEntity target = getLoopholeTarget(entity);
        if (target == null) {
            message(entity, "ability.lotmcraft.loophole.no_target");
            return;
        }

        FxHandler.playOnEntity(level, "loophole_crack", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.8));
        LivingEntity main = ErrorUtil.getMainBody(level, target);

        if (main != null) {
            if (target.getHealth() > target.getMaxHealth() * 0.5f) {
                message(entity, "ability.lotmcraft.loophole.second_body_too_whole");
                return;
            }
            main.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 15, 0, false, false, false));
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("ability.lotmcraft.loophole.true_body_found", main.getName(),
                        main.blockPosition().getX(), main.blockPosition().getY(), main.blockPosition().getZ(),
                        main.level().dimension().location().getPath()).withColor(ErrorUtil.FATE_COLOR));
            }
            return;
        }

        if (AbilityUtil.isTargetSignificantlyStronger(entity, target)) {
            message(entity, "ability.lotmcraft.loophole.resisted");
            return;
        }

        LoopholeHandler.markLoopholeFound(target, entity, 20 * 60);
        message(entity, "ability.lotmcraft.loophole.found", target.getName());
    }

    private static boolean isInvincible(LivingEntity target) {
        MobEffectInstance resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
        return (target.isInvulnerable() && !(target instanceof Player player && player.isCreative()))
                || AbilityUtil.invul.containsKey(target.getUUID())
                || DeceitAbility.cannotBeHarmed.contains(target.getUUID())
                || (resistance != null && resistance.getAmplifier() >= 3);
    }

    private void exploitLoophole(ServerLevel level, LivingEntity entity) {
        clearArtifactScaling(entity);
        LivingEntity target = getLoopholeTarget(entity);
        if (target == null) {
            message(entity, "ability.lotmcraft.loophole.no_target");
            return;
        }
        if (AbilityUtil.isTargetSignificantlyStronger(entity, target) && !LoopholeHandler.isLoopholeFound(target, entity)) {
            message(entity, "ability.lotmcraft.loophole.resisted");
            return;
        }

        FxHandler.playOnEntity(level, "loophole_crack", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.8));

        if (isInvincible(target)) {
            if (!(target instanceof Player)) target.setInvulnerable(false);
            AbilityUtil.invul.remove(target.getUUID());
            DeceitAbility.cannotBeHarmed.remove(target.getUUID());
            MobEffectInstance resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null && resistance.getAmplifier() >= 3) target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            level.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
            message(entity, "ability.lotmcraft.loophole.invincibility_broken", target.getName());
            return;
        }

        float damage = Math.max(6f, target.getMaxHealth() * 0.15f) * multiplier(entity);
        LoopholeHandler.exploitNextAbility(target, entity, damage, 20 * 60);
        message(entity, "ability.lotmcraft.loophole.exploit_set", target.getName());
    }

    private static boolean isTemporarySummon(Entity entity) {
        if (entity instanceof Player) return false;
        if (entity.getPersistentData().getBoolean("VoidSummoned")) return true;
        if (!(entity instanceof LivingEntity) && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)) {
            return entity.getClass().getName().startsWith("de.jakob.lotm.entity.custom.ability_entities");
        }
        return false;
    }

    private void realityLoophole(ServerLevel level, LivingEntity entity) {
        clearArtifactScaling(entity);
        Vec3 center = AbilityUtil.getTargetLocation(entity, Math.max(40, baseDistance), 2);
        List<Entity> candidates = level.getEntities(entity, net.minecraft.world.phys.AABB.ofSize(center, 12, 12, 12), LoopHoleCreationAbility::isTemporarySummon);

        if (candidates.isEmpty()) {
            message(entity, "ability.lotmcraft.loophole.no_summon");
            return;
        }

        int banished = 0;
        for (Entity summon : candidates) {
            if (summon instanceof de.jakob.lotm.entity.custom.ability_entities.wheel_of_fortune_pathway.CycleOfFateEntity) continue;
            if (summon instanceof LivingEntity living && AbilityUtil.isTargetSignificantlyStronger(entity, living)) continue;

            if (summon instanceof TimeChangeEntity timeChange && random.nextFloat() < 0.4f) {
                timeChange.setDuration(Math.max(1, timeChange.getDuration() / 5));
                continue;
            }

            level.sendParticles(ParticleTypes.REVERSE_PORTAL, summon.getX(), summon.getY() + summon.getBbHeight() / 2, summon.getZ(), 40, 0.5, 0.6, 0.5, 0.05);
            FxHandler.play(level, "loophole_crack", summon.position().add(0, summon.getBbHeight() / 2, 0), 1.2);
            summon.discard();
            banished++;
        }
        message(entity, "ability.lotmcraft.loophole.banished", banished);
    }

    private void dimensionLoophole(ServerLevel level, LivingEntity entity) {
        clearArtifactScaling(entity);
        if (!(entity instanceof ServerPlayer player)) return;
        BlockPos pos = AbilityUtil.getTargetBlock(player, 8, false);
        if (pos == null || !LoopholeHandler.isDoor(level, pos)) {
            message(player, "ability.lotmcraft.loophole.no_door");
            return;
        }
        FxHandler.play(level, "loophole_crack", pos.getCenter(), 1);
        message(player, LoopholeHandler.selectDoor(player, pos));
    }

    private void timeLoophole(ServerLevel level, LivingEntity entity) {
        clearArtifactScaling(entity);
        LivingEntity target = getLoopholeTarget(entity);
        if (target == null) {
            message(entity, "ability.lotmcraft.loophole.no_target");
            return;
        }
        if (AbilityUtil.isTargetSignificantlyStronger(entity, target)) {
            message(entity, "ability.lotmcraft.loophole.resisted");
            return;
        }

        FxHandler.playOnEntity(level, "time_loophole", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));

        ToggleAbility.cleanUp(level, target);
        for (MobEffectInstance effect : new ArrayList<>(target.getActiveEffects())) {
            if (!effect.isInfiniteDuration()) target.removeEffect(effect.getEffect());
        }
        AbilityUtil.invul.remove(target.getUUID());
        DeceitAbility.cannotBeHarmed.remove(target.getUUID());
        DeceitAbility.cannotBeTargeted.remove(target.getUUID());
        TimeHandler.dissolveOwnedTime(target.getUUID());
        DeceptionTrapHandler.remove(target.getUUID());

        for (Entity owned : level.getEntities(target, target.getBoundingBox().inflate(128), e ->
                (e instanceof TimeChangeEntity timeChange && target.getUUID().equals(timeChange.getCasterUUID()))
                        || (e.getPersistentData().getBoolean("VoidSummoned") && e.getPersistentData().hasUUID("VoidSummonOwner")
                        && target.getUUID().equals(e.getPersistentData().getUUID("VoidSummonOwner"))))) {
            owned.discard();
        }

        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.6f);
        message(entity, "ability.lotmcraft.loophole.time_dissolved", target.getName());
    }

    private void createLoophole(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ProhibitionHandler.IsInTheftZone(entity.position(), (ServerLevel) level, AbilityUtil.getSeqWithArt(entity, this)))
            return;
        Vec3 targetLoc = AbilityUtil.getTargetLocation(entity, baseDistance, 2);
        UUID loopholeId = UUID.randomUUID();

        if(entity instanceof ServerPlayer serverPlayer) {
            EffectManager.playEffect(EffectIds.LOOPHOLE, targetLoc.x, targetLoc.y, targetLoc.z, serverPlayer, entity);
        }

        LoopholeData loopholeData = new LoopholeData(
                loopholeId,
                entity.getUUID(),
                targetLoc,
                3.0 * multiplier(entity), // radius
                serverLevel,
                System.currentTimeMillis() + (20 * 7 * 50) // 7 seconds in milliseconds
        );
        activeLoopholes.put(loopholeId, loopholeData);

        ServerScheduler.scheduleForDuration(0, 2, (20 * 7), () -> {
            updateEntitiesInLoophole(loopholeData);

            AbilityUtil.getNearbyEntities(entity, serverLevel, targetLoc, 3).forEach(e -> {
                double resistance = AbilityUtil.getSequenceResistanceFactor(entity, e);
                if (ThreadLocalRandom.current().nextDouble() >= resistance) {
                    e.teleportTo(targetLoc.x, targetLoc.y, targetLoc.z);
                }
            });
        });

        ServerScheduler.scheduleForDuration(0, 45, (20 * 7), () -> {
            AbilityUtil.getNearbyEntities(entity, serverLevel, targetLoc, 3).forEach(e -> {
                if (BeyonderData.isBeyonder(e))
                    TheftHandler.performAbilityTheft(serverLevel, entity, e, random, true, this);
            });
        });

        ServerScheduler.scheduleDelayed(20 * 14, () -> {
            removeLoophole(loopholeId);
            clearArtifactScaling(entity);
        });
    }

    private static void updateEntitiesInLoophole(LoopholeData loopholeData) {
        if (loopholeData.level == null) return;

        List<LivingEntity> entitiesInRange = AbilityUtil.getNearbyEntities(
                null,
                loopholeData.level,
                loopholeData.center,
                loopholeData.radius
        );

        entityToLoophole.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(loopholeData.id)) {
                boolean stillInside = entitiesInRange.stream()
                        .anyMatch(e -> e.getUUID().equals(entry.getKey()));
                return !stillInside;
            }
            return false;
        });

        for (LivingEntity entity : entitiesInRange) {
            UUID entityId = entity.getUUID();

            // Only add if not already in another loophole
            if (!entityToLoophole.containsKey(entityId)) {
                entityToLoophole.put(entityId, loopholeData.id);
            }
        }

        var ownerEntity = loopholeData.level.getEntity(loopholeData.creatorId);
//        if (ownerEntity instanceof ServerPlayer owner) {
//            if (!BeyonderData.getPathway(owner).equals("error") || BeyonderData.getSequence(owner) != 2) return;
//            var set = Seq1.map.get(ownerEntity.getUUID());
//            var factions = BeyonderData.factionStorage.getPartOfFaction(owner.getName().getString());
//
//            FactionCore nation = null;
//            FactionCore church = null;
//
//            switch (factions.size()) {
//                case 1 -> {
//                    nation = factions.getFirst();
//                }
//                case 2 -> {
//                    nation = factions.getFirst();
//                    church = factions.getLast();
//                }
//            }
//
//            boolean allGood = true;
//            for (var entity : entitiesInRange) {
//                if (entity instanceof ServerPlayer player) {
//                    if(BeyonderData.getSequence(player) > 7) continue;
//
//                    if (nation != null && nation.isPartOfFaction(player.getName().getString()))
//                        allGood = false;
//                    else if(church != null && church.isPartOfFaction(player.getName().getString()))
//                        allGood = false;
//
//                    if(allGood){
//                        allGood = !AllyUtil.areAllies(player, owner);
//                    }
//
//                    if(allGood)
//                        set.add(player.getUUID());
//                }
//            }
//
//            Seq1.map.put(owner.getUUID(), set);
//        }
    }

    private static void removeLoophole(UUID loopholeId) {
        activeLoopholes.remove(loopholeId);
        entityToLoophole.entrySet().removeIf(entry -> entry.getValue().equals(loopholeId));
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        if (isRedirecting.get()) return;

        LivingEntity user = event.getEntity();
        if (user == null) return;

        UUID entityId = user.getUUID();

        UUID loopholeId = entityToLoophole.get(entityId);
        if (loopholeId == null) return;

        LoopholeData loopholeData = activeLoopholes.get(loopholeId);
        if (loopholeData == null) {
            entityToLoophole.remove(entityId);
            return;
        }

        if (System.currentTimeMillis() > loopholeData.expirationTime) {
            removeLoophole(loopholeId);
            return;
        }

        LivingEntity creator = loopholeData.getCreator();
        if (creator == null || !creator.isAlive()) {
            return;
        }

        if (entityId.equals(loopholeData.creatorId)) {
            return;
        }

        double escapeChance = AbilityUtil.getSequenceResistanceFactor(creator, user);
        if (ThreadLocalRandom.current().nextDouble() < escapeChance) {
            return;
        }

        event.setCanceled(true);

        Ability ability = event.getAbility();
        if (ability != null && user.level() instanceof ServerLevel serverLevel && ability.canBeUsedByNPC
                && !(ability instanceof LoopHoleCreationAbility)
                && !(ability instanceof AvatarCreationAbility)
                && !(ability instanceof VirtualPersonaAbility)) {
            isRedirecting.set(true);
            try {
                ability.useAbility(serverLevel, creator, false, false, true, false);
            } finally {
                isRedirecting.set(false);
            }
        }
    }

    private static class LoopholeData {
        final UUID id;
        final UUID creatorId;
        final Vec3 center;
        final double radius;
        final ServerLevel level;
        final long expirationTime;

        LoopholeData(UUID id, UUID creatorId, Vec3 center, double radius, ServerLevel level, long expirationTime) {
            this.id = id;
            this.creatorId = creatorId;
            this.center = center;
            this.radius = radius;
            this.level = level;
            this.expirationTime = expirationTime;
        }

        LivingEntity getCreator() {
            if (level == null) return null;

            Entity entity = level.getEntity(creatorId);
            if (entity instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
            return null;
        }
    }

    private void stealAbilities(LivingEntity entity, LivingEntity target) {
        if (!BeyonderData.isBeyonder(target)) {
            return;
        }

//        ArrayList<AbilityItem> stealableAbilities = new ArrayList<>(AbilityItemHandler.ITEMS.getEntries().stream().filter(abilityEntry -> {
//            if(!(abilityEntry.get() instanceof AbilityItem abilityItem) || abilityEntry.get() instanceof ToggleAbilityItem) return false;
//            if(!abilityItem.canBeCopied) return false;
//            if(BeyonderData.isSpecificAbilityDisabled(target, abilityItem.getDescriptionId())) return false;
//
//            return abilityItem.canUse(target, true);
//        }).map(abilityEntry -> (AbilityItem) abilityEntry.get()).toList());
//
//        if(stealableAbilities.isEmpty()) {
//            return;
//        }
//
//        if(AbilityUtil.isTargetSignificantlyStronger(entity, target)) {
//            return;
//        }
//
//        if(doesTheftFail(BeyonderData.getSequence(entity), BeyonderData.getSequence(target))) {
//            return;
//        }
//
//        if(entity instanceof ServerPlayer serverPlayer)  {
//            EffectManager.playEffect(EffectIds.ABILITY_THEFT, target.position().x, target.position().y + target.getEyeHeight(), target.position().z, serverPlayer);
//        }
//
//        List<AbilityItem> stolenItems = new ArrayList<>();
//        int abilityCount = 3;
//        int abilityUses = 4;
//        for(int i = 0; i < abilityCount; i++) {
//            if(stealableAbilities.isEmpty()) {
//                break;
//            }
//            int index = random.nextInt(stealableAbilities.size());
//            AbilityItem stolenAbility = stealableAbilities.get(index);
//            stealableAbilities.remove(index);
//            stolenItems.add(stolenAbility);
//            BeyonderData.disableSpecificAbilityWithTimeLimit(target, "ability_theft_disable", stolenAbility.getDescriptionId(), 120 * 1000L);
//        }
//
//        for(AbilityItem stolenItem : stolenItems) {
//            ItemStack stolenStack = new ItemStack(stolenItem);
//            stolenStack.set(ModDataComponents.ABILITY_USES, abilityUses);
//            stolenStack.set(ModDataComponents.IS_STOLEN, true);
//
//
//            if(entity instanceof Player player && !player.getInventory().add(stolenStack)) {
//                player.drop(stolenStack, false);
//            }
//
//        }
    }

    private boolean doesTheftFail(int userSeq, int targetSeq) {
        if (targetSeq > userSeq) {
            return false;
        }

        int difference = userSeq - targetSeq;

        double baseFailPerStep = 0.15;

        double failChance = difference * baseFailPerStep;

        failChance = Math.min(Math.max(failChance, 0.0), 0.95);

        return random.nextDouble() < failChance;
    }
}
