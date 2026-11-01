package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

public final class ContractText {
    private ContractText() {}

    public static Component sentence(ContractAction action, Component a, Component b, @Nullable Component item,
                                     int seconds, ChatFormatting nameFmt, ChatFormatting itemFmt, ChatFormatting timeFmt) {
        Component A = quote(a, nameFmt), B = quote(b, nameFmt);
        Component T = Component.literal(ContractRules.formatDuration(seconds)).withStyle(timeFmt);
        if (action == ContractAction.DROP_ITEM) {
            Component I = item == null ? Component.translatable("contractnotary.sentence.any_item").withStyle(itemFmt)
                                       : quote(item, itemFmt);
            return Component.translatable("contractnotary.sentence.drop_item", A, I, B, T);
        }
        return Component.translatable("contractnotary.sentence." + action.key(), A, B, T);
    }

    private static Component quote(Component c, ChatFormatting f) {
        return Component.literal("\u201c").append(c).append("\u201d").withStyle(f);
    }
}
