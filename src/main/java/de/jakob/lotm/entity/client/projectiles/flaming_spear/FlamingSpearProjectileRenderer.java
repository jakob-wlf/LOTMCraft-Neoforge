package de.jakob.lotm.entity.client.projectiles.flaming_spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.FlamingSpearProjectileEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class FlamingSpearProjectileRenderer extends EntityRenderer<FlamingSpearProjectileEntity> {

    private static final float SPIN_SPEED = 14.0F;

    private final FlamingSpearProjectileModel model;

    public FlamingSpearProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new FlamingSpearProjectileModel(context.bakeLayer(FlamingSpearProjectileModel.LAYER_LOCATION));
    }

    @Override
    public void render(FlamingSpearProjectileEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean petrified = entity.getTags().contains("petrified");
        float age = entity.tickCount + partialTicks;
        float size = entity.getSize();

        this.model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);

        poseStack.pushPose();
        poseStack.scale(size, size, size);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + 90.0F));
        if (!petrified) {
            poseStack.mulPose(Axis.YP.rotationDegrees(age * SPIN_SPEED));
        }

        ResourceLocation texture = petrified ? LOTMCraft.STONE_TEXTURE : this.getTextureLocation(entity);

        VertexConsumer solid = buffer.getBuffer(this.model.renderType(texture));
        if (petrified) {
            this.model.renderToBuffer(poseStack, solid, packedLight, OverlayTexture.NO_OVERLAY, 0xFF808080);
        } else {
            this.model.renderToBuffer(poseStack, solid, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

            float pulse = 0.5F + 0.5F * Mth.sin(age * 0.6F);
            float flicker = 0.5F + 0.5F * Mth.sin(age * 1.7F + 1.3F);
            int alpha = (int) (255.0F * (0.20F + 0.16F * pulse + 0.08F * flicker));
            float glowScale = 1.10F + 0.04F * pulse;

            poseStack.pushPose();
            poseStack.scale(glowScale, 1.03F, glowScale); // fatter, barely longer, so the tip doesn't poke far ahead
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(texture));
            this.model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    (alpha << 24) | 0x00FFFFFF);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected int getBlockLightLevel(@NotNull FlamingSpearProjectileEntity projectileEntity, @NotNull BlockPos blockpos) {
        return 15;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull FlamingSpearProjectileEntity entity) {
        return switch (entity.getVariant()) {
            case DEFAULT -> tex("flaming_spear");
            case CRIMSON -> tex("flaming_spear7");
            case WHITISH_RED -> tex("flaming_spear6");
            case WHITE -> tex("flaming_spear5");
            case WHITISH_BLUE -> tex("flaming_spear4");
            case AZURE -> tex("flaming_spear3");
            case AZURE_VIOLENT -> tex("flaming_spear2");
            case VIOLENT -> tex("flaming_spear1");
            case DARK -> tex("flaming_spear0");
        };
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/flaming_spear/" + name + ".png");
    }
}
