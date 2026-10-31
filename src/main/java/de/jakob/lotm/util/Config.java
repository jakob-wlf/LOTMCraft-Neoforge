package de.jakob.lotm.util;

import de.jakob.lotm.LOTMCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_BIOMES = BUILDER
            .comment("A list of biomes to ban npc spawn.")
            .defineListAllowEmpty("banned_biomes", List.of("minecraft:mushroom_fields", "minecraft:deep_dark"),
                    obj -> obj instanceof String && ((String) obj).contains(":"));

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_SUMMONED_ITEMS = BUILDER
            .comment("A list of items to ban from being summoned by historical summon.")
            .defineListAllowEmpty("banned_summoned_items", List.of(),
                    obj -> obj instanceof String && ((String) obj).contains(":"));


    static {
        BUILDER.comment("Spawn rates. Multipliers scale the spawn weight of this mod's natural spawns (1.0 = default, 0 = disabled).",
                "Changes take effect after a world/server restart.").push("spawning");
    }

    public static final ModConfigSpec.DoubleValue BEYONDER_SPAWN_RATE_MULTIPLIER = BUILDER
            .comment("Spawn rate multiplier for Beyonder NPCs.")
            .defineInRange("beyonder_spawn_rate_multiplier", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue MOB_SPAWN_RATE_MULTIPLIER = BUILDER
            .comment("Spawn rate multiplier for all other mobs of this mod (spirit world creatures, grim reapers, ...).")
            .defineInRange("mob_spawn_rate_multiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Law of Beyonder Characteristics Convergence.",
                "Time (in seconds) a Beyonder has to spend in the overworld before convergence can start attracting other Beyonders.").push("convergence");
    }

    public static final ModConfigSpec.IntValue CONVERGENCE_TIME_LOW_SEQUENCES = BUILDER
            .comment("Time for Sequence 9 - 7.")
            .defineInRange("convergence_time_seq_9_to_7", 60 * 35, 0, Integer.MAX_VALUE / 20);

    public static final ModConfigSpec.IntValue CONVERGENCE_TIME_MID_SEQUENCES = BUILDER
            .comment("Time for Sequence 6 - 5.")
            .defineInRange("convergence_time_seq_6_to_5", 60 * 55, 0, Integer.MAX_VALUE / 20);

    public static final ModConfigSpec.IntValue CONVERGENCE_TIME_HIGH_SEQUENCES = BUILDER
            .comment("Time for Sequence 4 - 0.")
            .defineInRange("convergence_time_seq_4_to_0", 60 * 90, 0, Integer.MAX_VALUE / 20);

    static {
        BUILDER.pop();
        BUILDER.push("spirituality");
    }

    public static final ModConfigSpec.DoubleValue SPIRITUALITY_COST_MULTIPLIER = BUILDER
            .comment("Multiplier for all spirituality players lose (ability costs, toggle upkeep, drains). 1.0 = default, 0 = free.")
            .defineInRange("spirituality_cost_multiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static Set<ResourceLocation> biomes;
    public static Set<ResourceLocation> items;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        biomes = Config.BANNED_BIOMES.get().stream()
                .map(ResourceLocation::parse)
                .collect(Collectors.toSet());

        items = Config.BANNED_SUMMONED_ITEMS.get().stream()
                .map(ResourceLocation::parse)
                .collect(Collectors.toSet());
    }
}
