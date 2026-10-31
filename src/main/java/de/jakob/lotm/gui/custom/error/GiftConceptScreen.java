package de.jakob.lotm.gui.custom.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.gui.custom.selection_gui.ButtonListGui;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GiftConceptScreen extends ButtonListGui<GiftConceptScreen.GiftEntry> {

    public record GiftEntry(String kind, String id, int index, Component label) {}

    public GiftConceptScreen(CompoundTag data) {
        super(Component.translatable("gui.lotmcraft.gifting.title"), build(data));
    }

    private static List<GiftEntry> build(CompoundTag data) {
        List<GiftEntry> entries = new ArrayList<>();

        ListTag concepts = data.getList("concepts", Tag.TAG_COMPOUND);
        for (int i = 0; i < concepts.size(); i++) {
            CompoundTag tag = concepts.getCompound(i);
            String id = tag.getString("id");
            entries.add(new GiftEntry("concept", id, 0, Component.translatable("gui.lotmcraft.gifting.concept." + id)
                    .append(Component.literal("  " + tag.getInt("amount") + " " + tag.getString("unit")).withColor(0xB8A6FF))));
        }

        ListTag effects = data.getList("effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            CompoundTag tag = effects.getCompound(i);
            String level = tag.getInt("amplifier") > 0 ? " " + (tag.getInt("amplifier") + 1) : "";
            entries.add(new GiftEntry("effect", "", tag.getInt("index"), Component.translatable("gui.lotmcraft.gifting.effect")
                    .append(Component.literal(" " + tag.getString("name") + level + "  " + tag.getInt("seconds") + "s").withColor(0xE0A0A0))));
        }

        ListTag abilities = data.getList("abilities", Tag.TAG_COMPOUND);
        for (int i = 0; i < abilities.size(); i++) {
            CompoundTag tag = abilities.getCompound(i);
            Ability ability = LOTMCraft.abilityHandler.getById(tag.getString("id"));
            Component name = ability != null ? ability.getName() : Component.literal(tag.getString("id"));
            entries.add(new GiftEntry("ability", "", tag.getInt("index"), Component.translatable("gui.lotmcraft.gifting.ability")
                    .append(Component.literal(" ")).append(name).append(Component.literal("  x" + tag.getInt("uses")).withColor(0xA0C8E0))));
        }

        if (entries.isEmpty()) {
            entries.add(new GiftEntry("none", "", 0, Component.translatable("gui.lotmcraft.gifting.empty")));
        }
        return entries;
    }

    @Override
    protected Component getItemName(GiftEntry item) {
        return item.label();
    }

    @Override
    protected void onItemSelected(GiftEntry item) {
        if (!"none".equals(item.kind())) {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", item.kind());
            tag.putString("id", item.id());
            tag.putInt("index", item.index());
            PacketHandler.sendToServer(new ErrorActionPacket("gift_concept", tag));
        }
        onClose();
    }
}
