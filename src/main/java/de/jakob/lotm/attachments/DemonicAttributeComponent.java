package de.jakob.lotm.attachments;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class DemonicAttributeComponent implements INBTSerializable<CompoundTag> {
    public static final StreamCodec<RegistryFriendlyByteBuf, DemonicAttributeComponent> STREAM_CODEC =
            StreamCodec.of(
                    (buf, component) -> buf.writeVarInt(component.getPairId()),
                    buf -> {
                        DemonicAttributeComponent component = new DemonicAttributeComponent();
                        component.setPairId(buf.readVarInt());
                        return component;
                    }
            );
    private int pairId = 0;
    public int getPairId() {return pairId;}
    public void setPairId(int pairId) {this.pairId = pairId >= 1 && pairId <= 6 ? pairId : 0;}
    public boolean hasPair() {return pairId >= 1 && pairId <= 6;}
    public void clear() {pairId = 0;}
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("PairId", pairId);
        return tag;
    }
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {pairId = tag.contains("PairId", Tag.TAG_INT) ? tag.getInt("PairId") : 0;}
}