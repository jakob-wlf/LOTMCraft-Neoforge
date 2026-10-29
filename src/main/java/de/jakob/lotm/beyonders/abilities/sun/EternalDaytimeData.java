package de.jakob.lotm.beyonders.abilities.sun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EternalDaytimeData extends SavedData {

    private static final String NAME = "eternal_daytime_data";

    public static final SavedData.Factory<EternalDaytimeData> FACTORY =
            new SavedData.Factory<>(
                    EternalDaytimeData::new,
                    EternalDaytimeData::load,
                    null
            );

    private final Map<UUID, Domain> domains = new HashMap<>();

    public static EternalDaytimeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for(Domain domain : domains.values()) {
            CompoundTag domainTag = new CompoundTag();
            domainTag.putUUID("owner", domain.owner);
            domainTag.putString("dimension", domain.dimension.location().toString());
            domainTag.putDouble("x", domain.center.x);
            domainTag.putDouble("y", domain.center.y);
            domainTag.putDouble("z", domain.center.z);
            domainTag.putDouble("radius", domain.radius);
            domainTag.putInt("sequence", domain.casterSequence);
            domainTag.putLongArray("lights", domain.lightBlocks.stream().mapToLong(BlockPos::asLong).toArray());
            list.add(domainTag);
        }
        tag.put("domains", list);
        return tag;
    }

    public static EternalDaytimeData load(CompoundTag tag, HolderLookup.Provider registries) {
        EternalDaytimeData data = new EternalDaytimeData();
        ListTag list = tag.getList("domains", Tag.TAG_COMPOUND);
        for(int i = 0; i < list.size(); i++) {
            CompoundTag domainTag = list.getCompound(i);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(domainTag.getString("dimension")));
            Domain domain = new Domain(
                    domainTag.getUUID("owner"),
                    dimension,
                    new Vec3(domainTag.getDouble("x"), domainTag.getDouble("y"), domainTag.getDouble("z")),
                    domainTag.getDouble("radius"),
                    domainTag.getInt("sequence")
            );
            for(long l : domainTag.getLongArray("lights")) {
                domain.lightBlocks.add(BlockPos.of(l));
            }
            data.domains.put(domain.owner, domain);
        }
        return data;
    }

    @Nullable
    public Domain getDomain(UUID owner) {
        return domains.get(owner);
    }

    public Collection<Domain> getDomains() {
        return Collections.unmodifiableCollection(domains.values());
    }

    public void addDomain(Domain domain) {
        domains.put(domain.owner, domain);
        setDirty();
    }

    public void removeDomain(MinecraftServer server, UUID owner) {
        Domain domain = domains.remove(owner);
        if(domain == null) return;

        ServerLevel level = server.getLevel(domain.dimension);
        if(level != null) {
            for(BlockPos pos : domain.lightBlocks) {
                if(level.getBlockState(pos).is(Blocks.LIGHT)) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
        setDirty();
    }

    @Nullable
    public Domain getAffectingDomain(Level level, Vec3 pos, int sequence) {
        for(Domain domain : domains.values()) {
            if(domain.contains(level, pos) && sequence >= domain.casterSequence) {
                return domain;
            }
        }
        return null;
    }

    public static class Domain {
        public final UUID owner;
        public final ResourceKey<Level> dimension;
        public final Vec3 center;
        public final double radius;
        public final int casterSequence;
        public final Set<BlockPos> lightBlocks = new HashSet<>();

        public Domain(UUID owner, ResourceKey<Level> dimension, Vec3 center, double radius, int casterSequence) {
            this.owner = owner;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.casterSequence = casterSequence;
        }

        public boolean contains(Level level, Vec3 pos) {
            if(!level.dimension().equals(dimension)) return false;
            double dx = pos.x - center.x;
            double dz = pos.z - center.z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }
}
