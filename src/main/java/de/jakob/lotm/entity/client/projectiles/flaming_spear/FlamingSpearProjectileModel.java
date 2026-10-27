package de.jakob.lotm.entity.client.projectiles.flaming_spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.FlamingSpearProjectileEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Flaming spear: a charred, ember-cracked shaft, a molten leaf-shaped blade with crescent horns,
 * and animated flame tongues + drifting embers. Model space: the tip points towards -Y and the
 * flames stream towards +Y (matches the existing renderer rotations).
 * Texture atlas: 256x128 (see textures/entity/flaming_spear/*.png).
 */
public class FlamingSpearProjectileModel extends EntityModel<FlamingSpearProjectileEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "flaming_spear"), "main");

    private static final CubeDeformation NO_DEFORM = new CubeDeformation(0.0F);
    private static final int FLAME_COUNT = 16;
    private static final int EMBER_COUNT = 14;
    private static final float EMBER_START = 5.0F;
    private static final float EMBER_RANGE = 120.0F;

    private final ModelPart root;
    private final ModelPart spear;
    private final ModelPart[] flames = new ModelPart[FLAME_COUNT];

    public FlamingSpearProjectileModel(ModelPart root) {
        super();
        this.root = root;
        this.spear = root.getChild("spear");
        for (int i = 0; i < FLAME_COUNT; i++) this.flames[i] = root.getChild("flame_" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // ── static spear body: shaft, grips, collar, blade, horns, barbs ──
        CubeListBuilder spearCubes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 48.0F, 3.0F, NO_DEFORM) // shaft
                .texOffs(209, 52).addBox(-2.0F, 8.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band0
                .texOffs(209, 52).addBox(-2.0F, 20.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band1
                .texOffs(209, 52).addBox(-2.0F, 32.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band2
                .texOffs(209, 52).addBox(-2.0F, 44.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band3
                .texOffs(226, 52).addBox(-2.0F, 48.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // butt
                .texOffs(243, 52).addBox(-1.0F, 51.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // spike0
                .texOffs(44, 82).addBox(-0.5F, 56.0F, -0.5F, 1.0F, 3.0F, 1.0F, NO_DEFORM) // spike1
                .texOffs(148, 52).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F, NO_DEFORM) // ringB
                .texOffs(131, 52).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 5.0F, 4.0F, NO_DEFORM) // socket
                .texOffs(173, 52).addBox(-3.0F, -9.0F, -3.0F, 6.0F, 2.0F, 6.0F, NO_DEFORM) // ringA
                .texOffs(38, 74).addBox(-3.5F, -12.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade0
                .texOffs(57, 74).addBox(-4.5F, -15.0F, -1.0F, 9.0F, 3.0F, 2.0F, NO_DEFORM) // blade1
                .texOffs(80, 74).addBox(-5.5F, -18.0F, -1.0F, 11.0F, 3.0F, 2.0F, NO_DEFORM) // blade2
                .texOffs(107, 74).addBox(-5.5F, -21.0F, -1.0F, 11.0F, 3.0F, 2.0F, NO_DEFORM) // blade3
                .texOffs(134, 74).addBox(-5.0F, -24.0F, -1.0F, 10.0F, 3.0F, 2.0F, NO_DEFORM) // blade4
                .texOffs(159, 74).addBox(-4.5F, -27.0F, -1.0F, 9.0F, 3.0F, 2.0F, NO_DEFORM) // blade5
                .texOffs(182, 74).addBox(-4.0F, -30.0F, -1.0F, 8.0F, 3.0F, 2.0F, NO_DEFORM) // blade6
                .texOffs(203, 74).addBox(-3.5F, -33.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade7
                .texOffs(222, 74).addBox(-3.0F, -36.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade8
                .texOffs(239, 74).addBox(-2.5F, -39.0F, -1.0F, 5.0F, 3.0F, 2.0F, NO_DEFORM) // blade9
                .texOffs(0, 82).addBox(-2.0F, -42.0F, -1.0F, 4.0F, 3.0F, 2.0F, NO_DEFORM) // blade10
                .texOffs(49, 82).addBox(-1.5F, -44.0F, -1.0F, 3.0F, 2.0F, 2.0F, NO_DEFORM) // blade11
                .texOffs(89, 82).addBox(-1.0F, -46.0F, -0.5F, 2.0F, 2.0F, 1.0F, NO_DEFORM) // blade12
                .texOffs(96, 82).addBox(-0.5F, -48.0F, -0.5F, 1.0F, 2.0F, 1.0F, NO_DEFORM) // blade13
                .texOffs(233, 0).addBox(-1.5F, -33.0F, -2.0F, 3.0F, 24.0F, 4.0F, NO_DEFORM) // spine0
                .texOffs(120, 52).addBox(-1.0F, -41.0F, -1.5F, 2.0F, 8.0F, 3.0F, NO_DEFORM) // spine1
                .texOffs(13, 82).addBox(3.0F, -8.0F, -1.0F, 4.0F, 3.0F, 2.0F, NO_DEFORM) // hornR0
                .texOffs(13, 82).addBox(-7.0F, -8.0F, -1.0F, 4.0F, 3.0F, 2.0F, NO_DEFORM) // hornL0
                .texOffs(198, 52).addBox(7.0F, -11.0F, -1.0F, 3.0F, 6.0F, 2.0F, NO_DEFORM) // hornR1
                .texOffs(198, 52).addBox(-10.0F, -11.0F, -1.0F, 3.0F, 6.0F, 2.0F, NO_DEFORM) // hornL1
                .texOffs(0, 74).addBox(8.0F, -16.0F, -1.0F, 3.0F, 5.0F, 2.0F, NO_DEFORM) // hornR2
                .texOffs(0, 74).addBox(-11.0F, -16.0F, -1.0F, 3.0F, 5.0F, 2.0F, NO_DEFORM) // hornL2
                .texOffs(20, 74).addBox(8.0F, -20.0F, -1.0F, 2.0F, 4.0F, 2.0F, NO_DEFORM) // hornR3
                .texOffs(20, 74).addBox(-10.0F, -20.0F, -1.0F, 2.0F, 4.0F, 2.0F, NO_DEFORM) // hornL3
                .texOffs(26, 82).addBox(7.0F, -23.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // hornR4
                .texOffs(26, 82).addBox(-9.0F, -23.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // hornL4
                .texOffs(35, 82).addBox(6.0F, -26.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // hornR5
                .texOffs(35, 82).addBox(-8.0F, -26.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // hornL5
                .texOffs(60, 82).addBox(3.0F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F, NO_DEFORM) // barbR0
                .texOffs(60, 82).addBox(-6.0F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F, NO_DEFORM) // barbL0
                .texOffs(11, 74).addBox(5.0F, 1.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // barbR1
                .texOffs(11, 74).addBox(-7.0F, 1.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // barbL1
                .texOffs(29, 74).addBox(4.0F, 6.0F, -1.0F, 2.0F, 4.0F, 2.0F, NO_DEFORM) // barbR2
                .texOffs(29, 74).addBox(-6.0F, 6.0F, -1.0F, 2.0F, 4.0F, 2.0F, NO_DEFORM); // barbL2
        partdefinition.addOrReplaceChild("spear", spearCubes, PartPose.offset(0.0F, 20.0F, 0.0F));

        // flame 0: streams backwards
        partdefinition.addOrReplaceChild("flame_0", CubeListBuilder.create()
                .texOffs(67, 0).addBox(-8.0F, 0.0F, -0.5F, 16.0F, 34.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        // flame 1: streams backwards
        partdefinition.addOrReplaceChild("flame_1", CubeListBuilder.create()
                .texOffs(102, 0).addBox(-8.0F, 0.0F, -0.47F, 16.0F, 34.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        // flame 2: streams backwards
        partdefinition.addOrReplaceChild("flame_2", CubeListBuilder.create()
                .texOffs(179, 0).addBox(-6.0F, 0.0F, -0.44F, 12.0F, 28.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        // flame 3: streams backwards
        partdefinition.addOrReplaceChild("flame_3", CubeListBuilder.create()
                .texOffs(206, 0).addBox(-6.0F, 0.0F, -0.41F, 12.0F, 28.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        // flame 4: streams backwards
        partdefinition.addOrReplaceChild("flame_4", CubeListBuilder.create()
                .texOffs(30, 52).addBox(-3.5F, 0.0F, -0.38F, 7.0F, 18.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 34.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        // flame 5: streams backwards
        partdefinition.addOrReplaceChild("flame_5", CubeListBuilder.create()
                .texOffs(47, 52).addBox(-3.5F, 0.0F, -0.35F, 7.0F, 18.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 34.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        // flame 6: streams backwards
        partdefinition.addOrReplaceChild("flame_6", CubeListBuilder.create()
                .texOffs(64, 52).addBox(-3.0F, 0.0F, -0.32F, 6.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 48.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        // flame 7: streams backwards
        partdefinition.addOrReplaceChild("flame_7", CubeListBuilder.create()
                .texOffs(79, 52).addBox(-3.0F, 0.0F, -0.29F, 6.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 48.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        // flame 8: streams backwards
        partdefinition.addOrReplaceChild("flame_8", CubeListBuilder.create()
                .texOffs(13, 0).addBox(-6.0F, 0.0F, -0.26F, 12.0F, 44.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 67.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        // flame 9: streams backwards
        partdefinition.addOrReplaceChild("flame_9", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-6.0F, 0.0F, -0.23F, 12.0F, 44.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 67.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        // flame 10: streams backwards
        partdefinition.addOrReplaceChild("flame_10", CubeListBuilder.create()
                .texOffs(137, 0).addBox(-4.5F, 0.0F, -0.2F, 9.0F, 34.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 69.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        // flame 11: streams backwards
        partdefinition.addOrReplaceChild("flame_11", CubeListBuilder.create()
                .texOffs(158, 0).addBox(-4.5F, 0.0F, -0.17F, 9.0F, 34.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 69.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        // flame 12: licks forward
        partdefinition.addOrReplaceChild("flame_12", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-3.0F, -20.0F, -0.14F, 6.0F, 20.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        // flame 13: licks forward
        partdefinition.addOrReplaceChild("flame_13", CubeListBuilder.create()
                .texOffs(15, 52).addBox(-3.0F, -20.0F, -0.11F, 6.0F, 20.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        // flame 14: licks forward
        partdefinition.addOrReplaceChild("flame_14", CubeListBuilder.create()
                .texOffs(94, 52).addBox(-2.5F, -15.0F, -0.08F, 5.0F, 15.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        // flame 15: licks forward
        partdefinition.addOrReplaceChild("flame_15", CubeListBuilder.create()
                .texOffs(107, 52).addBox(-2.5F, -15.0F, -0.05F, 5.0F, 15.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, -0.785F, 0.0F));
        return LayerDefinition.create(meshdefinition, 256, 128);
    }

    public void setFireVisible(boolean visible) {
        for (ModelPart p : flames) p.visible = visible;
    }

    @Override
    public void setupAnim(FlamingSpearProjectileEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // flame tongues: sway, stretch and breathe out of phase with each other
        for (int i = 0; i < FLAME_COUNT; i++) {
            ModelPart flame = flames[i];
            float phase = i * 1.713F;
            float wave = ageInTicks * 0.55F + phase;
            flame.xRot = Mth.sin(wave) * 0.10F;
            flame.zRot = Mth.cos(wave * 0.83F + 1.1F) * 0.10F;
            float stretch = 1.0F + Mth.sin(ageInTicks * 0.95F + phase * 1.9F) * 0.14F
                    + Mth.sin(ageInTicks * 2.3F + phase) * 0.05F;
            float breathe = 1.0F + Mth.sin(ageInTicks * 1.3F + phase * 0.7F) * 0.08F;
            flame.yScale = stretch;
            flame.xScale = breathe;
            flame.zScale = breathe;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
