package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.*;

public class TreasureHunterAbility extends ToggleAbility {

    public TreasureHunterAbility(String id) {
        super(id);
        canBeUsedByNPC = false;
        canBeShared = false;
        tickRate = 5;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 6));
    }

    @Override
    protected float getSpiritualityCost() {
        return 0.4f;
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (level.isClientSide) ErrorClientFlags.treasureHunterActive = true;
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.treasureHunterActive = false;
            return;
        }
        if (entity instanceof ServerPlayer player) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", -1);
            ErrorUtil.send(player, "treasure", tag);
        }
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) return;

        Entity target = AbilityUtil.getTargetEntity(player, 32, 1.2f, true, true);
        if (target == null) {
            target = TheftAbility.findGroundItem(player, 32, false);
        }

        CompoundTag data = new CompoundTag();
        data.putInt("id", target == null ? -1 : target.getId());
        if (target == null) {
            ErrorUtil.send(player, "treasure", data);
            return;
        }

        List<ItemStack> shown = new ArrayList<>();
        int value = 0;

        if (target instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            shown.add(stack);
            value += ErrorUtil.getItemValue(stack);
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                for (ItemStack inner : contents.nonEmptyItems()) {
                    shown.add(inner);
                    value += ErrorUtil.getItemValue(inner);
                }
            }
        } else if (target instanceof LivingEntity living) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                ItemStack stack = living.getItemBySlot(slot);
                if (!stack.isEmpty()) shown.add(stack);
            }
            if (living instanceof Player victim) {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = victim.getInventory().items.get(i);
                    if (!stack.isEmpty() && !shown.contains(stack)) shown.add(stack);
                }
                for (int i = 9; i < victim.getInventory().items.size(); i++) {
                    ItemStack stack = victim.getInventory().items.get(i);
                    if (ErrorUtil.isValuable(stack)) shown.add(stack);
                }
            }
            value = ErrorUtil.getCarriedValue(living);
        }

        ListTag items = new ListTag();
        for (ItemStack stack : shown.subList(0, Math.min(36, shown.size()))) {
            CompoundTag tag = new CompoundTag();
            tag.put("item", stack.saveOptional(serverLevel.registryAccess()));
            tag.putInt("value", ErrorUtil.getItemValue(stack));
            items.add(tag);
        }
        data.put("items", items);
        data.putInt("value", value);
        data.putString("name", target.getName().getString());
        ErrorUtil.send(player, "treasure", data);
    }
}
