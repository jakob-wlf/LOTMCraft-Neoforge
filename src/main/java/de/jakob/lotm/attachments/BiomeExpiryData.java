package de.jakob.lotm.attachments;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// Temporarily swaps chunk biomes and restores the originals later. Saved with the world, so it survives crashes.
public class BiomeExpiryData extends SavedData {

    private static final int CHECK_INTERVAL_TICKS = 20;

    private record Entry(long expireAt, List<String> palette, int[] cells) {}

    private final Map<ChunkPos, Entry> expiries = new HashMap<>();

    public static final SavedData.Factory<BiomeExpiryData> FACTORY =
            new SavedData.Factory<>(BiomeExpiryData::new, BiomeExpiryData::load, null);

    public static BiomeExpiryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "biome_expiry");
    }
    public int schedule(ServerLevel level, Collection<ChunkPos> chunks, ResourceKey<Biome> biomeKey, long ticksFromNow) {
        Holder<Biome> target = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biomeKey);
        long expireAt = level.getGameTime() + ticksFromNow;
        List<ChunkAccess> changed = new ArrayList<>();
        for (ChunkPos pos : chunks) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            Entry existing = expiries.get(pos);
            if (existing == null) expiries.put(pos, capture(chunk, expireAt));
                // Overlapping casts keep the true originals and only push the expiry back
            else expiries.put(pos, new Entry(Math.max(existing.expireAt(), expireAt), existing.palette(), existing.cells()));
            for (LevelChunkSection section : chunk.getSections()) {
                PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();
                for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) biomes.set(x, y, z, target);
            }
            chunk.setUnsaved(true);
            changed.add(chunk);
        }
        if (!changed.isEmpty()) {
            level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
            setDirty();
        }
        return changed.size();
    }
    public void cancel(ServerLevel level, ChunkPos pos) {
        Entry entry = expiries.remove(pos);
        if (entry == null) return;
        List<ChunkAccess> changed = new ArrayList<>();
        restore(level, pos, entry, changed);
        level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
        setDirty();
    }
    public void tick(ServerLevel level) {
        if (expiries.isEmpty() || level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;
        long now = level.getGameTime();
        List<ChunkPos> due = new ArrayList<>();
        for (var e : expiries.entrySet()) {
            if (now >= e.getValue().expireAt() && level.hasChunk(e.getKey().x, e.getKey().z)) due.add(e.getKey());
        }
        if (due.isEmpty()) return;
        List<ChunkAccess> changed = new ArrayList<>();
        for (ChunkPos pos : due) restore(level, pos, expiries.remove(pos), changed);
        level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
        setDirty();
    }
    private static Entry capture(LevelChunk chunk, long expireAt) {
        LevelChunkSection[] sections = chunk.getSections();
        List<String> palette = new ArrayList<>();
        int[] cells = new int[sections.length * 64];
        int n = 0;
        for (LevelChunkSection section : sections) {
            PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
                String id = biomes.get(x, y, z).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
                int idx = palette.indexOf(id);
                if (idx < 0) {palette.add(id); idx = palette.size() - 1;}
                cells[n++] = idx;
            }
        }
        return new Entry(expireAt, palette, cells);
    }
    private static void restore(ServerLevel level, ChunkPos pos, Entry entry, List<ChunkAccess> changed) {
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<Holder<Biome>> palette = new ArrayList<>();
        for (String id : entry.palette()) {
            palette.add(registry.getHolder(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id)))
                    .map(h -> (Holder<Biome>) h).orElseGet(() -> registry.getHolderOrThrow(Biomes.PLAINS)));
        }
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        LevelChunkSection[] sections = chunk.getSections();
        int n = 0;
        for (int i = 0; i < sections.length && n + 64 <= entry.cells().length; i++) {
            PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) sections[i].getBiomes();
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) biomes.set(x, y, z, palette.get(entry.cells()[n++]));
        }
        chunk.setUnsaved(true);
        changed.add(chunk);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        expiries.forEach((pos, entry) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("x", pos.x);
            t.putInt("z", pos.z);
            t.putLong("time", entry.expireAt());
            ListTag pal = new ListTag();
            for (String id : entry.palette()) pal.add(StringTag.valueOf(id));
            t.put("palette", pal);
            t.putIntArray("cells", entry.cells());
            list.add(t);
        });
        tag.put("entries", list);
        return tag;
    }
    public static BiomeExpiryData load(CompoundTag tag, HolderLookup.Provider registries) {
        BiomeExpiryData data = new BiomeExpiryData();
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            List<String> palette = new ArrayList<>();
            for (Tag p : c.getList("palette", Tag.TAG_STRING)) palette.add(p.getAsString());
            data.expiries.put(new ChunkPos(c.getInt("x"), c.getInt("z")), new Entry(c.getLong("time"), palette, c.getIntArray("cells")));
        }
        return data;
    }
}