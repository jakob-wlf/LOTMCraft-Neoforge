package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HolyWaterAbility extends Ability {
    public HolyWaterAbility(String id) {
        super(id, 0.2f);

        canBeUsedByNPC = false;

        baseDamage = 1f;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(!(entity instanceof ServerPlayer player))
            return;

        level.playSound(null, BlockPos.containing(entity.position()), SoundEvents.BEACON_ACTIVATE, entity.getSoundSource(), .5f, .5f);

        ItemStack mainHandItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if(!isPotion(mainHandItem)) {
            mainHandItem = player.getItemInHand(InteractionHand.OFF_HAND);
            hand = InteractionHand.OFF_HAND;
        }

        if(!isPotion(mainHandItem)) {
            AbilityUtil.sendActionBar(player, Component.translatable("lotmcraft.holy_water_ability.no_item_in_hand").withColor(getColorForPathway("sun")));
            return;
        }

        ItemStack newPotion = mainHandItem.is(Items.POTION) ?
                new ItemStack(Items.POTION) : new ItemStack(Items.SPLASH_POTION);

        createPotion(newPotion);

        player.setItemInHand(hand, newPotion);
    }

    private void createPotion(ItemStack potion) {
        int sunColor = getColorForPathway("sun");

        Component name = Component.translatable("lotmcraft.holy_water_ability.item").withColor(sunColor);
        potion.set(DataComponents.CUSTOM_NAME, name);

        List<MobEffectInstance> customEffects = List.of(
                new MobEffectInstance(ModEffects.PURIFICATION, 60, (int) baseDamage)
        );

        PotionContents holyWaterContents = new PotionContents(
                Optional.of(Potions.AWKWARD),
                Optional.of(sunColor),
                customEffects
        );

        potion.set(DataComponents.POTION_CONTENTS, holyWaterContents);
    }

    private boolean isPotion(ItemStack itemStack) {
        return itemStack != null && (itemStack.is(Items.POTION) || itemStack.is(Items.SPLASH_POTION));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return Map.of("sun", 7);
    }

    @Override
    protected float getSpiritualityCost() {
        return 80;
    }
}
