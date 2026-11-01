package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.entity.custom.ability_entities.sun_pathway.SunEntity;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.AddEntityTagPacket;
import de.jakob.lotm.network.packets.toClient.CancelPhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayCancellablePhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayPhotonBlockEffectPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlaringSunAbility extends Ability {
    public FlaringSunAbility(String id) {
        super(id, 12, "purification", "purification_holy", "burning", "light_source", "light_strong", "light_weak");
        postsUsedAbilityEventManually = true;
        interactionRadius = 20;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(4, 5, 7, 10, 12));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(5000f, 2500f, 1500f, 800f, 780f));

        baseDamage = 4f;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 4));
    }

    @Override
    protected float getSpiritualityCost() {
        return 800;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        Vec3 targetPos = AbilityUtil.getTargetLocation(entity, baseDistance, 2);
        Vec3 startPos = targetPos.add(0, 5, 0);

        BlockPos blockPos = BlockPos.containing(startPos);
        BlockState state = level.getBlockState(blockPos);
        if(state.getCollisionShape(level, blockPos).isEmpty()) {
            level.setBlockAndUpdate(blockPos, Blocks.LIGHT.defaultBlockState());
        }

        if(BeyonderData.isGriefingEnabled(entity)) {
            AbilityUtil.getBlocksInSphereRadius((ServerLevel) level, targetPos, 9, true, true, false).forEach(
                    b -> level.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState())
            );

            AbilityUtil.getBlocksInSphereRadius((ServerLevel) level, targetPos, 9, true).forEach(
                    b -> level.setBlockAndUpdate(b, Blocks.FIRE.defaultBlockState())
            );

            AbilityUtil.getBlocksInSphereRadius((ServerLevel) level, targetPos, 10, true, true, false).forEach(
                    b -> level.setBlockAndUpdate(b, Blocks.BASALT.defaultBlockState())
            );
        }

        AtomicBoolean wasDarkened = new AtomicBoolean(false);
        UUID effectId = UUID.randomUUID();

        ServerScheduler.scheduleDelayed(2, () -> {
            double offsetX = startPos.x - (blockPos.getX() + 0.5);
            double offsetY = startPos.y - (blockPos.getY() + 0.5);
            double offsetZ = startPos.z - (blockPos.getZ() + 0.5);

            PacketHandler.sendToNearbyPlayers(new PlayCancellablePhotonBlockEffectPacket("flaring_sun", blockPos, offsetX, offsetY, offsetZ, 2, null, -1, false, true, null, effectId), (ServerLevel) level, startPos, 256);
        });

        ServerScheduler.scheduleForDuration(0, 5, 20 * 15, () -> {
            if(!wasDarkened.get()) {
                if(InteractionHandler.isInteractionPossible(new Location(targetPos, level), "darkness", BeyonderData.getSequence(entity))) {
                    wasDarkened.set(true);
                    PacketHandler.sendToNearbyPlayers(new CancelPhotonBlockEffectPacket(effectId), (ServerLevel) level, startPos, 256);
                }

                AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 24* multiplier(entity), baseDamage/2, targetPos, true, false, 20 * 4, ModDamageTypes.source(level, ModDamageTypes.PURIFICATION, entity));
                AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 24* multiplier(entity), baseDamage/2, targetPos, true, false, 20 * 4, ModDamageTypes.source(level, ModDamageTypes.FAITH, entity));
            }
            else {
                ParticleUtil.spawnSphereParticles((ServerLevel) level, ParticleTypes.SMOKE, startPos, 5.65f, 300);
            }
        }, () -> {
            if(level.getBlockState(blockPos).getBlock() == Blocks.LIGHT) {
                level.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
            }
        }, (ServerLevel) level, () -> AbilityUtil.getTimeInArea(entity, new Location(targetPos, level)));

        NeoForge.EVENT_BUS.post(new AbilityUsedEvent((ServerLevel) level, targetPos, entity, this, interactionFlags, interactionRadius, interactionCacheTicks));
    }
}
