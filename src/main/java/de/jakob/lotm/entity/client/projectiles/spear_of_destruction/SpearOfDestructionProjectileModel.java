package de.jakob.lotm.entity.client.projectiles.spear_of_destruction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.SpearOfDestructionProjectileEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Spear of Destruction: an upgraded flaming spear in black, purple and red. Obsidian shaft with
 * red-hot cracks, spiked bands, a jagged blade with a molten fuller, sweeping crown prongs,
 * two counter-rotating shard rings, 24 animated flame tongues and drifting embers.
 * Model space: the tip points towards -Y, flames stream towards +Y.
 * Texture atlas: 256x160 (textures/entity/spear_of_destruction/spear_of_destruction.png).
 */
public class SpearOfDestructionProjectileModel extends EntityModel<SpearOfDestructionProjectileEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "spear_of_destruction"), "main");

    private static final CubeDeformation NO_DEFORM = new CubeDeformation(0.0F);
    private static final int FLAME_COUNT = 24;
    private static final int EMBER_COUNT = 20;
    private static final float EMBER_START = 0.0F;
    private static final float EMBER_RANGE = 140.0F;

    private final ModelPart root;
    private final ModelPart[] orbits = new ModelPart[2];
    private final float[] orbitBaseY = new float[2];
    private final ModelPart[] flames = new ModelPart[FLAME_COUNT];
    private final ModelPart[] embers = new ModelPart[EMBER_COUNT];

    public SpearOfDestructionProjectileModel(ModelPart root) {
        super();
        this.root = root;
        for (int i = 0; i < 2; i++) {
            orbits[i] = root.getChild("orbit_" + i);
            orbitBaseY[i] = orbits[i].y;
        }
        for (int i = 0; i < FLAME_COUNT; i++) flames[i] = root.getChild("flame_" + i);
        for (int i = 0; i < EMBER_COUNT; i++) embers[i] = root.getChild("ember_" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // ── solid body: obsidian shaft, spiked bands, head, blade, prongs, barbs ──
        CubeListBuilder bodyCubes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 50.0F, 4.0F, NO_DEFORM) // shaft
                .texOffs(222, 89).addBox(-3.0F, 8.0F, -3.0F, 6.0F, 3.0F, 6.0F, NO_DEFORM) // band0
                .texOffs(164, 119).addBox(-3.0F, 11.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs0_-1-1
                .texOffs(164, 119).addBox(-3.0F, 11.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs0_-11
                .texOffs(164, 119).addBox(2.0F, 11.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs0_1-1
                .texOffs(164, 119).addBox(2.0F, 11.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs0_11
                .texOffs(222, 89).addBox(-3.0F, 20.0F, -3.0F, 6.0F, 3.0F, 6.0F, NO_DEFORM) // band1
                .texOffs(164, 119).addBox(-3.0F, 23.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs1_-1-1
                .texOffs(164, 119).addBox(-3.0F, 23.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs1_-11
                .texOffs(164, 119).addBox(2.0F, 23.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs1_1-1
                .texOffs(164, 119).addBox(2.0F, 23.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs1_11
                .texOffs(222, 89).addBox(-3.0F, 32.0F, -3.0F, 6.0F, 3.0F, 6.0F, NO_DEFORM) // band2
                .texOffs(164, 119).addBox(-3.0F, 35.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs2_-1-1
                .texOffs(164, 119).addBox(-3.0F, 35.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs2_-11
                .texOffs(164, 119).addBox(2.0F, 35.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs2_1-1
                .texOffs(164, 119).addBox(2.0F, 35.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs2_11
                .texOffs(222, 89).addBox(-3.0F, 44.0F, -3.0F, 6.0F, 3.0F, 6.0F, NO_DEFORM) // band3
                .texOffs(164, 119).addBox(-3.0F, 47.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs3_-1-1
                .texOffs(164, 119).addBox(-3.0F, 47.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs3_-11
                .texOffs(164, 119).addBox(2.0F, 47.0F, -3.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs3_1-1
                .texOffs(164, 119).addBox(2.0F, 47.0F, 2.0F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // bs3_11
                .texOffs(0, 109).addBox(-3.0F, 50.0F, -3.0F, 6.0F, 3.0F, 6.0F, NO_DEFORM) // butt
                .texOffs(138, 89).addBox(-1.0F, 53.0F, -1.0F, 2.0F, 8.0F, 2.0F, NO_DEFORM) // tail0
                .texOffs(145, 109).addBox(-0.5F, 61.0F, -0.5F, 1.0F, 5.0F, 1.0F, NO_DEFORM) // tail1
                .texOffs(147, 89).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 2.0F, 8.0F, NO_DEFORM) // ringB
                .texOffs(90, 89).addBox(-3.0F, -8.0F, -3.0F, 6.0F, 6.0F, 6.0F, NO_DEFORM) // socket
                .texOffs(180, 89).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 2.0F, 8.0F, NO_DEFORM) // ringA
                .texOffs(115, 89).addBox(-2.0F, -8.0F, -3.5F, 4.0F, 4.0F, 7.0F, NO_DEFORM) // core
                .texOffs(150, 109).addBox(-4.0F, -13.0F, -1.5F, 8.0F, 3.0F, 3.0F, NO_DEFORM) // blade0
                .texOffs(173, 109).addBox(-5.0F, -16.0F, -1.5F, 10.0F, 3.0F, 3.0F, NO_DEFORM) // blade1
                .texOffs(200, 109).addBox(-6.0F, -19.0F, -1.5F, 12.0F, 3.0F, 3.0F, NO_DEFORM) // blade2
                .texOffs(0, 119).addBox(-6.0F, -22.0F, -1.5F, 12.0F, 3.0F, 3.0F, NO_DEFORM) // blade3
                .texOffs(31, 119).addBox(-5.5F, -25.0F, -1.5F, 11.0F, 3.0F, 3.0F, NO_DEFORM) // blade4
                .texOffs(60, 119).addBox(-5.0F, -28.0F, -1.5F, 10.0F, 3.0F, 3.0F, NO_DEFORM) // blade5
                .texOffs(87, 119).addBox(-4.5F, -31.0F, -1.5F, 9.0F, 3.0F, 3.0F, NO_DEFORM) // blade6
                .texOffs(112, 119).addBox(-4.0F, -34.0F, -1.5F, 8.0F, 3.0F, 3.0F, NO_DEFORM) // blade7
                .texOffs(169, 119).addBox(-3.5F, -37.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade8
                .texOffs(188, 119).addBox(-3.0F, -40.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade9
                .texOffs(205, 119).addBox(-2.5F, -43.0F, -1.0F, 5.0F, 3.0F, 2.0F, NO_DEFORM) // blade10
                .texOffs(220, 119).addBox(-2.0F, -46.0F, -1.0F, 4.0F, 3.0F, 2.0F, NO_DEFORM) // blade11
                .texOffs(233, 119).addBox(-1.5F, -49.0F, -1.0F, 3.0F, 3.0F, 2.0F, NO_DEFORM) // blade12
                .texOffs(244, 119).addBox(-1.0F, -52.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // blade13
                .texOffs(38, 126).addBox(-0.5F, -54.0F, -0.5F, 1.0F, 2.0F, 1.0F, NO_DEFORM) // blade14
                .texOffs(203, 0).addBox(-1.5F, -40.0F, -2.5F, 3.0F, 30.0F, 5.0F, NO_DEFORM) // spine0
                .texOffs(77, 89).addBox(-1.0F, -50.0F, -2.0F, 2.0F, 10.0F, 4.0F, NO_DEFORM) // spine1
                .texOffs(135, 119).addBox(4.0F, -10.0F, -1.5F, 4.0F, 3.0F, 3.0F, NO_DEFORM) // hornR0
                .texOffs(135, 119).addBox(-8.0F, -10.0F, -1.5F, 4.0F, 3.0F, 3.0F, NO_DEFORM) // hornL0
                .texOffs(49, 109).addBox(8.0F, -14.0F, -1.5F, 3.0F, 5.0F, 3.0F, NO_DEFORM) // hornR1
                .texOffs(49, 109).addBox(-11.0F, -14.0F, -1.5F, 3.0F, 5.0F, 3.0F, NO_DEFORM) // hornL1
                .texOffs(25, 109).addBox(10.0F, -20.0F, -1.5F, 3.0F, 6.0F, 3.0F, NO_DEFORM) // hornR2
                .texOffs(25, 109).addBox(-13.0F, -20.0F, -1.5F, 3.0F, 6.0F, 3.0F, NO_DEFORM) // hornL2
                .texOffs(38, 109).addBox(10.0F, -27.0F, -1.5F, 2.0F, 6.0F, 3.0F, NO_DEFORM) // hornR3
                .texOffs(38, 109).addBox(-12.0F, -27.0F, -1.5F, 2.0F, 6.0F, 3.0F, NO_DEFORM) // hornL3
                .texOffs(62, 109).addBox(9.0F, -33.0F, -1.5F, 2.0F, 5.0F, 3.0F, NO_DEFORM) // hornR4
                .texOffs(62, 109).addBox(-11.0F, -33.0F, -1.5F, 2.0F, 5.0F, 3.0F, NO_DEFORM) // hornL4
                .texOffs(91, 109).addBox(8.0F, -38.0F, -1.5F, 2.0F, 4.0F, 3.0F, NO_DEFORM) // hornR5
                .texOffs(91, 109).addBox(-10.0F, -38.0F, -1.5F, 2.0F, 4.0F, 3.0F, NO_DEFORM) // hornL5
                .texOffs(150, 119).addBox(7.0F, -41.0F, -1.5F, 1.0F, 3.0F, 3.0F, NO_DEFORM) // hornR6
                .texOffs(150, 119).addBox(-8.0F, -41.0F, -1.5F, 1.0F, 3.0F, 3.0F, NO_DEFORM) // hornL6
                .texOffs(0, 126).addBox(4.0F, -2.0F, -1.0F, 3.0F, 3.0F, 2.0F, NO_DEFORM) // barbR0
                .texOffs(0, 126).addBox(-7.0F, -2.0F, -1.0F, 3.0F, 3.0F, 2.0F, NO_DEFORM) // barbL0
                .texOffs(73, 109).addBox(7.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, NO_DEFORM) // barbR1
                .texOffs(73, 109).addBox(-9.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, NO_DEFORM) // barbL1
                .texOffs(82, 109).addBox(8.0F, 6.0F, -1.0F, 2.0F, 6.0F, 2.0F, NO_DEFORM) // barbR2
                .texOffs(82, 109).addBox(-10.0F, 6.0F, -1.0F, 2.0F, 6.0F, 2.0F, NO_DEFORM) // barbL2
                .texOffs(102, 109).addBox(8.0F, 12.0F, -1.0F, 1.0F, 5.0F, 2.0F, NO_DEFORM) // barbR3
                .texOffs(102, 109).addBox(-9.0F, 12.0F, -1.0F, 1.0F, 5.0F, 2.0F, NO_DEFORM) // barbL3
                .texOffs(109, 109).addBox(7.0F, -24.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // toothR0
                .texOffs(109, 109).addBox(-9.0F, -24.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // toothL0
                .texOffs(118, 109).addBox(6.0F, -30.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // toothR1
                .texOffs(118, 109).addBox(-8.0F, -30.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // toothL1
                .texOffs(127, 109).addBox(5.0F, -36.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // toothR2
                .texOffs(127, 109).addBox(-7.0F, -36.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // toothL2
                .texOffs(136, 109).addBox(3.0F, -42.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM) // toothR3
                .texOffs(136, 109).addBox(-5.0F, -42.0F, -1.0F, 2.0F, 5.0F, 2.0F, NO_DEFORM); // toothL3
        partdefinition.addOrReplaceChild("spear", bodyCubes, PartPose.offset(0.0F, 22.0F, 0.0F));

        // ── orbiting obsidian shards ──
        PartDefinition orbit_0 = partdefinition.addOrReplaceChild("orbit_0", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        orbit_0.addOrReplaceChild("shard_0", CubeListBuilder.create().texOffs(213, 89).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, NO_DEFORM), PartPose.offsetAndRotation(11.0F, 3.0F, 0.0F, 0.0F, -0.0F, 0.0F));
        orbit_0.addOrReplaceChild("shard_1", CubeListBuilder.create().texOffs(213, 89).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, NO_DEFORM), PartPose.offsetAndRotation(5.5F, -3.0F, 9.526F, 0.0F, -1.047F, 0.0F));
        orbit_0.addOrReplaceChild("shard_2", CubeListBuilder.create().texOffs(213, 89).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, NO_DEFORM), PartPose.offsetAndRotation(-5.5F, 3.0F, 9.526F, 0.0F, -2.094F, 0.0F));
        orbit_0.addOrReplaceChild("shard_3", CubeListBuilder.create().texOffs(213, 89).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, NO_DEFORM), PartPose.offsetAndRotation(-11.0F, -3.0F, 0.0F, 0.0F, -3.142F, 0.0F));
        orbit_0.addOrReplaceChild("shard_4", CubeListBuilder.create().texOffs(213, 89).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, NO_DEFORM), PartPose.offsetAndRotation(-5.5F, 3.0F, -9.526F, 0.0F, -4.189F, 0.0F));
        orbit_0.addOrReplaceChild("shard_5", CubeListBuilder.create().texOffs(213, 89).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, NO_DEFORM), PartPose.offsetAndRotation(5.5F, -3.0F, -9.526F, 0.0F, -5.236F, 0.0F));
        PartDefinition orbit_1 = partdefinition.addOrReplaceChild("orbit_1", CubeListBuilder.create(), PartPose.offset(0.0F, -16.0F, 0.0F));
        orbit_1.addOrReplaceChild("shard_0", CubeListBuilder.create().texOffs(159, 119).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offsetAndRotation(7.0F, 1.8F, 0.0F, 0.0F, -0.0F, 0.0F));
        orbit_1.addOrReplaceChild("shard_1", CubeListBuilder.create().texOffs(159, 119).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offsetAndRotation(0.0F, -1.8F, 7.0F, 0.0F, -1.571F, 0.0F));
        orbit_1.addOrReplaceChild("shard_2", CubeListBuilder.create().texOffs(159, 119).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offsetAndRotation(-7.0F, 1.8F, 0.0F, 0.0F, -3.142F, 0.0F));
        orbit_1.addOrReplaceChild("shard_3", CubeListBuilder.create().texOffs(159, 119).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, NO_DEFORM), PartPose.offsetAndRotation(-0.0F, -1.8F, -7.0F, 0.0F, -4.712F, 0.0F));

        // ── flame tongues ──
        partdefinition.addOrReplaceChild("flame_0", CubeListBuilder.create()
                .texOffs(79, 0).addBox(-9.0F, 0.0F, -0.5F, 18.0F, 40.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("flame_1", CubeListBuilder.create()
                .texOffs(118, 0).addBox(-9.0F, 0.0F, -0.475F, 18.0F, 40.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("flame_2", CubeListBuilder.create()
                .texOffs(220, 0).addBox(-7.0F, 0.0F, -0.45F, 14.0F, 32.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        partdefinition.addOrReplaceChild("flame_3", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-7.0F, 0.0F, -0.425F, 14.0F, 32.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        partdefinition.addOrReplaceChild("flame_4", CubeListBuilder.create()
                .texOffs(69, 55).addBox(-5.0F, 0.0F, -0.4F, 10.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.393F, 0.0F));

        partdefinition.addOrReplaceChild("flame_5", CubeListBuilder.create()
                .texOffs(92, 55).addBox(-5.0F, 0.0F, -0.375F, 10.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 1.178F, 0.0F));

        partdefinition.addOrReplaceChild("flame_6", CubeListBuilder.create()
                .texOffs(115, 55).addBox(-5.0F, 0.0F, -0.35F, 10.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 1.963F, 0.0F));

        partdefinition.addOrReplaceChild("flame_7", CubeListBuilder.create()
                .texOffs(138, 55).addBox(-5.0F, 0.0F, -0.325F, 10.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 2.749F, 0.0F));

        partdefinition.addOrReplaceChild("flame_8", CubeListBuilder.create()
                .texOffs(161, 55).addBox(-4.0F, 0.0F, -0.3F, 8.0F, 22.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 34.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("flame_9", CubeListBuilder.create()
                .texOffs(180, 55).addBox(-4.0F, 0.0F, -0.275F, 8.0F, 22.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 34.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("flame_10", CubeListBuilder.create()
                .texOffs(233, 55).addBox(-3.5F, 0.0F, -0.25F, 7.0F, 18.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 48.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        partdefinition.addOrReplaceChild("flame_11", CubeListBuilder.create()
                .texOffs(0, 89).addBox(-3.5F, 0.0F, -0.225F, 7.0F, 18.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 48.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        partdefinition.addOrReplaceChild("flame_12", CubeListBuilder.create()
                .texOffs(47, 89).addBox(-3.0F, 0.0F, -0.2F, 6.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 60.0F, 0.0F, 0.0F, 0.393F, 0.0F));

        partdefinition.addOrReplaceChild("flame_13", CubeListBuilder.create()
                .texOffs(62, 89).addBox(-3.0F, 0.0F, -0.175F, 6.0F, 16.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 60.0F, 0.0F, 0.0F, -0.393F, 0.0F));

        partdefinition.addOrReplaceChild("flame_14", CubeListBuilder.create()
                .texOffs(17, 0).addBox(-7.0F, 0.0F, -0.15F, 14.0F, 52.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 70.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("flame_15", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-7.0F, 0.0F, -0.125F, 14.0F, 52.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 70.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("flame_16", CubeListBuilder.create()
                .texOffs(157, 0).addBox(-5.0F, 0.0F, -0.1F, 10.0F, 40.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 72.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        partdefinition.addOrReplaceChild("flame_17", CubeListBuilder.create()
                .texOffs(180, 0).addBox(-5.0F, 0.0F, -0.075F, 10.0F, 40.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 72.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        partdefinition.addOrReplaceChild("flame_18", CubeListBuilder.create()
                .texOffs(31, 55).addBox(-4.0F, 0.0F, -0.05F, 8.0F, 30.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 74.0F, 0.0F, 0.0F, 0.393F, 0.0F));

        partdefinition.addOrReplaceChild("flame_19", CubeListBuilder.create()
                .texOffs(50, 55).addBox(-4.0F, 0.0F, -0.025F, 8.0F, 30.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 74.0F, 0.0F, 0.0F, -0.393F, 0.0F));

        partdefinition.addOrReplaceChild("flame_20", CubeListBuilder.create()
                .texOffs(199, 55).addBox(-3.5F, -22.0F, 0.0F, 7.0F, 22.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("flame_21", CubeListBuilder.create()
                .texOffs(216, 55).addBox(-3.5F, -22.0F, 0.025F, 7.0F, 22.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("flame_22", CubeListBuilder.create()
                .texOffs(17, 89).addBox(-3.0F, -18.0F, 0.05F, 6.0F, 18.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, 0.785F, 0.0F));

        partdefinition.addOrReplaceChild("flame_23", CubeListBuilder.create()
                .texOffs(32, 89).addBox(-3.0F, -18.0F, 0.075F, 6.0F, 18.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, -16.0F, 0.0F, 0.0F, -0.785F, 0.0F));

        // ── embers ──
        partdefinition.addOrReplaceChild("ember_0", CubeListBuilder.create().texOffs(11, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_1", CubeListBuilder.create().texOffs(20, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_2", CubeListBuilder.create().texOffs(29, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_3", CubeListBuilder.create().texOffs(11, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_4", CubeListBuilder.create().texOffs(20, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_5", CubeListBuilder.create().texOffs(29, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_6", CubeListBuilder.create().texOffs(11, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_7", CubeListBuilder.create().texOffs(20, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_8", CubeListBuilder.create().texOffs(29, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_9", CubeListBuilder.create().texOffs(11, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_10", CubeListBuilder.create().texOffs(20, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_11", CubeListBuilder.create().texOffs(29, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_12", CubeListBuilder.create().texOffs(11, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_13", CubeListBuilder.create().texOffs(20, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_14", CubeListBuilder.create().texOffs(29, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_15", CubeListBuilder.create().texOffs(11, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_16", CubeListBuilder.create().texOffs(20, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_17", CubeListBuilder.create().texOffs(29, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_18", CubeListBuilder.create().texOffs(11, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("ember_19", CubeListBuilder.create().texOffs(20, 126).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);

        return LayerDefinition.create(meshdefinition, 256, 160);
    }

    /** Hide / show the animated fire, shards and embers (used when the spear is petrified). */
    public void setFireVisible(boolean visible) {
        for (ModelPart p : orbits) p.visible = visible;
        for (ModelPart p : flames) p.visible = visible;
        for (ModelPart p : embers) p.visible = visible;
    }

    @Override
    public void setupAnim(SpearOfDestructionProjectileEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // shard rings: counter-rotate and bob
        orbits[0].yRot = ageInTicks * 0.13F;
        orbits[1].yRot = -ageInTicks * 0.22F;
        for (int i = 0; i < 2; i++) {
            orbits[i].y = orbitBaseY[i] + Mth.sin(ageInTicks * 0.2F + i * 2.0F) * 2.0F;
        }

        // flame tongues: sway, stretch and breathe out of phase (a little more violent than the flaming spear)
        for (int i = 0; i < FLAME_COUNT; i++) {
            ModelPart flame = flames[i];
            float phase = i * 1.713F;
            float wave = ageInTicks * 0.65F + phase;
            flame.xRot = Mth.sin(wave) * 0.12F;
            flame.zRot = Mth.cos(wave * 0.83F + 1.1F) * 0.12F;
            flame.yScale = 1.0F + Mth.sin(ageInTicks * 1.05F + phase * 1.9F) * 0.16F
                    + Mth.sin(ageInTicks * 2.6F + phase) * 0.06F;
            float breathe = 1.0F + Mth.sin(ageInTicks * 1.4F + phase * 0.7F) * 0.09F;
            flame.xScale = breathe;
            flame.zScale = breathe;
        }

        // embers: spiral away from the head, growing outwards and shrinking to nothing
        for (int i = 0; i < EMBER_COUNT; i++) {
            ModelPart ember = embers[i];
            float speed = 0.012F + (i % 4) * 0.004F;
            float p = (ageInTicks * speed + i * 0.6180339F) % 1.0F;
            float dir = (i % 2 == 0) ? 1.0F : -1.0F;
            float angle = i * 2.399F + ageInTicks * (0.12F + (i % 3) * 0.03F) * dir;
            float radius = (3.0F + (i % 5) * 1.5F) * (0.4F + p * 1.8F);
            ember.x = Mth.cos(angle) * radius;
            ember.z = Mth.sin(angle) * radius;
            ember.y = EMBER_START + p * EMBER_RANGE;
            float s = Math.max(0.05F, Mth.sin(p * Mth.PI) * (0.8F + (i % 3) * 0.3F));
            ember.xScale = s;
            ember.yScale = s;
            ember.zScale = s;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
