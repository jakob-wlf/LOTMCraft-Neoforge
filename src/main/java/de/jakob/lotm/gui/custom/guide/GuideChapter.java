package de.jakob.lotm.gui.custom.guide;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record GuideChapter(ResourceLocation id, int order, Component title, ItemStack icon, int color,
                           @Nullable Component quote, @Nullable EntityType<?> coverEntity,
                           List<GuideElement> elements) {
}
