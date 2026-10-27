package de.jakob.lotm.entity.client.projectiles.spear_of_light;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.SpearOfLightProjectileEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Spear of Light: ivory-and-gold shaft, faceted crystal blade, swept angel wings,
 * two counter-rotating halos, trailing beams of light, twinkling sparkles and drifting motes.
 * Model space: the tip points towards -Y, light streams towards +Y (same as the old model).
 * Texture atlas: 256x128 (textures/entity/spear_of_light/spear_of_light.png).
 */
public class SpearOfLightProjectileModel extends EntityModel<SpearOfLightProjectileEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "spear_of_light"), "main");

    private static final CubeDeformation NO_DEFORM = new CubeDeformation(0.0F);
    private static final int RAY_COUNT = 10;
    private static final int SPARKLE_COUNT = 8;
    private static final int MOTE_COUNT = 16;
    private static final float MOTE_START = -6.0F;
    private static final float MOTE_RANGE = 110.0F;

    private final ModelPart root;
    private final ModelPart[] halos = new ModelPart[2];
    private final ModelPart[] rays = new ModelPart[RAY_COUNT];
    private final ModelPart[] sparkles = new ModelPart[SPARKLE_COUNT];
    private final ModelPart[] motes = new ModelPart[MOTE_COUNT];
    private final float[][] sparkleBase = new float[SPARKLE_COUNT][3];

    public SpearOfLightProjectileModel(ModelPart root) {
        super();
        this.root = root;
        for (int i = 0; i < 2; i++) halos[i] = root.getChild("halo_" + i);
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
                .texOffs(0, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 52.0F, 3.0F, NO_DEFORM) // shaft
                .texOffs(93, 56).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band0
                .texOffs(93, 56).addBox(-2.0F, 18.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band1
                .texOffs(93, 56).addBox(-2.0F, 30.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band2
                .texOffs(93, 56).addBox(-2.0F, 42.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band3
                .texOffs(110, 56).addBox(-2.0F, 52.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // butt
                .texOffs(17, 56).addBox(-1.0F, 55.0F, -1.0F, 2.0F, 6.0F, 2.0F, NO_DEFORM) // tail0
                .texOffs(140, 56).addBox(-0.5F, 61.0F, -0.5F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // tail1
                .texOffs(26, 56).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F, NO_DEFORM) // ringB
                .texOffs(0, 56).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 5.0F, 4.0F, NO_DEFORM) // socket
                .texOffs(51, 56).addBox(-3.0F, -9.0F, -3.0F, 6.0F, 2.0F, 6.0F, NO_DEFORM) // ringA
                .texOffs(76, 56).addBox(-1.5F, -6.0F, -2.5F, 3.0F, 3.0F, 5.0F, NO_DEFORM) // gem
                .texOffs(145, 56).addBox(-3.0F, -12.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade0
                .texOffs(162, 56).addBox(-4.0F, -15.0F, -1.0F, 8.0F, 3.0F, 2.0F, NO_DEFORM) // blade1
                .texOffs(183, 56).addBox(-4.0F, -18.0F, -1.0F, 8.0F, 3.0F, 2.0F, NO_DEFORM) // blade2
                .texOffs(204, 56).addBox(-4.0F, -21.0F, -1.0F, 8.0F, 3.0F, 2.0F, NO_DEFORM) // blade3
                .texOffs(225, 56).addBox(-3.5F, -24.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade4
                .texOffs(0, 66).addBox(-3.5F, -27.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade5
                .texOffs(19, 66).addBox(-3.0F, -30.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade6
                .texOffs(36, 66).addBox(-3.0F, -33.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade7
                .texOffs(53, 66).addBox(-2.5F, -36.0F, -1.0F, 5.0F, 3.0F, 2.0F, NO_DEFORM) // blade8
                .texOffs(68, 66).addBox(-2.0F, -39.0F, -1.0F, 4.0F, 3.0F, 2.0F, NO_DEFORM) // blade9
                .texOffs(81, 66).addBox(-1.5F, -42.0F, -1.0F, 3.0F, 3.0F, 2.0F, NO_DEFORM) // blade10
                .texOffs(92, 66).addBox(-1.0F, -45.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // blade11
                .texOffs(128, 66).addBox(-0.5F, -48.0F, -0.5F, 1.0F, 3.0F, 1.0F, NO_DEFORM) // blade12
                .texOffs(131, 0).addBox(-1.0F, -34.0F, -1.5F, 2.0F, 25.0F, 3.0F, NO_DEFORM) // spine0
                .texOffs(235, 0).addBox(-0.5F, -42.0F, -1.0F, 1.0F, 8.0F, 2.0F, NO_DEFORM) // spine1
                .texOffs(217, 0).addBox(3.0F, -8.0F, -0.5F, 3.0F, 11.0F, 1.0F, NO_DEFORM) // wingR0
                .texOffs(217, 0).addBox(-6.0F, -8.0F, -0.5F, 3.0F, 11.0F, 1.0F, NO_DEFORM) // wingL0
                .texOffs(199, 0).addBox(5.0F, -6.0F, -0.38F, 3.0F, 13.0F, 1.0F, NO_DEFORM) // wingR1
                .texOffs(199, 0).addBox(-8.0F, -6.0F, -0.38F, 3.0F, 13.0F, 1.0F, NO_DEFORM) // wingL1
                .texOffs(190, 0).addBox(7.0F, -4.0F, -0.26F, 3.0F, 15.0F, 1.0F, NO_DEFORM) // wingR2
                .texOffs(190, 0).addBox(-10.0F, -4.0F, -0.26F, 3.0F, 15.0F, 1.0F, NO_DEFORM) // wingL2
                .texOffs(208, 0).addBox(9.0F, -1.0F, -0.14F, 3.0F, 13.0F, 1.0F, NO_DEFORM) // wingR3
                .texOffs(208, 0).addBox(-12.0F, -1.0F, -0.14F, 3.0F, 13.0F, 1.0F, NO_DEFORM) // wingL3
                .texOffs(226, 0).addBox(11.0F, 2.0F, -0.02F, 3.0F, 10.0F, 1.0F, NO_DEFORM) // wingR4
                .texOffs(226, 0).addBox(-14.0F, 2.0F, -0.02F, 3.0F, 10.0F, 1.0F, NO_DEFORM) // wingL4
                .texOffs(101, 66).addBox(4.0F, -11.0F, 0.1F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covR0
                .texOffs(101, 66).addBox(-7.0F, -11.0F, 0.1F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covL0
                .texOffs(110, 66).addBox(6.0F, -9.0F, 0.2F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covR1
                .texOffs(110, 66).addBox(-9.0F, -9.0F, 0.2F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covL1
                .texOffs(119, 66).addBox(8.0F, -7.0F, 0.3F, 3.0F, 4.0F, 1.0F, NO_DEFORM) // covR2
                .texOffs(119, 66).addBox(-11.0F, -7.0F, 0.3F, 3.0F, 4.0F, 1.0F, NO_DEFORM); // covL2
        partdefinition.addOrReplaceChild("spear", bodyCubes, PartPose.offset(0.0F, 18.0F, 0.0F));

        // ── halos (octagonal rings that rotate in setupAnim) ──
        PartDefinition halo_0 = partdefinition.addOrReplaceChild("halo_0", CubeListBuilder.create(), PartPose.offset(0.0F, -4.0F, 0.0F));
        halo_0.addOrReplaceChild("seg_0", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 0.0F, 0.0F));
        halo_0.addOrReplaceChild("seg_1", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 0.785F, 0.0F));
        halo_0.addOrReplaceChild("seg_2", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.571F, 0.0F));
        halo_0.addOrReplaceChild("seg_3", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 2.356F, 0.0F));
        halo_0.addOrReplaceChild("seg_4", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 3.142F, 0.0F));
        halo_0.addOrReplaceChild("seg_5", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 3.927F, 0.0F));
        halo_0.addOrReplaceChild("seg_6", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 4.712F, 0.0F));
        halo_0.addOrReplaceChild("seg_7", CubeListBuilder.create().texOffs(133, 66).addBox(-3.5F, -1.0F, 7.0F, 7.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 5.498F, 0.0F));
        PartDefinition halo_1 = partdefinition.addOrReplaceChild("halo_1", CubeListBuilder.create(), PartPose.offset(0.0F, -19.0F, 0.0F));
        halo_1.addOrReplaceChild("seg_0", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 0.0F, 0.0F));
        halo_1.addOrReplaceChild("seg_1", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 0.785F, 0.0F));
        halo_1.addOrReplaceChild("seg_2", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.571F, 0.0F));
        halo_1.addOrReplaceChild("seg_3", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 2.356F, 0.0F));
        halo_1.addOrReplaceChild("seg_4", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 3.142F, 0.0F));
        halo_1.addOrReplaceChild("seg_5", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 3.927F, 0.0F));
        halo_1.addOrReplaceChild("seg_6", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 4.712F, 0.0F));
        halo_1.addOrReplaceChild("seg_7", CubeListBuilder.create().texOffs(170, 66).addBox(-2.5F, -0.5F, 4.5F, 5.0F, 1.0F, 2.0F, NO_DEFORM), PartPose.rotation(0.0F, 5.498F, 0.0F));

        // ── beams of light ──
        partdefinition.addOrReplaceChild("ray_0", CubeListBuilder.create()
                .texOffs(13, 0).addBox(-5.0F, 0.0F, -0.47F, 10.0F, 48.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 68.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("ray_1", CubeListBuilder.create()
                .texOffs(36, 0).addBox(-5.0F, 0.0F, -0.44F, 10.0F, 48.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 68.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("ray_2", CubeListBuilder.create()
                .texOffs(59, 0).addBox(-3.5F, 0.0F, -0.41F, 7.0F, 36.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 70.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        partdefinition.addOrReplaceChild("ray_3", CubeListBuilder.create()
                .texOffs(76, 0).addBox(-3.5F, 0.0F, -0.38F, 7.0F, 36.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 70.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        partdefinition.addOrReplaceChild("ray_4", CubeListBuilder.create()
                .texOffs(93, 0).addBox(-4.0F, 0.0F, -0.35F, 8.0F, 34.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("ray_5", CubeListBuilder.create()
                .texOffs(112, 0).addBox(-4.0F, 0.0F, -0.32F, 8.0F, 34.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("ray_6", CubeListBuilder.create()
                .texOffs(142, 0).addBox(-2.5F, -22.0F, -0.29F, 5.0F, 22.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("ray_7", CubeListBuilder.create()
                .texOffs(155, 0).addBox(-2.5F, -22.0F, -0.26F, 5.0F, 22.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("ray_8", CubeListBuilder.create()
                .texOffs(168, 0).addBox(-2.0F, -16.0F, -0.23F, 4.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        partdefinition.addOrReplaceChild("ray_9", CubeListBuilder.create()
                .texOffs(179, 0).addBox(-2.0F, -16.0F, -0.2F, 4.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        // ── sparkles (two crossed star planes each) ──
        PartDefinition sparkle0 = partdefinition.addOrReplaceChild("sparkle_0", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(7.0F, -12.0F, 3.0F));
        sparkle0.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle1 = partdefinition.addOrReplaceChild("sparkle_1", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(-7.0F, -8.0F, -3.0F));
        sparkle1.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle2 = partdefinition.addOrReplaceChild("sparkle_2", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(5.0F, -26.0F, -4.0F));
        sparkle2.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle3 = partdefinition.addOrReplaceChild("sparkle_3", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(-5.0F, -22.0F, 4.0F));
        sparkle3.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle4 = partdefinition.addOrReplaceChild("sparkle_4", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(10.0F, 12.0F, 3.0F));
        sparkle4.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle5 = partdefinition.addOrReplaceChild("sparkle_5", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(-9.0F, 20.0F, -3.0F));
        sparkle5.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle6 = partdefinition.addOrReplaceChild("sparkle_6", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(8.0F, 32.0F, 4.0F));
        sparkle6.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));
        PartDefinition sparkle7 = partdefinition.addOrReplaceChild("sparkle_7", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offset(-6.0F, 42.0F, -4.0F));
        sparkle7.addOrReplaceChild("cross", CubeListBuilder.create().texOffs(127, 56).addBox(-2.5F, -2.5F, -0.5F, 5.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.rotation(0.0F, 1.5708F, 0.0F));

        // ── drifting motes ──
        partdefinition.addOrReplaceChild("mote_0", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_1", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_2", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_3", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_4", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_5", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_6", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_7", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_8", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_9", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_10", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_11", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_12", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_13", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_14", CubeListBuilder.create().texOffs(152, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_15", CubeListBuilder.create().texOffs(161, 66).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);

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
    public void setupAnim(SpearOfLightProjectileEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // halos turn in opposite directions and gently breathe
        halos[0].yRot = ageInTicks * 0.09F;
        halos[1].yRot = -ageInTicks * 0.15F;
        for (int i = 0; i < 2; i++) {
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
