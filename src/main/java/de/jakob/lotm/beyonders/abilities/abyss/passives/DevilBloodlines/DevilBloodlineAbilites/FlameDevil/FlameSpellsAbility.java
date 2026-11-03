package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.FlameDevil;

import de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbility;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.entity.custom.projectiles.FireballEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.data.PriestFlameVariants;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.helper.VectorUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FlameSpellsAbility extends SelectableAbility {

    public FlameSpellsAbility(String id) {
        super(id, 10f, "burning");

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(4, 4, 5, 6, 7, 8, 10));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(1050f, 610f, 430f, 310f, 270f, 190f, 150f));

        baseDamage = 14f;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of(
                "abyss", 6
        ));
    }

    @Override
    protected float getSpiritualityCost() {
        return 150;
    }
    @Override
    public boolean meetsBloodlineRequirement(LivingEntity entity) {
        return hasFlameDevilBloodline(entity);
    }
    private static boolean hasFlameDevilBloodline(LivingEntity entity) {
        if (entity instanceof AvatarEntity) return false;
        return DevilBloodlineAbility.hasBloodline(entity, DevilBloodlineAbility.Bloodline.FLAME_DEVIL);
    }
    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.flame_spells.sulfur_fireball",
                "ability.lotmcraft.flame_spells.volcanic_eruption",
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        switch(abilityIndex) {
            case 0 -> fireball(level, entity);
            case 1 -> eruption(level, entity);
        }
    }

    private void eruption(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        Vec3 startPos = entity.position();
        boolean griefing = BeyonderData.isGriefingEnabled(entity);

        level.playSound(null, BlockPos.containing(startPos), SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 3, 1);
        level.playSound(null, BlockPos.containing(startPos), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3, 1);

        float damage = baseDamage * BloodlineFilthFlameSpiritAbility.getFireDamageMultiplier(entity);
        AbilityUtil.getNearbyEntities(entity, (ServerLevel) level, startPos, 5).forEach(e -> {
            e.hurt(ModDamageTypes.source(level, ModDamageTypes.FIRE, entity), damage);
            e.setRemainingFireTicks(20 * 3);
            Vec3 away = e.position().subtract(startPos);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(0, 0, 1);
            Vec3 dir = away.normalize();
            e.setDeltaMovement(new Vec3(dir.x, .75, dir.z).normalize().scale(.5));
        });

        ParticleUtil.spawnParticles((ServerLevel) level, ParticleTypes.FLAME, startPos.add(0, 1, 0), 600, .75, 1.25, .75, .025);
        ParticleUtil.spawnParticles((ServerLevel) level, ParticleTypes.EXPLOSION, startPos.add(0, 1, 0), 20, .75, 1.25, .75, .025);

        AbilityUtil.getBlocksInCircleOutline((ServerLevel) level, startPos.subtract(0, 1, 0), 5).forEach(b -> spawnFallingBlocks(level, startPos, b, griefing));
        AbilityUtil.getBlocksInCircleOutline((ServerLevel) level, startPos.subtract(0, 1, 0), 3).forEach(b -> spawnFallingBlocks(level, startPos, b, griefing));

        NeoForge.EVENT_BUS.post(new AbilityUsedEvent((ServerLevel) level, startPos, entity, this, new String[]{"explosion", "burning"}, 4, 20));
    }

    private void spawnFallingBlocks(Level level, Vec3 startPos, BlockPos b, boolean griefing) {
        if (random.nextInt(2) != 0) return;
        BlockState state = level.getBlockState(b);
        BlockState above = level.getBlockState(b.above());
        if (state.getCollisionShape(level, b).isEmpty() || !above.getCollisionShape(level, b.above()).isEmpty()) return;
        if (!griefing || state.getDestroySpeed(level, b) < 0 || level.getBlockEntity(b) != null) {
            ParticleUtil.spawnParticles((ServerLevel) level, new BlockParticleOption(ParticleTypes.BLOCK, state), Vec3.atCenterOf(b.above()), 10, .3, .05);
            return;
        }
        Vec3 vectorFromCenter = new Vec3(b.getX() + 0.5 - startPos.x, 0, b.getZ() + 0.5 - startPos.z).normalize();
        Vec3 movement = new Vec3(vectorFromCenter.x, 1, vectorFromCenter.z).normalize().scale(.75);
        FallingBlockEntity block = FallingBlockEntity.fall(level, b.above(), state);
        block.setDeltaMovement(movement);
        level.setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
        block.hurtMarked = true;
    }

    private void fireball(Level level, LivingEntity entity) {
        if (level.isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 castPos = entity.getEyePosition();
        boolean dealsFilth = AbilityUtil.getSeqWithArt(entity, this) <= 5;
        float damage = baseDamage * .95f * BloodlineFilthFlameSpiritAbility.getFireDamageMultiplier(entity);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            ServerScheduler.scheduleDelayed(i * 5, () -> shootSulfurFireball(serverLevel, entity, index, dealsFilth, damage), serverLevel, () -> AbilityUtil.getTimeInArea(entity, new Location(castPos, serverLevel)));
        }
    }

    private void shootSulfurFireball(ServerLevel level, LivingEntity entity, int index, boolean dealsFilth, float damage) {
        Vec3 eyePos = entity.getEyePosition();
        Vec3 lookDir = entity.getLookAngle();
        Vec3 targetPos = AbilityUtil.getTargetLocation(entity, 50, 1.4f);
        double horizontalOffset = (index - 1) * 1.5;
        Vec3 startPos = VectorUtil.getRelativePosition(eyePos.add(lookDir.normalize()), lookDir.normalize(), 0, horizontalOffset, 0.25);
        Vec3 direction = targetPos.subtract(startPos).normalize();
        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.WITHER_SHOOT, entity.getSoundSource(), 1.0f, 1.0f);
        FireballEntity fireball = new FireballEntity(level, entity, damage, BeyonderData.isGriefingEnabled(entity));
        fireball.setPos(startPos.x, startPos.y, startPos.z);
        fireball.setVariant(dealsFilth ? PriestFlameVariants.DARK : PriestFlameVariants.AZURE);
        fireball.setSecondaryDamageType(dealsFilth ? ModDamageTypes.FILTH : ModDamageTypes.FIRE);
        fireball.shoot(direction.x, direction.y, direction.z, 1.2f, 0);
        level.addFreshEntity(fireball);
    }
}
