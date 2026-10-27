package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class BreachPenalty {
    private BreachPenalty() {}

    public static int level(int violatorSeq, int otherSeq) {
        int diff = violatorSeq - otherSeq;
        return Mth.clamp(3 + Math.abs(diff) + (diff > 0 ? 2 : 0), 1, 10);
    }

    private static void add(ServerPlayer p, Holder<MobEffect> e, int ticks, int amp) {
        p.addEffect(new MobEffectInstance(e, ticks, Math.max(0, amp), false, true, true));
    }

    public static void apply(ServerPlayer p, int level, int diff) {
        long now = p.server.overworld().getGameTime();
        int ticks = (30 + level * 30) * 20;
        add(p, MobEffects.WEAKNESS, ticks, level / 3);
        add(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, level / 3);
        add(p, MobEffects.GLOWING, ticks, 0);
        if (level >= 3) add(p, MobEffects.DIG_SLOWDOWN, ticks, level / 4);
        if (level >= 4) add(p, MobEffects.HUNGER, ticks, level / 2);
        if (level >= 5) add(p, MobEffects.CONFUSION, ticks / 4, 0);
        if (level >= 6) add(p, MobEffects.BLINDNESS, ticks / 5, 0);
        if (level >= 8) add(p, MobEffects.DARKNESS, ticks / 3, 0);
        float dmg = Math.min(level * 1.5f, p.getHealth() - 1f);
        if (dmg > 0) { p.invulnerableTime = 0; p.hurt(p.damageSources().magic(), dmg); }
        ContractSavedData.get(p.server).seal(p.getUUID(), now + ticks);
        ContractFx.judgment(p, level);
        p.sendSystemMessage(Component.translatable("contractnotary.msg.judgment", level, ContractRules.formatDuration(ticks / 20))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    public static void empower(ServerPlayer p, int level) {
        long now = p.server.overworld().getGameTime();
        int ticks = (20 + level * 20) * 20;
        add(p, MobEffects.DAMAGE_BOOST, ticks, level / 3);
        add(p, MobEffects.DAMAGE_RESISTANCE, ticks, (level - 1) / 3);
        add(p, MobEffects.REGENERATION, ticks, level / 4);
        add(p, MobEffects.MOVEMENT_SPEED, ticks, level / 5);
        add(p, MobEffects.ABSORPTION, ticks, level / 3);
        add(p, MobEffects.FIRE_RESISTANCE, ticks, 0);
        ContractSavedData.get(p.server).setEmpowered(p.getUUID(), now + ticks, level);
        ContractFx.blessing(p);
        p.sendSystemMessage(Component.translatable("contractnotary.msg.blessing", level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }
}
