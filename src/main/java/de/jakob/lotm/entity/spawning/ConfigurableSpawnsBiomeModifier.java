package de.jakob.lotm.entity.spawning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.jakob.lotm.util.Config;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.List;

/**
 * Same as neoforge:add_spawns, but scales the spawn weights by the multiplier configured for the given spawn group.
 */
public record ConfigurableSpawnsBiomeModifier(HolderSet<Biome> biomes, List<MobSpawnSettings.SpawnerData> spawners, String spawnGroup) implements BiomeModifier {

    public static final MapCodec<ConfigurableSpawnsBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ConfigurableSpawnsBiomeModifier::biomes),
            MobSpawnSettings.SpawnerData.CODEC.listOf().fieldOf("spawners").forGetter(ConfigurableSpawnsBiomeModifier::spawners),
            Codec.STRING.optionalFieldOf("spawn_group", "mob").forGetter(ConfigurableSpawnsBiomeModifier::spawnGroup)
    ).apply(builder, ConfigurableSpawnsBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !biomes.contains(biome)) return;

        double multiplier = getMultiplier();
        if (multiplier <= 0) return;

        MobSpawnSettingsBuilder spawns = builder.getMobSpawnSettings();
        for (MobSpawnSettings.SpawnerData spawner : spawners) {
            MobSpawnSettings.SpawnerData scaled = spawner;
            if (multiplier != 1.0) {
                int weight = Math.max(1, (int) Math.round(spawner.getWeight().asInt() * multiplier));
                scaled = new MobSpawnSettings.SpawnerData(spawner.type, weight, spawner.minCount, spawner.maxCount);
            }
            spawns.addSpawn(spawner.type.getCategory(), scaled);
        }
    }

    private double getMultiplier() {
        return spawnGroup.equals("beyonder")
                ? Config.BEYONDER_SPAWN_RATE_MULTIPLIER.get()
                : Config.MOB_SPAWN_RATE_MULTIPLIER.get();
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ModBiomeModifiers.CONFIGURABLE_SPAWNS.get();
    }
}
