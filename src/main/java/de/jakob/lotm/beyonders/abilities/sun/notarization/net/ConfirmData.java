package de.jakob.lotm.beyonders.abilities.sun.notarization.net;

import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.Contract;
import net.minecraft.network.FriendlyByteBuf;

public record ConfirmData(int actionId, String nameA, String nameB, String item, int seconds, String notary, int remainingTicks) {

    public static ConfirmData from(Contract c, long now) {
        return new ConfirmData(c.action.getId(), c.nameA, c.nameB, c.item == null ? "" : c.item.toString(),
                c.seconds, c.notaryName, (int) Math.max(0, c.confirmDeadline - now));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(actionId);
        buf.writeUtf(nameA, 64);
        buf.writeUtf(nameB, 64);
        buf.writeUtf(item, 256);
        buf.writeVarInt(seconds);
        buf.writeUtf(notary, 64);
        buf.writeVarInt(remainingTicks);
    }

    public static ConfirmData read(FriendlyByteBuf buf) {
        return new ConfirmData(buf.readVarInt(), buf.readUtf(64), buf.readUtf(64), buf.readUtf(256),
                buf.readVarInt(), buf.readUtf(64), buf.readVarInt());
    }
}