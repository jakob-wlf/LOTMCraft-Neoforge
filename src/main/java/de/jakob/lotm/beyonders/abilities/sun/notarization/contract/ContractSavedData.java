package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class ContractSavedData extends SavedData {
    public final Map<UUID, Contract> active = new LinkedHashMap<>();
    private final Map<UUID, Integer> sequences = new HashMap<>();
    private final Map<UUID, Integer> debts = new HashMap<>();
    private final Map<UUID, Long> sealedUntil = new HashMap<>();
    private final Map<UUID, long[]> empowered = new HashMap<>();

    public static ContractSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ContractSavedData::new, ContractSavedData::load, null), "contractnotary_data");
    }

    public int getSequence(UUID id) { return sequences.getOrDefault(id, 9); }
    public void setSequence(UUID id, int seq) { sequences.put(id, seq); setDirty(); }

    public void addDebt(UUID id, int level) { debts.merge(id, level, Math::max); setDirty(); }
    public int takeDebt(UUID id) { Integer v = debts.remove(id); if (v != null) setDirty(); return v == null ? 0 : v; }

    public void seal(UUID id, long until) { sealedUntil.merge(id, until, Math::max); setDirty(); }
    public boolean isSealed(UUID id, long now) { return sealedUntil.getOrDefault(id, 0L) > now; }

    public void setEmpowered(UUID id, long until, int level) { empowered.put(id, new long[]{until, level}); setDirty(); }
    public int empowermentLevel(UUID id, long now) {
        long[] e = empowered.get(id);
        return e != null && e[0] > now ? (int) e[1] : 0;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        active.values().forEach(c -> list.add(c.save()));
        tag.put("active", list);
        CompoundTag s = new CompoundTag(); sequences.forEach((k, v) -> s.putInt(k.toString(), v)); tag.put("sequences", s);
        CompoundTag d = new CompoundTag(); debts.forEach((k, v) -> d.putInt(k.toString(), v)); tag.put("debts", d);
        CompoundTag l = new CompoundTag(); sealedUntil.forEach((k, v) -> l.putLong(k.toString(), v)); tag.put("sealed", l);
        CompoundTag e = new CompoundTag(); empowered.forEach((k, v) -> e.putLongArray(k.toString(), v)); tag.put("empowered", e);
        return tag;
    }

    public static ContractSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ContractSavedData data = new ContractSavedData();
        for (Tag t : tag.getList("active", Tag.TAG_COMPOUND)) {
            Contract c = Contract.load((CompoundTag) t);
            if (c != null) data.active.put(c.id, c);
        }
        CompoundTag s = tag.getCompound("sequences"); for (String k : s.getAllKeys()) data.sequences.put(UUID.fromString(k), s.getInt(k));
        CompoundTag d = tag.getCompound("debts"); for (String k : d.getAllKeys()) data.debts.put(UUID.fromString(k), d.getInt(k));
        CompoundTag l = tag.getCompound("sealed"); for (String k : l.getAllKeys()) data.sealedUntil.put(UUID.fromString(k), l.getLong(k));
        CompoundTag e = tag.getCompound("empowered"); for (String k : e.getAllKeys()) data.empowered.put(UUID.fromString(k), e.getLongArray(k));
        return data;
    }
}
