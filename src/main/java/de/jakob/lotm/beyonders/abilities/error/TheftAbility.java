package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.TheftHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ThoroughTheftHandler;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.SendPassiveTheftEffectPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TheftAbility extends SelectableAbility {

    private static final int GROUND_RANGE_SPIRITUAL = 7;
    private static final int QUICK_RANGE = 50;

    private static final HashMap<UUID, Integer> lastMarked = new HashMap<>();

    public TheftAbility(String id) {
        super(id, 1.75f);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(1, 1, 1, 1, 1, 2, 2, 3, 3));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3000f, 1400f, 1000f, 580f, 550f, 422f, 300f, 220f, 130f));

        canBeUsedByNPC = false;
        onHoldTickInverval = 4;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 8));
    }

    @Override
    public float getSpiritualityCost() {
        return 40f;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.theft.quick",
                "ability.lotmcraft.theft.thorough"
        };
    }

    @Override
    public String[] getSequenceDependantAbilityNames(int sequence) {
        if (sequence > 6) {
            return new String[]{"ability.lotmcraft.theft.spiritual"};
        }
        return getAllAbilityNames();
    }

    @Override
    public String getSelectedAbility(LivingEntity entity) {
        String[] names = getSequenceDependantAbilityNames(BeyonderData.getSequence(entity));
        if (names.length == 0) return "";
        int index = Math.min(getSelectedAbilityIndex(entity.getUUID()), names.length - 1);
        return names[Math.max(0, index)];
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (ProhibitionHandler.IsInTheftZone(entity.position(), serverLevel, AbilityUtil.getSeqWithArt(entity, this))) return;

        int seq = AbilityUtil.getSeqWithArt(entity, this);
        if (seq > 6) {
            spiritualTheft(serverLevel, player);
        } else if (selectedAbility == 1) {
            thoroughTheft(player);
        } else {
            quickTheft(serverLevel, player);
        }
    }

    private void spiritualTheft(ServerLevel level, ServerPlayer player) {
        LivingEntity target = AbilityUtil.getTargetEntity(player, baseDistance, 1.5f);

        if (target != null) {
            if (TheftHandler.stealSpiritualItem(target, player, this)) {
                PacketHandler.sendToPlayer(player, new SendPassiveTheftEffectPacket(target.getEyePosition().x, target.getEyePosition().y, target.getEyePosition().z));
            } else {
                AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.theft.no_spirituality").withColor(ErrorUtil.ERROR_COLOR));
            }
            return;
        }

        ItemEntity item = findGroundItem(player, GROUND_RANGE_SPIRITUAL, true);
        if (item != null) {
            TheftHandler.stealGroundItem(item, player);
            return;
        }

        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.theft.no_target").withColor(ErrorUtil.ERROR_COLOR));
    }

    private void quickTheft(ServerLevel level, ServerPlayer player) {
        Entity target = getQuickTheftTarget(player);

        if (target instanceof LivingEntity living) {
            if (TheftHandler.stealRandomItem(living, player, this)) {
                PacketHandler.sendToPlayer(player, new SendPassiveTheftEffectPacket(living.getEyePosition().x, living.getEyePosition().y, living.getEyePosition().z));
            } else {
                AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.theft.failed").withColor(ErrorUtil.ERROR_COLOR));
            }
            return;
        }

        if (target instanceof ItemEntity item) {
            TheftHandler.stealGroundItem(item, player);
            return;
        }

        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.theft.no_target").withColor(ErrorUtil.ERROR_COLOR));
    }

    private void thoroughTheft(ServerPlayer player) {
        LivingEntity target = AbilityUtil.getTargetEntity(player, ThoroughTheftHandler.MAX_RANGE, 1.5f);
        if (target == null) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.theft.no_target").withColor(ErrorUtil.ERROR_COLOR));
            return;
        }
        ThoroughTheftHandler.open(player, target, this);
    }

    @Nullable
    private Entity getQuickTheftTarget(ServerPlayer player) {
        LivingEntity living = AbilityUtil.getTargetEntity(player, QUICK_RANGE, 1.5f);
        if (living != null) return living;
        return findGroundItem(player, QUICK_RANGE, false);
    }

    @Nullable
    public static ItemEntity findGroundItem(LivingEntity player, double range, boolean spiritualOnly) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        HitResult blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistance = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(eye) + 1.5;

        ItemEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(2))) {
            if (spiritualOnly && !ErrorUtil.isSpiritualItem(item.getItem())) continue;

            Vec3 toItem = item.position().add(0, 0.25, 0).subtract(eye);
            double along = toItem.dot(look);
            if (along < 0 || along > maxDistance) continue;

            double offAxis = toItem.subtract(look.scale(along)).length();
            double tolerance = 0.6 + along * 0.04;
            if (offAxis > tolerance) continue;

            double score = offAxis + along * 0.01;
            if (score < bestScore) {
                bestScore = score;
                best = item;
            }
        }

        if (best == null && spiritualOnly) {
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(range))) {
                if (!ErrorUtil.isSpiritualItem(item.getItem())) continue;
                double distance = item.distanceToSqr(player);
                if (distance < bestScore && distance <= range * range) {
                    bestScore = distance;
                    best = item;
                }
            }
        }

        return best;
    }

    @Override
    public void onHold(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;

        int id = -1;
        if (BeyonderData.getSequence(player) <= 6 && getSelectedAbilityIndex(player.getUUID()) == 0) {
            Entity target = getQuickTheftTarget(player);
            if (target != null) id = target.getId();
        }

        Integer previous = lastMarked.get(player.getUUID());
        if (previous != null && previous == id && player.tickCount % 20 != 0) return;
        lastMarked.put(player.getUUID(), id);

        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        ErrorUtil.send(player, "theft_mark", tag);
    }
}
