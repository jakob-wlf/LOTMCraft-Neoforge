package de.jakob.lotm.beyonders.abilities.mother;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.core.interaction.InteractionHandler;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.ability_entities.TendrilEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class PlantControllingAbility extends SelectableAbility {
    private static final int VINE_FADE = 10;
    private static final HashSet<UUID> boundEntities = new HashSet<>();

    public PlantControllingAbility(String id) {
        super(id, 2);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(1, 1, 1, 2, 2, 3, 4, 5));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3500f, 1500f, 1000f, 700f, 500f, 300f, 240f, 195f));

        baseDamage = 5;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("mother", 7));
    }

    @Override
    protected float getSpiritualityCost() {
        return 45;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{"ability.lotmcraft.plants.trap", "ability.lotmcraft.plants.attack"};
    }

    private void entrap(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        LivingEntity targetEntity = AbilityUtil.getTargetEntity(entity, baseDistance, 2);
        if(targetEntity == null)
            return;

        if(boundEntities.contains(targetEntity.getUUID())) {
            if(entity instanceof ServerPlayer player) {
                ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(Component.literal("Entity is already bound by your plants.").withColor(0xFFff124d));
                player.connection.send(packet);
            }
            return;
        }

        int duration = 20 * 2;

        // vines break out of the ground around the target, arc over and coil up its body
        List<TendrilEntity> vines = new ArrayList<>();
        int vineCount = 6;
        for(int i = 0; i < vineCount; i++) {
            double angle = i * Math.PI * 2 / vineCount + random.nextDouble(-.4, .4);
            double distance = random.nextDouble(2.2, 4.2);
            Vec3 root = groundBelow(level, targetEntity.position().add(Math.cos(angle) * distance, 1.5, Math.sin(angle) * distance));

            vines.add(TendrilEntity.spawn((ServerLevel) level, TendrilEntity.Kind.VINE, root, targetEntity, root,
                    random.nextInt(9, 15), duration + VINE_FADE, VINE_FADE, (float) random.nextDouble(1.2, 1.8), (float) random.nextDouble(.075, .1)));
            FxHandler.play((ServerLevel) level, "plant_root", root, 1);
        }

        level.playSound(null, entity, Blocks.GRASS_BLOCK.getSoundType(Blocks.GRASS_BLOCK.defaultBlockState(), level, BlockPos.containing(entity.position().x, entity.position().y, entity.position().z), null).getBreakSound(), SoundSource.BLOCKS, 5,1);


        boundEntities.add(targetEntity.getUUID());
        FxHandler.play((ServerLevel) level, "plant_ensnare", targetEntity.position().add(0, .05, 0), Math.max(1, targetEntity.getBbWidth() / .6));

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this);

        if(!BeyonderData.isBeyonder(targetEntity) || BeyonderData.getSequence(targetEntity) - 1 > entitySeq) {
            if(targetEntity instanceof Mob) {
                ((Mob) targetEntity).setNoAi(true);
                ServerScheduler.scheduleDelayed(duration, () -> ((Mob) targetEntity).setNoAi(false));
            }
        }

        Location loc = new Location(targetEntity.position(), targetEntity.level());

        AtomicReference<UUID> taskIdRef = new AtomicReference<>();
        UUID taskId = ServerScheduler.scheduleForDuration(0, 5, duration, () -> {
            boolean escape = InteractionHandler.isInteractionPossibleForEntity(loc, "escape", entitySeq, targetEntity);
            boolean burning = InteractionHandler.isInteractionPossibleForEntity(loc, "burning", entitySeq, targetEntity);

            if(escape || burning) {
                ServerScheduler.cancel(taskIdRef.get());

                targetEntity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                targetEntity.removeEffect(MobEffects.WEAKNESS);
                targetEntity.removeEffect(MobEffects.DIG_SLOWDOWN);
                if (targetEntity instanceof Mob mob) mob.setNoAi(false);

                boundEntities.remove(targetEntity.getUUID());
                vines.forEach(TendrilEntity::release);

                if(burning) {
                    Vec3 pos = targetEntity.position().add(0, targetEntity.getBbHeight() * .5, 0);
                    FxHandler.play((ServerLevel) level, "bindings_burn", pos, Math.max(1, targetEntity.getBbHeight() / 1.8));
                    level.playSound(null, BlockPos.containing(pos),
                            SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.2f);
                }
                return;
            }

            targetEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 10, false, false, false));
            targetEntity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 10, false, false, false));
            targetEntity.setDeltaMovement(new Vec3(0, 0, 0));
            targetEntity.setOnGround(true);
            var pos = targetEntity.position();
            targetEntity.setDeltaMovement(new Vec3(0, 0, 0));
            targetEntity.hurtMarked = true;

            targetEntity.teleportTo(pos.x, pos.y, pos.z);

            loc.setLevel(targetEntity.level());
            loc.setPosition(targetEntity.position());
        });
        taskIdRef.set(taskId);

        ServerScheduler.scheduleDelayed(duration, () -> boundEntities.remove(targetEntity.getUUID()));
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        switch (abilityIndex) {
            case 0 -> entrap(level, entity);
            case 1 -> attack(level, entity);
        }
    }

    private void attack(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        Vec3 look = entity.getLookAngle().normalize();
        Vec3 targetLoc = AbilityUtil.getTargetLocation(entity, baseDistance, 1.4f);
        LivingEntity targetEntity = AbilityUtil.getTargetEntity(entity, 16, 2);
        Vec3 end = targetEntity != null ? targetEntity.position().add(0, targetEntity.getBbHeight() * .5, 0) : targetLoc;

        // the vine whips out of the ground beside the caster
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 root = groundBelow(level, entity.position().add(look.scale(1.2)).add(side.scale(random.nextBoolean() ? random.nextDouble(1, 2.5) : -random.nextDouble(1, 2.5))).add(0, 1, 0));

        int reach = 5;
        TendrilEntity.spawn((ServerLevel) level, TendrilEntity.Kind.VINE, root, null, end, reach, reach + 14, 8, 0, .12f);
        FxHandler.play((ServerLevel) level, "plant_root", root, 1);

        level.playSound(null, entity, Blocks.GRASS_BLOCK.getSoundType(Blocks.GRASS_BLOCK.defaultBlockState(), level, BlockPos.containing(entity.position().x, entity.position().y, entity.position().z), null).getBreakSound(), SoundSource.BLOCKS, 5,1);

        ServerScheduler.scheduleDelayed(reach, () -> {
            FxHandler.play((ServerLevel) level, "plant_lash", end, 1);
            level.playSound(null, end.x, end.y, end.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2f, .7f);
            if(targetEntity != null && targetEntity.isAlive())
                targetEntity.hurt(ModDamageTypes.source(level, ModDamageTypes.NATURE_WRATH, entity), baseDamage);
        });
    }

    /** First spot with ground underneath, searching down from {@code pos} for a few blocks. */
    private static Vec3 groundBelow(Level level, Vec3 pos) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(pos).mutable();
        for(int i = 0; i < 6; i++) {
            BlockPos below = cursor.below();
            if(!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty())
                return new Vec3(pos.x, cursor.getY(), pos.z);
            cursor.move(0, -1, 0);
        }
        return pos.subtract(0, 1.5, 0);
    }
}
