package de.jakob.lotm.entity.client.projectiles.unshadowed_spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.UnshadowedSpearProjectileEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Unshadowed Spear: a lesser, golden Spear of Light. Bronze-gold shaft, amber crystal blade, small
 * wings, a single rotating halo, a few light beams, sparkles and drifting motes.
 * Model space: the tip points towards -Y, light streams towards +Y (same as the old model).
 * Texture atlas: 256x128 (textures/entity/unshadowed_spear/unshadowed_spear.png).
 */
public class UnshadowedSpearProjectileModel extends EntityModel<UnshadowedSpearProjectileEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "unshadowed_spear"), "main");

    private static final CubeDeformation NO_DEFORM = new CubeDeformation(0.0F);
    private static final int RAY_COUNT = 6;
    private static final int SPARKLE_COUNT = 4;
    private static final int MOTE_COUNT = 8;
    private static final float MOTE_START = -6.0F;
    private static final float MOTE_RANGE = 110.0F;

    private final ModelPart root;
    private final ModelPart[] halos = new ModelPart[1];
    private final ModelPart[] rays = new ModelPart[RAY_COUNT];
    private final ModelPart[] sparkles = new ModelPart[SPARKLE_COUNT];
    private final ModelPart[] motes = new ModelPart[MOTE_COUNT];
    private final float[][] sparkleBase = new float[SPARKLE_COUNT][3];

    public UnshadowedSpearProjectileModel(ModelPart root) {
        super();
        this.root = root;
        for (int i = 0; i < 1; i++) halos[i] = root.getChild("halo_" + i);
        for (int i = 0; i < RAY_COUNT; i++) rays[i] = root.getChild("ray_" + i);
        for (int i = 0; i < SPARKLE_COUNT; i++) {
            sparkles[i] = root.getChild("sparkle_" + i);
            sparkleBase[i] = new float[]{sparkles[i].x, sparkles[i].y, sparkles[i].z};
        }
        for (int i = 0; i < MOTE_COUNT; i++) motes[i] = root.getChild("mote_" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // ── solid body: shaft, gold fittings, crystal blade, wings ──
        CubeListBuilder bodyCubes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 44.0F, 3.0F, NO_DEFORM) // shaft
                .texOffs(0, 48).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band0
                .texOffs(0, 48).addBox(-2.0F, 18.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band1
                .texOffs(0, 48).addBox(-2.0F, 30.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band2
                .texOffs(17, 48).addBox(-2.0F, 44.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // butt
                .texOffs(165, 0).addBox(-1.0F, 47.0F, -1.0F, 2.0F, 6.0F, 2.0F, NO_DEFORM) // tail0
                .texOffs(47, 48).addBox(-0.5F, 53.0F, -0.5F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // tail1
                .texOffs(174, 0).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F, NO_DEFORM) // ringB
                .texOffs(148, 0).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 5.0F, 4.0F, NO_DEFORM) // socket
                .texOffs(199, 0).addBox(-3.0F, -9.0F, -3.0F, 6.0F, 2.0F, 6.0F, NO_DEFORM) // ringA
                .texOffs(224, 0).addBox(-1.5F, -6.0F, -2.5F, 3.0F, 3.0F, 5.0F, NO_DEFORM) // gem
                .texOffs(52, 48).addBox(-3.0F, -12.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade0
                .texOffs(69, 48).addBox(-3.5F, -15.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade1
                .texOffs(88, 48).addBox(-3.5F, -18.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade2
                .texOffs(107, 48).addBox(-3.5F, -21.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade3
                .texOffs(126, 48).addBox(-3.0F, -24.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade4
                .texOffs(143, 48).addBox(-2.5F, -27.0F, -1.0F, 5.0F, 3.0F, 2.0F, NO_DEFORM) // blade5
                .texOffs(158, 48).addBox(-2.0F, -30.0F, -1.0F, 4.0F, 3.0F, 2.0F, NO_DEFORM) // blade6
                .texOffs(171, 48).addBox(-1.5F, -33.0F, -1.0F, 3.0F, 3.0F, 2.0F, NO_DEFORM) // blade7
                .texOffs(182, 48).addBox(-1.0F, -36.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // blade8
                .texOffs(209, 48).addBox(-0.5F, -39.0F, -0.5F, 1.0F, 3.0F, 1.0F, NO_DEFORM) // blade9
                .texOffs(81, 0).addBox(-1.0F, -28.0F, -1.5F, 2.0F, 19.0F, 3.0F, NO_DEFORM) // spine0
                .texOffs(132, 0).addBox(-0.5F, -36.0F, -1.0F, 1.0F, 8.0F, 2.0F, NO_DEFORM) // spine1
                .texOffs(139, 0).addBox(3.0F, -7.0F, -0.5F, 3.0F, 9.0F, 1.0F, NO_DEFORM) // wingR0
                .texOffs(139, 0).addBox(-6.0F, -7.0F, -0.5F, 3.0F, 9.0F, 1.0F, NO_DEFORM) // wingL0
                .texOffs(114, 0).addBox(5.0F, -5.0F, -0.38F, 3.0F, 11.0F, 1.0F, NO_DEFORM) // wingR1
                .texOffs(114, 0).addBox(-8.0F, -5.0F, -0.38F, 3.0F, 11.0F, 1.0F, NO_DEFORM) // wingL1
                .texOffs(123, 0).addBox(7.0F, -2.0F, -0.26F, 3.0F, 10.0F, 1.0F, NO_DEFORM) // wingR2
                .texOffs(123, 0).addBox(-10.0F, -2.0F, -0.26F, 3.0F, 10.0F, 1.0F, NO_DEFORM) // wingL2
                .texOffs(191, 48).addBox(4.0F, -10.0F, 0.1F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covR0
                .texOffs(191, 48).addBox(-7.0F, -10.0F, 0.1F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covL0
                .texOffs(200, 48).addBox(6.0F, -8.0F, 0.2F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covR1
                .texOffs(200, 48).addBox(-9.0F, -8.0F, 0.2F, 3.0F, 4.0F, 1.0F, NO_DEFORM); // covL1
        partdefinition.addOrReplaceChild("spear", bodyCubes, PartPose.offset(0.0F, 18.0F, 0.0F));

        // ── halos (octagonal rings that rotate in setupAnim) ──
        PartDefinition halo_0 = partdefinition.addOrReplaceChild("halo_0", CubeListBuilder.create(), PartPose.offset(0.0F, -2.0F, 0.0F));
        halo_0.addOrReplaceChild("seg_0", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 0.0F, 0.0F));
        halo_0.addOrReplaceChild("seg_1", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 0.785F, 0.0F));
        halo_0.addOrReplaceChild("seg_2", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.571F, 0.0F));
        halo_0.addOrReplaceChild("seg_3", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 2.356F, 0.0F));
        halo_0.addOrReplaceChild("seg_4", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 3.142F, 0.0F));
        halo_0.addOrReplaceChild("seg_5", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 3.927F, 0.0F));
        halo_0.addOrReplaceChild("seg_6", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 4.712F, 0.0F));
        halo_0.addOrReplaceChild("seg_7", CubeListBuilder.create().texOffs(214, 48).addBox(-3.0F, -1.0F, 6.0F, 6.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 5.498F, 0.0F));

        // ── beams of light ──
        partdefinition.addOrReplaceChild("ray_0", CubeListBuilder.create()
                .texOffs(13, 0).addBox(-4.0F, 0.0F, -0.47F, 8.0F, 36.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 62.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("ray_1", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4.0F, 0.0F, -0.44F, 8.0F, 36.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 62.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("ray_2", CubeListBuilder.create()
                .texOffs(51, 0).addBox(-3.0F, 0.0F, -0.41F, 6.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 64.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        partdefinition.addOrReplaceChild("ray_3", CubeListBuilder.create()
                .texOffs(66, 0).addBox(-3.0F, 0.0F, -0.38F, 6.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 64.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        partdefinition.addOrReplaceChild("ray_4", CubeListBuilder.create()
                .texOffs(92, 0).addBox(-2.0F, -16.0F, -0.35F, 4.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("ray_5", CubeListBuilder.create()
                .texOffs(103, 0).addBox(-2.0F, -16.0F, -0.32F, 4.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        // ── sparkles (two crossed star planes each) ──
        PartDefinition sparkle0 = partdefinition.addOrReplaceChild("sparkle_0", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(7.0F, -6.0F, 3.0F));
        sparkle0.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle1 = partdefinition.addOrReplaceChild("sparkle_1", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(-6.0F, -2.0F, -3.0F));
        sparkle1.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle2 = partdefinition.addOrReplaceChild("sparkle_2", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(5.0F, -16.0F, -3.0F));
        sparkle2.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle3 = partdefinition.addOrReplaceChild("sparkle_3", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(-5.0F, 22.0F, 3.0F));
        sparkle3.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(34, 48).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));

        // ── drifting motes ──
        partdefinition.addOrReplaceChild("mote_0", CubeListBuilder.create().texOffs(231, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_1", CubeListBuilder.create().texOffs(240, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_2", CubeListBuilder.create().texOffs(231, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_3", CubeListBuilder.create().texOffs(240, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_4", CubeListBuilder.create().texOffs(231, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_5", CubeListBuilder.create().texOffs(240, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_6", CubeListBuilder.create().texOffs(231, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_7", CubeListBuilder.create().texOffs(240, 48).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);

        return LayerDefinition.create(meshdefinition, 256, 128);
    }

    /** Hide / show the animated light effects (used when the spear is petrified). */
    public void setFxVisible(boolean visible) {
        for (ModelPart p : halos) p.visible = visible;
        for (ModelPart p : rays) p.visible = visible;
        for (ModelPart p : sparkles) p.visible = visible;
        for (ModelPart p : motes) p.visible = visible;
    }

    @Override
    public void setupAnim(UnshadowedSpearProjectileEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // the halo turns and gently breathes
        halos[0].yRot = ageInTicks * 0.09F;
        for (int i = 0; i < 1; i++) {
            float breathe = 1.0F + Mth.sin(ageInTicks * 0.4F + i * 1.6F) * 0.05F;
            halos[i].xScale = breathe;
            halos[i].zScale = breathe;
        }

        // light beams shimmer: tiny sway and length pulses
        for (int i = 0; i < RAY_COUNT; i++) {
            float phase = i * 1.37F;
            rays[i].xRot = Mth.sin(ageInTicks * 0.35F + phase) * 0.04F;
            rays[i].zRot = Mth.cos(ageInTicks * 0.3F + phase * 1.3F) * 0.04F;
            rays[i].yScale = 1.0F + Mth.sin(ageInTicks * 0.7F + phase * 2.1F) * 0.10F;
            float w = 1.0F + Mth.sin(ageInTicks * 1.1F + phase) * 0.06F;
            rays[i].xScale = w;
            rays[i].zScale = w;
        }

        // sparkles spin (they flash when edge-on) and twinkle in size
        for (int i = 0; i < SPARKLE_COUNT; i++) {
            float phase = i * 1.9F;
            sparkles[i].yRot = ageInTicks * (0.12F + (i % 3) * 0.05F) + phase;
            float twinkle = Math.max(0.05F, Math.abs(Mth.sin(ageInTicks * 0.28F + phase)));
            sparkles[i].xScale = twinkle;
            sparkles[i].yScale = twinkle;
            sparkles[i].zScale = twinkle;
            sparkles[i].y = sparkleBase[i][1] + Mth.sin(ageInTicks * 0.15F + phase) * 1.5F;
        }

        // motes: slow spiral away from the spear, fading by shrinking
        for (int i = 0; i < MOTE_COUNT; i++) {
            ModelPart m = motes[i];
            float speed = 0.008F + (i % 4) * 0.003F;
            float p = (ageInTicks * speed + i * 0.6180339F) % 1.0F;
            float dir = (i % 2 == 0) ? 1.0F : -1.0F;
            float angle = i * 2.399F + ageInTicks * (0.08F + (i % 3) * 0.02F) * dir;
            float radius = (4.0F + (i % 5) * 1.8F) * (0.5F + p * 1.3F);
            m.x = Mth.cos(angle) * radius;
            m.z = Mth.sin(angle) * radius;
            m.y = MOTE_START + p * MOTE_RANGE;
            float s = Math.max(0.05F, Mth.sin(p * Mth.PI) * (0.7F + (i % 3) * 0.3F));
            m.xScale = s;
            m.yScale = s;
            m.zScale = s;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
