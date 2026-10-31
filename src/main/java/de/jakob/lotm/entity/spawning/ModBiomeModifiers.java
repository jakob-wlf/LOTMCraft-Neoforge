package de.jakob.lotm.entity.spawning;

import com.mojang.serialization.MapCodec;
import de.jakob.lotm.LOTMCraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, LOTMCraft.MOD_ID);

    public static final Supplier<MapCodec<ConfigurableSpawnsBiomeModifier>> CONFIGURABLE_SPAWNS =
            BIOME_MODIFIER_SERIALIZERS.register("configurable_add_spawns", () -> ConfigurableSpawnsBiomeModifier.CODEC);

    public static void register(IEventBus modEventBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
