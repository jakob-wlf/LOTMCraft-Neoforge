package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.FlameDevil;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class LavaSwordItem extends Item {
    public static final int MAX_DURABILITY = 60;

    public LavaSwordItem(Properties properties) {super(properties.stacksTo(1).durability(MAX_DURABILITY).fireResistant());}
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {return InteractionResultHolder.success(stack);}
        if (!(level instanceof ServerLevel serverLevel)) {return InteractionResultHolder.pass(stack);}
        if (player.getCooldowns().isOnCooldown(this)) {return InteractionResultHolder.fail(stack);}
        if (!SwordOfLavaAbility.fireLavaSword(serverLevel, player)) {return InteractionResultHolder.fail(stack);}
        player.getCooldowns().addCooldown(this, SwordOfLavaAbility.PROJECTILE_COOLDOWN_TICKS);
        stack.hurtAndBreak(SwordOfLavaAbility.PROJECTILE_DURABILITY_COST, player, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.consume(stack);
    }
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, @NotNull LivingEntity attacker) {
        target.setRemainingFireTicks(20 * 4);
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }
    @Override
    public boolean isFoil(@NotNull ItemStack stack) {return true;}
}