package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.CancelPhotonEntityEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayPhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayPhotonEntityEffectPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AnimationUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class LightAuthorityAbility extends SelectableAbility {

    private static final double BEAM_AREA_RADIUS = 120;
    private static final double BEAM_HIT_RADIUS = 4;
    private static final double BEAM_HEIGHT = 80;
    private static final int BEAM_DURATION = 20 * 8;
    private static final int BEAM_IMPACT_DELAY = 5;

    private static final int PRISON_DURATION = 20 * 10;

    public LightAuthorityAbility(String id) {
        super(id, 8f, "purification", "purification_holy", "light_source", "light_strong", "light_weak");
        canBeShared = false;
        canBeCopied = false;
        interactionRadius = BEAM_AREA_RADIUS;
        interactionCacheTicks = BEAM_DURATION;

        baseDamage = 30f;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 0));
    }

    @Override
    protected float getSpiritualityCost() {
        return 9000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.light_authority.beams_of_light",
                "ability.lotmcraft.light_authority.light_traversal",
                "ability.lotmcraft.light_authority.prison_of_light"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if(!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        switch (abilityIndex) {
            case 0 -> beamsOfLight(serverLevel, entity);
            case 1 -> lightTraversal(serverLevel, entity);
            case 2 -> prisonOfLight(serverLevel, entity);
        }
    }

    private void beamsOfLight(ServerLevel level, LivingEntity entity) {
        Vec3 center = entity.position();
        float damage = baseDamage;
        boolean griefing = BeyonderData.isGriefingEnabled(entity);

        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, entity.getSoundSource(), 20f, .6f);
        if(entity instanceof Player player) AnimationUtil.playOpenArmAnimation(player);

        ServerScheduler.scheduleForDuration(0, 2, BEAM_DURATION, () -> {
            for(int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = Math.sqrt(random.nextDouble()) * BEAM_AREA_RADIUS;
                strikeBeam(level, entity, center.x + Math.cos(angle) * distance, center.z + Math.sin(angle) * distance, damage, griefing);
            }

            if(random.nextInt(3) == 0) {
                AbilityUtil.getNearbyEntities(entity, level, center, BEAM_AREA_RADIUS).stream()
                        .filter(target -> AbilityUtil.mayDamage(entity, target))
                        .forEach(target -> {
                            if(random.nextInt(3) == 0) {
                                strikeBeam(level, entity, target.getX() + random.nextGaussian(), target.getZ() + random.nextGaussian(), damage, griefing);
                            }
                        });
            }
        }, null, level, () -> AbilityUtil.getTimeInArea(entity, new Location(center, level)));
    }

    private void strikeBeam(ServerLevel level, LivingEntity caster, double x, double z, float damage, boolean griefing) {
        BlockPos column = BlockPos.containing(x, 0, z);
        if(!level.isLoaded(column)) return;

        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        Vec3 impact = new Vec3(x, groundY, z);

        PacketHandler.sendToNearbyPlayers(
                new PlayPhotonBlockEffectPacket("holy_beam", BlockPos.containing(impact), 0, 2.5, 0, 1, null, -1, false, true, new Vec3(1, 3, 1)),
                level,
                impact,
                256
        );

        if(random.nextInt(6) == 0) {
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.BEACON_DEACTIVATE, caster.getSoundSource(), 4f, 1.8f);
        }

        ServerScheduler.scheduleDelayed(BEAM_IMPACT_DELAY, () -> impactBeam(level, caster, impact, damage, griefing), level);
    }

    private void impactBeam(ServerLevel level, LivingEntity caster, Vec3 impact, float damage, boolean griefing) {
        double x = impact.x;
        double z = impact.z;
        AABB columnArea = new AABB(x - BEAM_HIT_RADIUS, impact.y - 2, z - BEAM_HIT_RADIUS, x + BEAM_HIT_RADIUS, impact.y + BEAM_HEIGHT, z + BEAM_HIT_RADIUS);
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, columnArea, target -> {
            double dx = target.getX() - x;
            double dz = target.getZ() - z;
            return dx * dx + dz * dz <= BEAM_HIT_RADIUS * BEAM_HIT_RADIUS && AbilityUtil.mayDamage(caster, target);
        });

        for(LivingEntity target : hit) {
            if(AbilityUtil.isUndeadOrEvil(target)) {
                target.hurt(ModDamageTypes.source(level, ModDamageTypes.PURIFICATION, caster), damage * 1.5f);
            } else {
                target.hurt(ModDamageTypes.source(level, ModDamageTypes.LIGHT, caster), damage);
            }
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 3, 0, false, false, false));
        }

        if(griefing) {
            scorchImpact(level, impact);
        }
    }

    private void scorchImpact(ServerLevel level, Vec3 impact) {
        double craterRadius = 3.5 + random.nextDouble() * 2;
        Vec3 craterCenter = impact.subtract(0, craterRadius * .4, 0);

        for(BlockPos pos : AbilityUtil.getBlocksInSphereRadius(level, craterCenter, craterRadius, true, true, false)) {
            BlockState state = level.getBlockState(pos);
            if(state.getDestroySpeed(level, pos) < 0) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        for(BlockPos pos : AbilityUtil.getBlocksInSphereRadius(level, craterCenter, craterRadius + 2, true, true, true)) {
            if(random.nextFloat() > .35f) continue;

            BlockState state = level.getBlockState(pos);
            if(state.is(BlockTags.SAND)) {
                level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
                continue;
            }

            BlockPos above = pos.above();
            if(level.getBlockState(above).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, above)) {
                level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }

    private void lightTraversal(ServerLevel level, LivingEntity entity) {
        Vec3 start = entity.position();
        Vec3 target = AbilityUtil.getTargetLocation(entity, baseDistance, 2);
        Vec3 path = target.subtract(start);
        double length = path.length();
        if(length < 1) return;

        Vec3 direction = path.normalize();
        Vec3 end = start.add(direction.scale(Math.max(0, length - 1.2)));
        float damage = baseDamage * .6f;

        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.toVector3f());
        PacketHandler.sendToNearbyPlayers(
                new PlayPhotonBlockEffectPacket("light_authority_light_travel", BlockPos.containing(start), 0, entity.getBbHeight() / 2, 0, 1, rotation, -1, false, true, new Vec3(1, 1, length)),
                level,
                start,
                256
        );
        PacketHandler.sendToNearbyPlayers(
                new PlayPhotonBlockEffectPacket("light_authority_light_burst", BlockPos.containing(start), 0, 0, 0, 1, null, -1, false, true, null),
                level,
                start,
                256
        );

        level.playSound(null, start.x, start.y, start.z, SoundEvents.BEACON_ACTIVATE, entity.getSoundSource(), 5f, 2f);

        for(double d = 0; d <= length; d += 1.5) {
            Vec3 point = start.add(direction.scale(d)).add(0, entity.getBbHeight() / 2, 0);
            AbilityUtil.damageNearbyEntities(level, entity, 2.5, ModDamageTypes.LIGHT, damage, point, true, false, false, 10);
        }

        entity.stopRiding();
        entity.teleportTo(end.x, end.y, end.z);
        entity.resetFallDistance();

        PacketHandler.sendToNearbyPlayers(
                new PlayPhotonBlockEffectPacket("light_authority_light_burst", BlockPos.containing(end), 0, 0, 0, 1, null, -1, false, true, null),
                level,
                end,
                256
        );
        level.playSound(null, end.x, end.y, end.z, SoundEvents.BEACON_ACTIVATE, entity.getSoundSource(), 5f, 2f);
    }

    private void prisonOfLight(ServerLevel level, LivingEntity entity) {
        LivingEntity target = AbilityUtil.getTargetEntity(entity, baseDistance, 2.5f);
        if(target == null) return;

        if(AbilityUtil.isTargetSignificantlyStronger(entity, target)) return;

        Vec3 prisonCenter = target.position();
        float damage = baseDamage * .12f;
        UUID effectId = UUID.randomUUID();

        PacketHandler.sendToNearbyPlayers(
                new PlayPhotonEntityEffectPacket("light_authority_prison", target.getId(), 0, -(target.getEyeHeight()), 0, 1.5, null, false, true, effectId, false),
                level,
                prisonCenter,
                256
        );
        level.playSound(null, prisonCenter.x, prisonCenter.y, prisonCenter.z, SoundEvents.BEACON_POWER_SELECT, entity.getSoundSource(), 6f, 1.4f);

        AbilityUtil.sealAllTargetAbilities(entity, target, "light_authority", PRISON_DURATION);

        AtomicReference<UUID> taskId = new AtomicReference<>();
        taskId.set(ServerScheduler.scheduleForDuration(0, 1, PRISON_DURATION, () -> {
            if(!target.isAlive() || target.level() != level) {
                PacketHandler.sendToAllPlayersInSameLevel(new CancelPhotonEntityEffectPacket(effectId), level);
                ServerScheduler.cancel(taskId.get());
                return;
            }

            if(target.position().distanceToSqr(prisonCenter) > .25) {
                target.teleportTo(prisonCenter.x, prisonCenter.y, prisonCenter.z);
            }
            target.setDeltaMovement(Vec3.ZERO);
            target.resetFallDistance();

            if(target.tickCount % 10 == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false, false));

                if(AbilityUtil.isUndeadOrEvil(target)) {
                    target.hurt(ModDamageTypes.source(level, ModDamageTypes.PURIFICATION, entity), damage * 2);
                } else {
                    target.hurt(ModDamageTypes.source(level, ModDamageTypes.LIGHT, entity), damage);
                }
            }
        }, () -> PacketHandler.sendToAllPlayersInSameLevel(new CancelPhotonEntityEffectPacket(effectId), level),
                level, () -> AbilityUtil.getTimeInArea(entity, new Location(prisonCenter, level))));
    }
}
