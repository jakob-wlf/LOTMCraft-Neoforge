package de.jakob.lotm.entity.client.projectiles.spear_of_destruction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.SpearOfDestructionProjectileEntity;
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

public class SpearOfDestructionProjectileRenderer extends EntityRenderer<SpearOfDestructionProjectileEntity> {

    private static final float BASE_SCALE = 2F;
    private static final float SPIN_SPEED = 10.0F;

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/spear_of_destruction/spear_of_destruction.png");

    private final SpearOfDestructionProjectileModel model;

    public SpearOfDestructionProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SpearOfDestructionProjectileModel(context.bakeLayer(SpearOfDestructionProjectileModel.LAYER_LOCATION));
    }

    @Override
    public void render(SpearOfDestructionProjectileEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean petrified = entity.getTags().contains("petrified");
        float age = entity.tickCount + partialTicks;

        this.model.setFireVisible(!petrified);
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

            float pulse = 0.5F + 0.5F * Mth.sin(age * 0.45F);
            float flicker = 0.5F + 0.5F * Mth.sin(age * 2.1F + 1.3F);
            float jitter = 0.5F + 0.5F * Mth.sin(age * 3.7F + 0.4F);
            int alpha = (int) (255.0F * (0.20F + 0.14F * pulse + 0.10F * flicker + 0.04F * jitter));
            float glowScale = 1.10F + 0.05F * pulse + 0.02F * flicker;

            poseStack.pushPose();
            poseStack.scale(glowScale, 1.03F, glowScale);
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(texture));
            this.model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    (alpha << 24) | 0x00FF4F8C);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected int getBlockLightLevel(@NotNull SpearOfDestructionProjectileEntity projectileEntity, @NotNull BlockPos blockpos) {
        return 15;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SpearOfDestructionProjectileEntity entity) {
        return TEXTURE;
    }
}
