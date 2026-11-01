package de.jakob.lotm.item;

import de.jakob.lotm.LOTMCraft;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, LOTMCraft.MOD_ID);

    public static final Holder<ArmorMaterial> HOLY = ARMOR_MATERIALS.register("holy", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                map.put(ArmorItem.Type.BOOTS, 3);
                map.put(ArmorItem.Type.LEGGINGS, 6);
                map.put(ArmorItem.Type.CHESTPLATE, 8);
                map.put(ArmorItem.Type.HELMET, 3);
                map.put(ArmorItem.Type.BODY, 19);
            }),
            15,                                            // enchantability
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(Items.NETHERITE_INGOT),    // repair item
            List.of(new ArmorMaterial.Layer(
                    ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "holy"))),
            3.0F,                                          // toughness
            0.1F));                                        // knockback resistance

    public static void register(IEventBus eventBus) {
        ARMOR_MATERIALS.register(eventBus);
    }


}