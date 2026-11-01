package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;

public class HolyEquipmentAbility extends Ability {
    public HolyEquipmentAbility(String id) {
        super(id, 20 * 60 * 5);
        canBeUsedByNPC = false;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(!(entity instanceof ServerPlayer serverPlayer)) return;

        ItemStack helmet = new ItemStack(ModItems.HOLY_HELMET.get());
        ItemStack chestPlate = new ItemStack(ModItems.HOLY_CHESTPLATE.get());
        ItemStack leggings = new ItemStack(ModItems.HOLY_LEGGINGS.get());
        ItemStack boots = new ItemStack(ModItems.HOLY_BOOTS.get());
        ItemStack sword = new ItemStack(ModItems.HOLY_SWORD.get());

        if(!serverPlayer.hasItemInSlot(EquipmentSlot.HEAD)) {
            serverPlayer.setItemSlot(EquipmentSlot.HEAD, helmet);
        } else if(!serverPlayer.addItem(helmet)) {
            serverPlayer.drop(helmet, false);
        }

        if(!serverPlayer.hasItemInSlot(EquipmentSlot.CHEST)) {
            serverPlayer.setItemSlot(EquipmentSlot.CHEST, chestPlate);
        } else if(!serverPlayer.addItem(chestPlate)) {
            serverPlayer.drop(chestPlate, false);
        }

        if(!serverPlayer.hasItemInSlot(EquipmentSlot.LEGS)) {
            serverPlayer.setItemSlot(EquipmentSlot.LEGS, leggings);
        } else if(!serverPlayer.addItem(leggings)) {
            serverPlayer.drop(leggings, false);
        }

        if(!serverPlayer.hasItemInSlot(EquipmentSlot.FEET)) {
            serverPlayer.setItemSlot(EquipmentSlot.FEET, boots);
        } else if(!serverPlayer.addItem(boots)) {
            serverPlayer.drop(boots, false);
        }

        if(serverPlayer.getMainHandItem().isEmpty()) {
            serverPlayer.setItemInHand(serverPlayer.getUsedItemHand(), sword);
        } else if(!serverPlayer.addItem(sword)) {
            serverPlayer.drop(sword, false);
        }

    }

    @Override
    public Map<String, Integer> getRequirements() {
        return Map.of("sun", 4);
    }

    @Override
    protected float getSpiritualityCost() {
        return 1000;
    }
}
