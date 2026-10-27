package de.jakob.lotm.beyonders.abilities.sun.notarization.net;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public record Candidate(UUID id, String name, int sequence, List<ItemStack> inventory) {
    public static final StreamCodec<RegistryFriendlyByteBuf, Candidate> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Candidate::id,
            ByteBufCodecs.STRING_UTF8, Candidate::name,
            ByteBufCodecs.VAR_INT, Candidate::sequence,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, Candidate::inventory,
            Candidate::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, List<Candidate>> LIST_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());
}
