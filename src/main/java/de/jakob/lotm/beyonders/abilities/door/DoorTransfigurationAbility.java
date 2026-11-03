package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.TransformationComponent;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DoorTransfigurationAbility extends ToggleAbility {
    private static final HashMap<UUID, Long> lastEscape = new HashMap<>();

    private static final int ESCAPE_COOLDOWN = 10;
    private static final int DOOR_BURST_DELAY = 35;
    private static final double BURST_RADIUS = 4.5;

    public DoorTransfigurationAbility(String id) {
        super(id, "escape");

        this.tickRate = 1;
        canBeReplicated = false;
        canBeCopied = false;
        canBeUsedInArtifact = false;
        canBeShared = false;

        baseDamage = 22f;
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if(level.isClientSide) {
            return;
        }

        if(BeyonderData.getSpirituality(entity) < getCost(BeyonderData.getSequence(entity))) {
            cancel((ServerLevel) level, entity);
        }
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if(level.isClientSide) return;
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if(level.isClientSide) {
            return;
        }

        lastEscape.remove(entity.getUUID());

        // players who still carry the old door transformation get turned back
        TransformationComponent transformationComponent = entity.getData(ModAttachments.TRANSFORMATION_COMPONENT);
        if(transformationComponent.isTransformed() &&
                transformationComponent.getTransformationIndex() >= TransformationComponent.TransformationType.DOOR_TRANSFIGURATION.getIndex() &&
                transformationComponent.getTransformationIndex() <= (TransformationComponent.TransformationType.DOOR_TRANSFIGURATION.getIndex() + 20)) {
            transformationComponent.setTransformedAndSync(false, entity);
        }
    }

    private static float getCost(int seq){
        return switch (seq){
            case 4 -> 1170f;
            case 3 -> 1150f;
            case 2 -> 1875;
            case 1 -> 3150f;
            case 0 -> 8100f;
            default -> 10000f;
        };
    }

    private static float getBurstDamageScale(int seq){
        return switch (seq){
            case 3 -> 1.1f;
            case 2 -> 2.25f;
            case 1 -> 2.8f;
            case 0 -> 3.5f;
            default -> 1f;
        };
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return Map.of("door", 4);
    }

    @Override
    protected float getSpiritualityCost() {
        return 14;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if(!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if(!((DoorTransfigurationAbility) LOTMCraft.abilityHandler.getById("door_transfiguration_ability")).isActiveForEntity(entity)) {
            return;
        }

        event.setNewDamage(0);

        // rapid hits (fire, poison, multi-hits) are absorbed without opening a new door every tick
        long gameTime = level.getGameTime();
        Long last = lastEscape.get(entity.getUUID());
        if(last != null && gameTime - last < ESCAPE_COOLDOWN) {
            return;
        }

        float spiritualityCost = getCost(BeyonderData.getSequence(entity));
        if(BeyonderData.getSpirituality(entity) >= spiritualityCost) {
            BeyonderData.reduceSpirituality(entity, spiritualityCost);
        }

        Entity attacker = event.getSource().getEntity();
        Vec3 newLoc = findTeleportLoc(entity, attacker);
        if(newLoc == null) {
            return;
        }
        lastEscape.put(entity.getUUID(), gameTime);

        Vec3 oldLoc = entity.position();
        Vec3 doorFacing = attacker != null && attacker != entity ? attacker.position().subtract(oldLoc) : entity.getLookAngle();
        double scale = Math.max(0.6, entity.getBbHeight() / 1.8);

        entity.teleportTo(newLoc.x, newLoc.y, newLoc.z);
        entity.resetFallDistance();

        FxHandler.play(level, "door_transfiguration_door", oldLoc, scale, FxHandler.yawTowards(doorFacing));
        FxHandler.play(level, "door_transfiguration_arrive", newLoc, scale);

        level.playSound(null, oldLoc.x, oldLoc.y, oldLoc.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, .8f);
        level.playSound(null, newLoc.x, newLoc.y, newLoc.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .5f, 1.4f);
        ServerScheduler.scheduleDelayed(20, () ->
                level.playSound(null, oldLoc.x, oldLoc.y, oldLoc.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.PLAYERS, .8f, .6f), level);
        DoorTransfigurationAbility ability = (DoorTransfigurationAbility) LOTMCraft.abilityHandler.getById("door_transfiguration_ability");
        double burstDamage = ability.baseDamage * getBurstDamageScale(BeyonderData.getSequence(entity)) * ability.multiplier(entity);
        Vec3 burstCenter = oldLoc.add(0, 1.65 * scale, 0);
        ServerScheduler.scheduleDelayed(DOOR_BURST_DELAY, () -> {
            AbilityUtil.damageNearbyEntities(level, entity, BURST_RADIUS * scale, ModDamageTypes.SPACE_DESTRUCTION, burstDamage, burstCenter, true, true);
            level.playSound(null, oldLoc.x, oldLoc.y, oldLoc.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.5f, 1.1f);
            level.playSound(null, oldLoc.x, oldLoc.y, oldLoc.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5f, 1.3f);
            level.playSound(null, oldLoc.x, oldLoc.y, oldLoc.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .7f, 1.5f);
        }, level);
    }

    private static Vec3 findTeleportLoc(LivingEntity entity, Entity attacker) {
        Level level = entity.level();
        RandomSource random = entity.getRandom();
        Vec3 origin = entity.position();
        Vec3 away = attacker != null && attacker != entity ? origin.subtract(attacker.position()).multiply(1, 0, 1) : Vec3.ZERO;
        Vec3 fallback = null;

        for(int i = 0; i < 60; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            if(i < 30 && away.lengthSqr() > 1.0E-4 && dir.dot(away.normalize()) < 0) {
                continue;
            }

            Vec3 candidate = origin.add(dir.scale(3 + random.nextDouble() * 3));
            for(int dy : new int[]{0, 1, -1, 2, -2}) {
                Vec3 pos = candidate.add(0, dy, 0);
                AABB box = entity.getBoundingBox().move(pos.subtract(origin));
                if(!level.noCollision(entity, box)) {
                    continue;
                }
                if(!level.noCollision(entity, box.move(0, -0.6, 0))) {
                    return pos;
                }
                if(fallback == null) {
                    fallback = pos;
                }
            }
        }

        return fallback;
    }
}
