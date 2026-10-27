package de.jakob.lotm.entity.client.projectiles.unshadowed_spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.UnshadowedSpearProjectileEntity;
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

public class UnshadowedSpearProjectileRenderer extends EntityRenderer<UnshadowedSpearProjectileEntity> {

    private static final float BASE_SCALE = 1.85F;
    private static final float SPIN_SPEED = 3.0F;

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/unshadowed_spear/unshadowed_spear.png");

    private final UnshadowedSpearProjectileModel model;

    public UnshadowedSpearProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new UnshadowedSpearProjectileModel(context.bakeLayer(UnshadowedSpearProjectileModel.LAYER_LOCATION));
    }

    @Override
    public void render(UnshadowedSpearProjectileEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean petrified = entity.getTags().contains("petrified");
        float age = entity.tickCount + partialTicks;

        this.model.setFxVisible(!petrified);
        this.model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);

        poseStack.pushPose();
        poseStack.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + 90.0F));
        if (!petrified) {
            poseStack.mulPose(Axis.YP.rotationDegrees(age * SPIN_SPEED));
        }

        ResourceLocation texture = petrified ? LOTMCraft.STONE_TEXTURE : TEXTURE;

        VertexConsumer solid = buffer.getBuffer(this.model.renderType(texture));
        if (petrified) {
            this.model.renderToBuffer(poseStack, solid, packedLight, OverlayTexture.NO_OVERLAY, 0xFF808080);
        } else {
            this.model.renderToBuffer(poseStack, solid, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

            float pulse = 0.5F + 0.5F * Mth.sin(age * 0.25F);
            float shimmer = 0.5F + 0.5F * Mth.sin(age * 1.1F + 0.7F);
            int alpha = (int) (255.0F * (0.12F + 0.10F * pulse + 0.04F * shimmer));
            float glowScale = 1.07F + 0.04F * pulse;

            poseStack.pushPose();
            poseStack.scale(glowScale, 1.02F, glowScale);
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(texture));
            this.model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    (alpha << 24) | 0x00FFFFFF);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected int getBlockLightLevel(@NotNull UnshadowedSpearProjectileEntity projectileEntity, @NotNull BlockPos blockpos) {
        return 15;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull UnshadowedSpearProjectileEntity entity) {
        return TEXTURE;
    }
}
