package de.jakob.lotm.events.custom;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.Nullable;

public class ArtifactUsedEvent extends Event{
    private @Nullable LivingEntity sourceEntity;
    private ItemStack artifactItem;
    private @Nullable String ability;

    public ArtifactUsedEvent(@Nullable LivingEntity sourceEntity, ItemStack artifactItem, @Nullable String ability) {
        this.sourceEntity = sourceEntity;
        this.artifactItem = artifactItem;
        this.ability = ability;
    }

    public @Nullable LivingEntity getSourceEntity() {
        return sourceEntity;
    }

    public void setSourceEntity(@Nullable LivingEntity sourceEntity) {
        this.sourceEntity = sourceEntity;
    }

    public ItemStack getArtifactItem() {
        return artifactItem;
    }

    public void setArtifactItem(ItemStack artifactItem) {
        this.artifactItem = artifactItem;
    }

    public @Nullable String getAbility() {
        return ability;
    }

    public void setAbility(@Nullable String ability) {
        this.ability = ability;
    }
}