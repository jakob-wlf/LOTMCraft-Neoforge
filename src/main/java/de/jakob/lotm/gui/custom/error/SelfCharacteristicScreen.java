package de.jakob.lotm.gui.custom.error;

import de.jakob.lotm.gui.custom.selection_gui.ButtonListGui;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SelfCharacteristicScreen extends ButtonListGui<SelfCharacteristicScreen.Option> {

    public record Option(int sequence, boolean regress, int stacks) {}

    public SelfCharacteristicScreen(CompoundTag data) {
        super(Component.translatable("gui.lotmcraft.self_characteristic.title"), build(data));
    }

    private static List<Option> build(CompoundTag data) {
        List<Option> options = new ArrayList<>();
        ListTag list = data.getList("options", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            options.add(new Option(tag.getInt("seq"), tag.getBoolean("regress"), tag.getInt("stacks")));
        }
        if (options.isEmpty()) options.add(new Option(-1, false, 0));
        return options;
    }

    @Override
    protected Component getItemName(Option item) {
        if (item.sequence() < 0) return Component.translatable("gui.lotmcraft.self_characteristic.none");
        if (item.regress()) {
            return Component.translatable("gui.lotmcraft.self_characteristic.regress", item.sequence()).withStyle(ChatFormatting.RED);
        }
        return Component.translatable("gui.lotmcraft.self_characteristic.extra", item.sequence(), item.stacks());
    }

    @Override
    protected void onItemSelected(Option item) {
        if (item.sequence() >= 0) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("seq", item.sequence());
            PacketHandler.sendToServer(new ErrorActionPacket("self_char", tag));
        }
        onClose();
    }
}
