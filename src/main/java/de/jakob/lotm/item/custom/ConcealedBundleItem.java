package de.jakob.lotm.item.custom;

import de.jakob.lotm.beyonders.abilities.door.ConcealedBundleAbility;
import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.gui.custom.concealed_bundle.ConcealedBundleMenu;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class ConcealedBundleItem extends Item {
    public ConcealedBundleItem(Properties properties) {
        super(properties);
    }

    public static boolean isOwner(ItemStack stack, LivingEntity entity) {
        UUID owner = stack.get(ModDataComponents.CONCEALED_BUNDLE_OWNER);
        return owner != null && owner.equals(entity.getUUID());
    }

    public static boolean canOpen(ItemStack stack, LivingEntity entity) {
        if (isOwner(stack, entity)) return true;
        int seq = ConcealedBundleAbility.sequenceOf(stack.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_SIZE, 0));
        return "door".equals(BeyonderData.getPathway(entity)) && BeyonderData.getSequence(entity) <= seq;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && ConcealedBundleAbility.stale(stack, level.getServer())) {
            fade(stack, player);
            return InteractionResultHolder.fail(stack);
        }
        if (!canOpen(stack, player)) {
            if (!level.isClientSide) AbilityUtil.sendActionBar(player, Component.translatable("item.lotmcraft.concealed_bundle.not_owner").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            int size = stack.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_SIZE, 0);
            stack.set(ModDataComponents.CONCEALED_BUNDLE_OPENS, opens(stack) + 1);
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ConcealedBundleMenu(id, inventory, hand, stack), stack.getHoverName()), buf -> {
                buf.writeVarInt(size);
                buf.writeEnum(hand);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || entity.tickCount % 20 != 0) return;
        if (ConcealedBundleAbility.stale(stack, level.getServer())) fade(stack, entity);
    }

    private static void fade(ItemStack stack, Entity holder) {
        Iterable<ItemStack> contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemsCopy();
        stack.setCount(0);
        for (ItemStack item : contents) {
            if (holder instanceof Player player) player.getInventory().placeItemBackInInventory(item);
            else holder.spawnAtLocation(item);
        }
        holder.level().playSound(null, holder.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.6f);
        if (holder instanceof Player player) AbilityUtil.sendActionBar(player, Component.translatable("item.lotmcraft.concealed_bundle.faded").withStyle(ChatFormatting.GRAY));
    }

    public static int opens(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_OPENS, 0);
    }

    public static boolean wornOut(ItemStack stack) {
        return opens(stack) >= ConcealedBundleAbility.maxOpens(stack.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_SIZE, 0));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        ItemContainerContents contents = itemEntity.getItem().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        ItemUtils.onContainerDestroyed(itemEntity, contents.nonEmptyItemsCopy());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int size = stack.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_SIZE, 0);
        tooltip.add(Component.translatable("item.lotmcraft.concealed_bundle.size", size).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lotmcraft.concealed_bundle.opens", opens(stack), ConcealedBundleAbility.maxOpens(size)).withStyle(ChatFormatting.GRAY));
    }
}
