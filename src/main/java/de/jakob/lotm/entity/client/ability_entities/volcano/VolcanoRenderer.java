package de.jakob.lotm.entity.client.ability_entities.volcano;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.ability_entities.VolcanoEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

public class VolcanoRenderer extends EntityRenderer<VolcanoEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/volcano/volcano.png");
    private static final ResourceLocation GLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/volcano/volcano_glow.png");

    private static final float SCALE = 4.0F;

    private final VolcanoModel<VolcanoEntity> model;

    public VolcanoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new VolcanoModel<>(context.bakeLayer(VolcanoModel.LAYER_LOCATION));
    }

    @Override
    public void render(VolcanoEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean petrified = entity.getTags().contains("petrified");

        poseStack.pushPose();

        Random random = new Random(entity.getId());

        poseStack.scale(SCALE, -SCALE, SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(random.nextInt(360)));

        int color = petrified ? 0xFF808080 : 0xFFFFFFFF;
        RenderType renderType = petrified ? this.model.renderType(LOTMCraft.STONE_TEXTURE) :
                this.model.renderType(this.getTextureLocation(entity));
        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);

        if (!petrified) {
            float time = entity.tickCount + partialTicks;
            int alpha = (int) (200 + 55 * Math.sin(time * 0.12F));
            int glowColor = (alpha << 24) | 0x00FFFFFF;
            VertexConsumer glow = buffer.getBuffer(RenderType.eyes(GLOW_TEXTURE));
            this.model.renderToBuffer(poseStack, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowColor);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected int getBlockLightLevel(VolcanoEntity projectileEntity, BlockPos blockpos) { return 15; }

    @Override
    public ResourceLocation getTextureLocation(VolcanoEntity entity) { return TEXTURE; }
}
