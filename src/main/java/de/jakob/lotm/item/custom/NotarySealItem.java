package de.jakob.lotm.item.custom;

import de.jakob.lotm.beyonders.abilities.sun.notarization.ability.NotaryAbilities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Test focus: use = Notarization, sneak+use = Authentication. Replace with your real ability triggers. */
public class NotarySealItem extends Item {
    public NotarySealItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) NotaryAbilities.authenticate(sp);
            else NotaryAbilities.openContractMenu(sp);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("contractnotary.seal.tooltip1").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("contractnotary.seal.tooltip2").withStyle(ChatFormatting.YELLOW));
    }
}
