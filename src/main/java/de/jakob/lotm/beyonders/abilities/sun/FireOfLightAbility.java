package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FireOfLightAbility extends Ability {
    public FireOfLightAbility(String id) {
        super(id, 1.75f, "purification", "burning", "light_source", "light_weak");
        postsUsedAbilityEventManually = true;
        interactionRadius = 4;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(1, 1, 1, 2, 2, 2, 3, 3));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(2000f, 1000f, 670f, 380f, 325f, 237f, 170f, 150f));

        baseDamage = 6f;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        Map<String, Integer> reqs = new HashMap<>();
        reqs.put("sun", 7);
        return reqs;
    }

    @Override
    protected float getSpiritualityCost() {
        return 50;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        Vec3 targetPos = AbilityUtil.getTargetLocation(entity, baseDistance, 1.4f);
        level.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.BLAZE_SHOOT, entity.getSoundSource(), 2.0f, .5f);
        level.playSound(null, targetPos.x, targetPos.y, targetPos.z, SoundEvents.BEACON_ACTIVATE, entity.getSoundSource(), .4f, .5f);

        ServerScheduler.scheduleDelayed(2, () -> {
            FxHandler.play((ServerLevel) level, "fire_of_light", groundBelow(level, targetPos), Mth.clamp(multiplier(entity), 1, 3));
        });

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this);

        if(entitySeq <= 4){
            AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 2.5* (int) multiplier(entity), ModDamageTypes.PURIFICATION, baseDamage * 0.34, targetPos, true, false, true, 0);
            AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 2.5* (int) multiplier(entity), ModDamageTypes.LIGHT, baseDamage * 0.33, targetPos, true, false, true, 0);
            AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 2.5* (int) multiplier(entity), ModDamageTypes.FAITH, baseDamage * 0.33, targetPos, true, false, true, 0);

        }
        else{
            AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 2.5* (int) multiplier(entity), ModDamageTypes.LIGHT, baseDamage, targetPos, true, false, true, 0);
        }

        BlockState block = level.getBlockState(BlockPos.containing(targetPos));
        if(block.isAir()) {
            level.setBlockAndUpdate(BlockPos.containing(targetPos), Blocks.LIGHT.defaultBlockState());
        }

        ServerScheduler.scheduleDelayed(25, () -> level.setBlockAndUpdate(BlockPos.containing(targetPos), Blocks.AIR.defaultBlockState()));
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent((ServerLevel) level, targetPos, entity, this, interactionFlags, interactionRadius, interactionCacheTicks));
    }

    /** The pillar rises from the ground under the targeted spot when there is any close below it. */
    private static Vec3 groundBelow(Level level, Vec3 pos) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(pos).mutable();
        for (int i = 0; i < 4; i++) {
            if (!level.getBlockState(cursor.below()).getCollisionShape(level, cursor.below()).isEmpty()) {
                return new Vec3(pos.x, cursor.getY(), pos.z);
            }
            cursor.move(0, -1, 0);
        }
        return pos.subtract(0, 1, 0);
    }
}
