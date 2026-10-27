package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class Contract {
    public final UUID id, partyA, partyB, notary;
    public final String nameA, nameB, notaryName;
    public final ContractAction action;
    @Nullable public final ResourceLocation item;
    public final int seconds;

    public int seqA = 9, seqB = 9;
    public boolean confirmedA, confirmedB;
    public long confirmDeadline, endTick, graceUntil;

    public Contract(UUID id, UUID partyA, String nameA, UUID partyB, String nameB, UUID notary, String notaryName,
                    ContractAction action, @Nullable ResourceLocation item, int seconds) {
        this.id = id; this.partyA = partyA; this.nameA = nameA; this.partyB = partyB; this.nameB = nameB;
        this.notary = notary; this.notaryName = notaryName; this.action = action; this.item = item; this.seconds = seconds;
    }

    public boolean involves(UUID u) { return partyA.equals(u) || partyB.equals(u); }

    public Component sentence() {
        Component itemName = item == null ? null : BuiltInRegistries.ITEM.get(item).getDescription();
        return ContractText.sentence(action, Component.literal(nameA), Component.literal(nameB), itemName, seconds,
                ChatFormatting.GOLD, ChatFormatting.AQUA, ChatFormatting.YELLOW);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id); t.putUUID("a", partyA); t.putUUID("b", partyB); t.putUUID("notary", notary);
        t.putString("nameA", nameA); t.putString("nameB", nameB); t.putString("notaryName", notaryName);
        t.putInt("action", action.getId());
        if (item != null) t.putString("item", item.toString());
        t.putInt("seconds", seconds); t.putInt("seqA", seqA); t.putInt("seqB", seqB);
        t.putLong("end", endTick); t.putLong("grace", graceUntil);
        return t;
    }

    @Nullable
    public static Contract load(CompoundTag t) {
        ContractAction a = ContractAction.byId(t.getInt("action"));
        if (a == null) return null;
        ResourceLocation item = t.contains("item") ? ResourceLocation.tryParse(t.getString("item")) : null;
        Contract c = new Contract(t.getUUID("id"), t.getUUID("a"), t.getString("nameA"), t.getUUID("b"), t.getString("nameB"),
                t.getUUID("notary"), t.getString("notaryName"), a, item, t.getInt("seconds"));
        c.seqA = t.getInt("seqA"); c.seqB = t.getInt("seqB");
        c.endTick = t.getLong("end"); c.graceUntil = t.getLong("grace");
        return c;
    }
}
