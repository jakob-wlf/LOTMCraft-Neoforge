package de.jakob.lotm.entity.client.ability_entities.darkness_pathway;

import de.jakob.lotm.entity.custom.ability_entities.darkness_pathway.DarknessPourEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class DarknessPourRenderer extends EntityRenderer<DarknessPourEntity> {
    public DarknessPourRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DarknessPourEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/particle/soul.png");
    }
}
