package de.jakob.lotm.entity.client.projectiles.frost_spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.FrostSpearProjectileEntity;
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

public class FrostSpearProjectileRenderer extends EntityRenderer<FrostSpearProjectileEntity> {

    /** Degrees per tick the spear rolls around its own axis. */
    private static final float SPIN_SPEED = 5.0F;

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/frost_spear/frost_spear.png");

    private final FrostSpearProjectileModel model;

    public FrostSpearProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new FrostSpearProjectileModel(context.bakeLayer(FrostSpearProjectileModel.LAYER_LOCATION));
    }

    @Override
    public void render(FrostSpearProjectileEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean petrified = entity.getTags().contains("petrified");
        float age = entity.tickCount + partialTicks;

        this.model.setFrostVisible(!petrified);
        this.model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + 90.0F));
        if (!petrified) {
            poseStack.mulPose(Axis.YP.rotationDegrees(age * SPIN_SPEED));
        }

        ResourceLocation texture = petrified ? LOTMCraft.STONE_TEXTURE : TEXTURE;

        // solid spear, always bright so the ice gleams in the dark
        VertexConsumer solid = buffer.getBuffer(this.model.renderType(texture));
        if (petrified) {
            this.model.renderToBuffer(poseStack, solid, packedLight, OverlayTexture.NO_OVERLAY, 0xFF808080);
        } else {
            this.model.renderToBuffer(poseStack, solid, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

            // faint additive cold shimmer
            float pulse = 0.5F + 0.5F * Mth.sin(age * 0.3F);
            int alpha = (int) (255.0F * (0.10F + 0.08F * pulse));
            poseStack.pushPose();
            poseStack.scale(1.07F, 1.02F, 1.07F);
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(texture));
            this.model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    (alpha << 24) | 0x00BFE6FF);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected int getBlockLightLevel(@NotNull FrostSpearProjectileEntity projectileEntity, @NotNull BlockPos blockpos) {
        return 15;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull FrostSpearProjectileEntity entity) {
        return TEXTURE;
    }
}
