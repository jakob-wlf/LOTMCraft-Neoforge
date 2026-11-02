package de.jakob.lotm.beyonders.rituals.impl;

import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItem;
import de.jakob.lotm.beyonders.potions.PotionRecipeItem;
import de.jakob.lotm.beyonders.potions.PotionRecipeItemHandler;
import de.jakob.lotm.beyonders.rituals.RitualResultHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public class RitualHigherSequenceRecipe implements RitualResultHandler {

    @Override
    public void perform(Map<String, Object> params, ServerPlayer player, BlockPos ritualCenter) {
    }

    @Override
    public void perform(Map<String, Object> params, ServerPlayer player, BlockPos ritualCenter, List<ItemStack> sacrifices) {
        for (ItemStack sacrifice : sacrifices) {
            if (!(sacrifice.getItem() instanceof BeyonderCharacteristicItem characteristic)) continue;
            PotionRecipeItem recipe = PotionRecipeItemHandler.selectRecipeOfPathwayAndSequence(characteristic.getPathway(), characteristic.getSequence() + 1);
            if (recipe == null) return;
            ItemStack reward = new ItemStack(recipe);
            if (!player.getInventory().add(reward)) player.drop(reward, false);
            return;
        }
    }
}
