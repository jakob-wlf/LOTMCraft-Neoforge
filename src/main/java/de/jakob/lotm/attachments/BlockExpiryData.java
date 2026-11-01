
package de.jakob.lotm.attachments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class BlockExpiryData extends SavedData {

    private record Entry(long expireAt, BlockState state, BlockState originalState) {}

    private final Map<BlockPos, Entry> expiries = new HashMap<>();

    public static final SavedData.Factory<BlockExpiryData> FACTORY =
            new SavedData.Factory<>(BlockExpiryData::new, BlockExpiryData::load, null);

    public static BlockExpiryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "block_expiry");
    }

    /** Mark a single block. Captures its current state. Returns false if skipped (unloaded or air). */
    public boolean schedule(ServerLevel level, BlockPos pos, long ticksFromNow) {
        return schedule(level, pos, ticksFromNow, null);
    }
    /** Schedule a temporary block, optionally restoring its original state. */
    public boolean schedule(ServerLevel level, BlockPos pos, long ticksFromNow, BlockState originalState) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        expiries.put(pos.immutable(), new Entry(level.getGameTime() + ticksFromNow, state, originalState));
        setDirty();
        return true;
    }

    /** Mark many blocks at once. Returns how many were actually marked. */
    public int scheduleAll(ServerLevel level, Collection<BlockPos> positions, long ticksFromNow) {
        int count = 0;
        for (BlockPos pos : positions) {
            if (schedule(level, pos, ticksFromNow)) count++;
        }
        return count;
    }

    public void cancel(BlockPos pos) {
        if (expiries.remove(pos) != null) setDirty();
    }

    public void tick(ServerLevel level) {
        if (expiries.isEmpty()) return;
        // Collect first, then process, so removals can't cause a ConcurrentModificationException
        long now = level.getGameTime();
        List<Map.Entry<BlockPos, Entry>> due = new ArrayList<>();
        for (var e : expiries.entrySet()) {
            if (now >= e.getValue().expireAt() && level.isLoaded(e.getKey())) {
                due.add(e);
            }
        }
        if (due.isEmpty()) return;

        for (var e : due) {
            BlockPos pos = e.getKey();
            Entry entry = e.getValue();

            // Only remove if the block is still exactly what was marked
            if (level.getBlockState(pos).equals(entry.state())) {
                if (entry.originalState() != null) {
                    level.setBlock(pos, entry.originalState(), Block.UPDATE_ALL);
                } else {
                    level.removeBlock(pos, false);
                }
            }
            expiries.remove(pos);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        expiries.forEach((pos, entry) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", pos.asLong());
            t.putLong("time", entry.expireAt());
            t.put("state", NbtUtils.writeBlockState(entry.state()));
            if (entry.originalState() != null) t.put("original", NbtUtils.writeBlockState(entry.originalState()));
            list.add(t);
        });
        tag.put("entries", list);
        return tag;
    }
    public static BlockExpiryData load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockExpiryData data = new BlockExpiryData();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BlockState state = NbtUtils.readBlockState(blocks, c.getCompound("state"));
            BlockState originalState = c.contains("original", Tag.TAG_COMPOUND) ? NbtUtils.readBlockState(blocks, c.getCompound("original")) : null;
            data.expiries.put(BlockPos.of(c.getLong("pos")), new Entry(c.getLong("time"), state, originalState));
        }

        return data;
    }
}
