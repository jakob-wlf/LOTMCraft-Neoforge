package de.jakob.lotm.entity.client.ability_entities.justiciar_pathway.judgment_sword;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.ability_entities.justiciar_pathway.JudgmentSwordEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

public class JudgmentSwordRenderer extends EntityRenderer<JudgmentSwordEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            LOTMCraft.MOD_ID, "textures/entity/judgment_sword/judgment_sword_silver.png");

    public JudgmentSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public void render(JudgmentSwordEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        Random random = new Random(entity.getId());

        // Mesh origin is the blade tip, slightly embedded once the entity lands.
        poseStack.translate(0, -.3, 0);
        poseStack.scale(1.6f, 1.6f, 1.6f);
        poseStack.mulPose(Axis.YP.rotationDegrees(random.nextInt(360)));

        var vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        JudgmentSwordModel.render(poseStack, vertexConsumer, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public boolean shouldRender(JudgmentSwordEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z)
                && frustum.isVisible(entity.getBoundingBox().inflate(2, 6, 2));
    }

    @Override
    protected int getBlockLightLevel(JudgmentSwordEntity entity, BlockPos blockpos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(JudgmentSwordEntity entity) {
        return TEXTURE;
    }
}
