package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.FateHandler;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class TamperFateAbility extends SelectableAbility {

    public TamperFateAbility(String id) {
        super(id, 6);
        canBeShared = false;
        canBeCopied = false;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(3, 4, 6));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(5000f, 3000f, 2200f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    protected float getSpiritualityCost() {
        return 2200;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.tamper_fate.tamper",
                "ability.lotmcraft.tamper_fate.counter"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel)) {
            if (entity instanceof Player player) player.playSound(SoundEvents.BELL_RESONATE, 1, 0.6f);
            return;
        }

        if (entity instanceof ServerPlayer player && (selectedAbility == 1 || FateHandler.hasCounterable(player.getUUID()))) {
            int returned = FateHandler.counterTamper(player);
            AbilityUtil.sendActionBar(player, Component.translatable(returned > 0 ? "ability.lotmcraft.tamper_fate.countered" : "ability.lotmcraft.tamper_fate.nothing_to_counter", returned)
                    .withColor(ErrorUtil.FATE_COLOR));
            if (returned > 0) FxHandler.playOnEntity(serverLevel, "fate_threads", player, player.getBbHeight() * 0.5, 1);
            if (selectedAbility == 1) return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(entity, Math.max(40, baseDistance), 2, false, true);
        if (target == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.tamper_fate.no_target").withColor(ErrorUtil.FATE_COLOR));
            return;
        }

        FxHandler.playOnEntity(serverLevel, "fate_threads", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.8));

        if (AllyUtil.areAllies(entity, target)) {
            tamperFortune(serverLevel, entity, target);
            return;
        }

        if (AbilityUtil.isTargetSignificantlyStronger(entity, target)
                || random.nextDouble() < AbilityUtil.getSequenceFailureChance(entity, target)) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.tamper_fate.resisted").withColor(ErrorUtil.FATE_COLOR));
            return;
        }

        tamperMisfortune(serverLevel, entity, target);
    }

    private void tamperFortune(ServerLevel level, LivingEntity caster, LivingEntity target) {
        switch (random.nextInt(3)) {
            case 0 -> target.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 60, 1));
            case 1 -> target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 1));
            default -> AbilityUtil.addTargetLuckWithMax(caster, target, 150, 600);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1.4f);
    }

    private void tamperMisfortune(ServerLevel level, LivingEntity caster, LivingEntity target) {
        switch (random.nextInt(5)) {
            case 0 -> {
                ItemStack held = target.getItemInHand(InteractionHand.MAIN_HAND);
                if (!held.isEmpty() && target instanceof Player player) {
                    player.drop(held.copy(), true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                } else {
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 10, 1));
                }
            }
            case 1 -> {
                target.setDeltaMovement(new Vec3(random.nextDouble() - 0.5, 0.35, random.nextDouble() - 0.5));
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 4, 3));
            }
            case 2 -> AbilityUtil.addTargetLuckWithMin(caster, target, -200, -800);
            case 3 -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(target.position())));
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                }
            }
            default -> {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 6, 0));
                target.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 20 * 60, 1));
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.3f, 2f);
    }
}
