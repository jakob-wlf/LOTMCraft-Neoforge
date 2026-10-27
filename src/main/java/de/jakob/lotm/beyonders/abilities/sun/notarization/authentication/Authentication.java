package de.jakob.lotm.beyonders.abilities.sun.notarization.authentication;

import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractFx;
import de.jakob.lotm.item.custom.NotarySealItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Reveals whether the item in your hand, or in the hand of the player you look at, is a historical summon or a fake. */
public final class Authentication {
    private Authentication() {}
    private static final double RANGE = 6.0;

    public static void authenticate(ServerPlayer caster) {
        ServerPlayer target = findTarget(caster);
        ServerPlayer holder = target != null ? target : caster;
        ItemStack stack = heldStack(holder);
        ServerLevel l = caster.serverLevel();
        Vec3 fx = holder.position().add(0, 1.2, 0);
        String holderName = holder.getGameProfile().getName();

        if (stack.isEmpty()) {
            caster.sendSystemMessage(Component.translatable("contractnotary.auth.empty", holderName).withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean isVoidSummoned = stack.has(DataComponents.CUSTOM_DATA) && stack.get(DataComponents.CUSTOM_DATA).contains("VoidSummonTime");
        Component msg;
        if (!isVoidSummoned) {
            msg = Component.translatable("contractnotary.auth.authentic", stack.getDisplayName()).withStyle(ChatFormatting.GOLD);
            ContractFx.authentic(l, fx);
        } else {
            msg = Component.translatable("contractnotary.auth.fake", stack.getDisplayName()).withStyle(ChatFormatting.RED);
            ContractFx.fake(l, fx);
        }
        caster.sendSystemMessage(Component.literal("\u2600 ").withStyle(ChatFormatting.GOLD).append(msg));
        caster.displayClientMessage(msg, true);
    }

    private static ItemStack heldStack(ServerPlayer p) {
        ItemStack main = p.getMainHandItem();
        if (main.isEmpty() || main.getItem() instanceof NotarySealItem) return p.getOffhandItem();
        return main;
    }

    private static ServerPlayer findTarget(ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        ServerPlayer best = null; double bestDot = 0.92;
        for (ServerPlayer p : caster.serverLevel().players()) {
            if (p == caster || p.isSpectator() || p.distanceToSqr(caster) > RANGE * RANGE) continue;
            double dot = look.dot(p.getEyePosition().subtract(eye).normalize());
            if (dot > bestDot) { bestDot = dot; best = p; }
        }
        return best;
    }
}
