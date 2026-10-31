package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.attachments.AllyComponent;
import de.jakob.lotm.attachments.DisabledAbilitiesComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.SanityComponent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.FateHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.TheftHandler;
import de.jakob.lotm.beyonders.abilities.visionary.prophecy.Prophecy;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.shapeShifting.ShapeShiftingUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

public class StealFateAbility extends SelectableAbility {

    private static final int EROSION_TICKS = 20 * 120;
    private static final int IMMUNITY_TICKS = 20 * 180;

    public StealFateAbility(String id) {
        super(id, 30);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeShared = false;
        canBeUsedInArtifact = false;
        cannotBeStolen = true;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(15, 20, 30));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(18000f, 12000f, 8000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    protected float getSpiritualityCost() {
        return 8000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.steal_fate.steal",
                "ability.lotmcraft.steal_fate.give"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            if (entity instanceof Player player && level.isClientSide) player.playSound(SoundEvents.BELL_RESONATE, 1, 0.5f);
            return;
        }
        if (ProhibitionHandler.IsInTheftZone(player.position(), serverLevel, AbilityUtil.getSeqWithArt(player, this))) return;

        if (selectedAbility == 0) steal(serverLevel, player);
        else give(serverLevel, player);
    }

    private void message(ServerPlayer player, String key, Object... args) {
        AbilityUtil.sendActionBar(player, Component.translatable(key, args).withColor(ErrorUtil.FATE_COLOR));
    }

    private float getFraction(int difference) {
        if (difference >= 2) return 1f;
        return switch (difference) {
            case 1 -> 0.5f;
            case 0 -> 1f / 3f;
            case -1 -> 0.25f;
            default -> 0f;
        };
    }

    private void steal(ServerLevel level, ServerPlayer thief) {
        LivingEntity target = AbilityUtil.getTargetEntity(thief, 60, 2);
        if (target == null) {
            message(thief, "ability.lotmcraft.steal_fate.no_target");
            return;
        }

        int thiefSeq = AbilityUtil.getSeqWithArt(thief, this);
        int targetSeq = ErrorUtil.seqOf(target);
        int difference = targetSeq - thiefSeq;

        DisabledAbilitiesComponent disabled = target.getData(ModAttachments.DISABLED_ABILITIES_COMPONENT);
        boolean weakened = target.getData(ModAttachments.SANITY_COMPONENT).getSanity() < 0.3f
                || target.getHealth() < target.getMaxHealth() * 0.2f
                || disabled.isAbilityUsageDisabled();
        if (weakened) difference++;

        float fraction = getFraction(difference);
        if (fraction <= 0 || (AbilityUtil.isTargetSignificantlyStronger(thiefSeq, targetSeq) && !weakened)) {
            message(thief, "ability.lotmcraft.steal_fate.beyond_you");
            return;
        }

        SanityComponent sanity = thief.getData(ModAttachments.SANITY_COMPONENT);
        switch (difference) {
            case 1 -> sanity.setSanityAndSync(sanity.getSanity() - 0.08f, thief);
            case 0 -> sanity.setSanityAndSync(sanity.getSanity() - 0.3f, thief);
            case -1 -> sanity.setSanityAndSync(sanity.getSanity() * 0.3f, thief);
            default -> {
            }
        }

        UUID fateId = UUID.randomUUID();
        FateHandler.StolenFate fate = new FateHandler.StolenFate(target.getUUID(), target.getName().getString(), level.getGameTime(), fraction);
        Random rng = new Random();

        List<ItemStack> stacks = TheftHandler.getAllStealableStacks(target);
        Collections.shuffle(stacks, rng);
        int toTake = (int) Math.ceil(stacks.size() * fraction);
        for (int i = 0; i < toTake; i++) {
            ItemStack live = stacks.get(i);
            ItemStack stolen = live.copy();
            live.setCount(0);
            ErrorUtil.markOwner(stolen, target);
            FateHandler.markFateItem(stolen, fateId);
            ErrorUtil.giveOrDrop(thief, stolen);
        }
        fate.itemTags.add(fateId);

        List<MobEffectInstance> effects = new ArrayList<>(target.getActiveEffects());
        Collections.shuffle(effects, rng);
        for (int i = 0; i < Math.ceil(effects.size() * fraction); i++) {
            MobEffectInstance effect = effects.get(i);
            fate.effects.add(new MobEffectInstance(effect));
            thief.addEffect(new MobEffectInstance(effect));
            target.removeEffect(effect.getEffect());
        }

        int targetLuck = target.getData(ModAttachments.LUCK_COMPONENT.get()).getLuck();
        int luck = (int) (targetLuck * fraction);
        if (luck != 0) {
            AbilityUtil.setTargetLuck(thief, target, targetLuck - luck);
            AbilityUtil.addTargetLuck(thief, thief, luck);
            fate.luck = luck;
        }

        if (target instanceof ServerPlayer targetPlayer) {
            List<AllyComponent.AllyInfo> allies = new ArrayList<>(targetPlayer.getData(ModAttachments.ALLY_COMPONENT).allies());
            Collections.shuffle(allies, rng);
            for (int i = 0; i < Math.ceil(allies.size() * fraction); i++) {
                ServerPlayer ally = level.getServer().getPlayerList().getPlayer(allies.get(i).uuid());
                if (ally == null || ally == thief) continue;
                AllyUtil.addAllyOneWay(ally, thief);
                AllyUtil.addAllyOneWay(thief, ally);
            }

            if (fraction >= 0.5f) {
                ShapeShiftingUtil.shapeShift(thief, targetPlayer, false);
            }

            BeyonderData.playerMap.get(targetPlayer.getUUID()).ifPresent(data -> {
                List<Prophecy> prophecies = new ArrayList<>(data.prophecies());
                Collections.shuffle(prophecies, rng);
                for (int i = 0; i < Math.ceil(prophecies.size() * fraction); i++) {
                    Prophecy prophecy = prophecies.get(i);
                    BeyonderData.playerMap.removeProphecy(targetPlayer.getUUID(), prophecy);
                    if (!BeyonderData.playerMap.contains(thief)) BeyonderData.playerMap.put(thief);
                    BeyonderData.playerMap.addProphecy(thief.getUUID(), new Prophecy(thief.getUUID(), prophecy.trigger(), prophecy.triggerType(), prophecy.casterId()));
                }
            });

            targetPlayer.displayClientMessage(Component.translatable("ability.lotmcraft.steal_fate.victim").withColor(ErrorUtil.FATE_COLOR), false);
        }

        FateHandler.redirectHunters(level, thief, target);
        FateHandler.grantImmunity(thief, target, IMMUNITY_TICKS);
        FateHandler.startErosion(target, thief, fraction, EROSION_TICKS);
        FateHandler.setLastStolen(thief.getUUID(), fate);

        FxHandler.playOnEntity(level, "fate_steal", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4f, 0.6f);
        message(thief, "ability.lotmcraft.steal_fate.success", target.getName(), Math.round(fraction * 100));
    }

    private void give(ServerLevel level, ServerPlayer thief) {
        FateHandler.StolenFate fate = FateHandler.getLastStolen(thief.getUUID());
        if (fate == null) {
            message(thief, "ability.lotmcraft.steal_fate.nothing_to_give");
            return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(thief, 60, 2, false, true);
        if (target == null) {
            message(thief, "ability.lotmcraft.steal_fate.no_target");
            return;
        }
        FateHandler.takeLastStolen(thief.getUUID());

        for (int i = 0; i < thief.getInventory().getContainerSize(); i++) {
            ItemStack stack = thief.getInventory().getItem(i);
            UUID id = FateHandler.getFateItemId(stack);
            if (id == null || !fate.itemTags.contains(id)) continue;
            ItemStack moved = stack.copy();
            thief.getInventory().setItem(i, ItemStack.EMPTY);
            if (target instanceof Player receiver) ErrorUtil.giveOrDrop(receiver, moved);
            else target.spawnAtLocation(moved);
        }

        for (MobEffectInstance effect : fate.effects) {
            target.addEffect(new MobEffectInstance(effect));
            thief.removeEffect(effect.getEffect());
        }

        if (fate.luck != 0) {
            AbilityUtil.addTargetLuck(thief, target, fate.luck);
            AbilityUtil.addTargetLuck(thief, thief, -fate.luck);
        }

        FateHandler.stopErosion(fate.target);
        FateHandler.startErosion(target, thief, fate.fraction, EROSION_TICKS);

        if (thief.getData(ModAttachments.SHAPE_SHIFT).getShape().endsWith(fate.target.toString())) {
            ShapeShiftingUtil.resetShape(thief);
        }

        FxHandler.playOnEntity(level, "fate_steal", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4f, 1.2f);
        message(thief, "ability.lotmcraft.steal_fate.given", fate.targetName, target.getName());
    }
}
