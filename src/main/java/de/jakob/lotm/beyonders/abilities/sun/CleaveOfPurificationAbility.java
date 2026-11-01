package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CleaveOfPurificationAbility extends Ability {
    public CleaveOfPurificationAbility(String id) {
        super(id, 1.8f, "purification", "light_weak");

        hasOptimalDistance = true;
        optimalDistance = 1f;
        interactionRadius = 4;

        baseDamage = 5f;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(1, 1, 1, 2, 2, 3, 3, 4));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(2000f, 1000f, 670f, 380f, 325f, 237f, 170f, 150f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        Map<String, Integer> reqs = new HashMap<>();
        reqs.put("sun", 7);
        return reqs;
    }

    @Override
    protected float getSpiritualityCost() {
        return 20;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(level.isClientSide) {
            ClientHandler.applyCameraShakeToPlayersInRadius(.8f, 6, (ClientLevel) level, entity.position(), 8);
            return;
        }

        Vec3 startPos = entity.getEyePosition().subtract(0, .2, 0).add(entity.getLookAngle().normalize());
        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.BLAZE_SHOOT, entity.getSoundSource(), 2.0f, .5f);
        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.PLAYER_ATTACK_SWEEP, entity.getSoundSource(), 1.6f, .7f);
        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.FIRECHARGE_USE, entity.getSoundSource(), 1.0f, 1.3f);

        // the crescent is authored as a 150 degree arc starting at +X, turned so its middle lies on the view
        FxHandler.play((ServerLevel) level, "cleave_of_purification", entity.getEyePosition().subtract(0, .35, 0),
                Mth.clamp(multiplier(entity), 1, 3), FxHandler.facing(entity, -15));

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this);
        if(entitySeq <= 4)
            AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 2.75* (int) multiplier(entity), ModDamageTypes.LIGHT ,baseDamage, startPos, true, false, true, 0);
        else
            AbilityUtil.damageNearbyEntities((ServerLevel) level, entity, 2.75* (int) multiplier(entity), ModDamageTypes.PURIFICATION ,baseDamage, startPos, true, false, true, 0);

        BlockState block = level.getBlockState(BlockPos.containing(startPos));
        if(block.isAir()) {
            level.setBlockAndUpdate(BlockPos.containing(startPos), Blocks.LIGHT.defaultBlockState());
        }

        ServerScheduler.scheduleDelayed(25, () -> level.setBlockAndUpdate(BlockPos.containing(startPos), Blocks.AIR.defaultBlockState()));
    }
}
