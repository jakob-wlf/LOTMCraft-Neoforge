package de.jakob.lotm.item.custom;

import de.jakob.lotm.damage.ModDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class HolyWeaponItem extends SwordItem {

    private final float extraKnockback;

    public HolyWeaponItem(Tier tier, Properties properties, float extraKnockback) {
        super(tier, properties);
        this.extraKnockback = extraKnockback;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!(entity instanceof LivingEntity target)) {
            return false;
        }


        if (player.level().isClientSide() || !(player.level() instanceof ServerLevel level)) {
            return true;
        }
        if (!target.isAttackable() || target.skipAttackInteraction(player)) {
            return true;
        }

        float cooldown = player.getAttackStrengthScale(0.5F);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        damage *= 0.2F + cooldown * cooldown * 0.8F;

        boolean crit = cooldown > 0.9F
                && player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.isPassenger();
        if (crit) {
            damage *= 1.5F;
        }

        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.PURIFICATION, player);
        damage = EnchantmentHelper.modifyDamage(level, stack, target, source, damage);

        boolean hurt = target.hurt(source, damage);
        if (hurt) {
            float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
            target.knockback(0.4F + extraKnockback, Mth.sin(yawRad), -Mth.cos(yawRad));
            EnchantmentHelper.doPostAttackEffects(level, target, source);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    crit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            player.causeFoodExhaustion(0.1F);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        player.resetAttackStrengthTicker();
        return true;
    }
}
