package de.jakob.lotm.entity.client.projectiles.frost_spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.FrostSpearProjectileEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Frost Spear: a deliberately simple low-tier spear. Frosted shaft, faceted ice blade, a pair of
 * icicle barbs, two crossed frost wisps trailing behind and a few drifting snow motes.
 * Model space: the tip points towards -Y, the wisps stream towards +Y.
 * Texture atlas: 128x64 (textures/entity/frost_spear/frost_spear.png).
 */
public class FrostSpearProjectileModel extends EntityModel<FrostSpearProjectileEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "frost_spear"), "main");

    private static final CubeDeformation NO_DEFORM = new CubeDeformation(0.0F);
    private static final int WISP_COUNT = 4;
    private static final int MOTE_COUNT = 6;
    private static final float MOTE_START = -6.0F;
    private static final float MOTE_RANGE = 90.0F;

    private final ModelPart root;
    private final ModelPart[] wisps = new ModelPart[WISP_COUNT];
    private final ModelPart[] motes = new ModelPart[MOTE_COUNT];

    public FrostSpearProjectileModel(ModelPart root) {
        super();
        this.root = root;
        for (int i = 0; i < WISP_COUNT; i++) wisps[i] = root.getChild("wisp_" + i);
        for (int i = 0; i < MOTE_COUNT; i++) motes[i] = root.getChild("mote_" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // ── solid body ──
        CubeListBuilder bodyCubes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 40.0F, 3.0F, NO_DEFORM) // shaft
                .texOffs(0, 44).addBox(-2.0F, 10.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band0
                .texOffs(0, 44).addBox(-2.0F, 26.0F, -2.0F, 4.0F, 3.0F, 4.0F, NO_DEFORM) // band1
                .texOffs(101, 0).addBox(-1.0F, 40.0F, -1.0F, 2.0F, 6.0F, 2.0F, NO_DEFORM) // tail0
                .texOffs(47, 44).addBox(-0.5F, 46.0F, -0.5F, 1.0F, 4.0F, 1.0F, NO_DEFORM) // tail1
                .texOffs(17, 44).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 2.0F, 5.0F, NO_DEFORM) // ring
                .texOffs(110, 0).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 4.0F, 4.0F, NO_DEFORM) // socket
                .texOffs(52, 44).addBox(-2.5F, -9.0F, -1.0F, 5.0F, 3.0F, 2.0F, NO_DEFORM) // blade0
                .texOffs(67, 44).addBox(-3.5F, -12.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade1
                .texOffs(86, 44).addBox(-3.5F, -15.0F, -1.0F, 7.0F, 3.0F, 2.0F, NO_DEFORM) // blade2
                .texOffs(105, 44).addBox(-3.0F, -18.0F, -1.0F, 6.0F, 3.0F, 2.0F, NO_DEFORM) // blade3
                .texOffs(0, 52).addBox(-2.5F, -21.0F, -1.0F, 5.0F, 3.0F, 2.0F, NO_DEFORM) // blade4
                .texOffs(15, 52).addBox(-2.0F, -24.0F, -1.0F, 4.0F, 3.0F, 2.0F, NO_DEFORM) // blade5
                .texOffs(28, 52).addBox(-1.5F, -27.0F, -1.0F, 3.0F, 3.0F, 2.0F, NO_DEFORM) // blade6
                .texOffs(39, 52).addBox(-1.0F, -30.0F, -1.0F, 2.0F, 3.0F, 2.0F, NO_DEFORM) // blade7
                .texOffs(48, 52).addBox(-0.5F, -33.0F, -0.5F, 1.0F, 3.0F, 1.0F, NO_DEFORM) // blade8
                .texOffs(81, 0).addBox(-1.0F, -26.0F, -1.5F, 2.0F, 20.0F, 3.0F, NO_DEFORM) // spine
                .texOffs(38, 44).addBox(3.0F, -5.0F, -1.0F, 2.0F, 4.0F, 2.0F, NO_DEFORM) // barbR0
                .texOffs(38, 44).addBox(-5.0F, -5.0F, -1.0F, 2.0F, 4.0F, 2.0F, NO_DEFORM) // barbL0
                .texOffs(92, 0).addBox(4.0F, -2.0F, -1.0F, 2.0F, 7.0F, 2.0F, NO_DEFORM) // barbR1
                .texOffs(92, 0).addBox(-6.0F, -2.0F, -1.0F, 2.0F, 7.0F, 2.0F, NO_DEFORM); // barbL1
        partdefinition.addOrReplaceChild("spear", bodyCubes, PartPose.offset(0.0F, 14.0F, 0.0F));

        // ── frost wisps ──
        partdefinition.addOrReplaceChild("wisp_0", CubeListBuilder.create()
                .texOffs(13, 0).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 30.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("wisp_1", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4.0F, 0.0F, -0.46F, 8.0F, 30.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        partdefinition.addOrReplaceChild("wisp_2", CubeListBuilder.create()
                .texOffs(51, 0).addBox(-3.0F, 0.0F, -0.42F, 6.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 56.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("wisp_3", CubeListBuilder.create()
                .texOffs(66, 0).addBox(-3.0F, 0.0F, -0.38F, 6.0F, 26.0F, 1.0F, NO_DEFORM),
                PartPose.offsetAndRotation(0.0F, 56.0F, 0.0F, 0.0F, 1.571F, 0.0F));

        // ── snow motes ──
        partdefinition.addOrReplaceChild("mote_0", CubeListBuilder.create().texOffs(53, 52).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_1", CubeListBuilder.create().texOffs(62, 52).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_2", CubeListBuilder.create().texOffs(53, 52).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_3", CubeListBuilder.create().texOffs(62, 52).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_4", CubeListBuilder.create().texOffs(53, 52).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);
        partdefinition.addOrReplaceChild("mote_5", CubeListBuilder.create().texOffs(62, 52).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, NO_DEFORM), PartPose.ZERO);

        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    /** Hide / show the wisps and motes (used when the spear is petrified). */
    public void setFrostVisible(boolean visible) {
        for (ModelPart p : wisps) p.visible = visible;
        for (ModelPart p : motes) p.visible = visible;
    }

    @Override
    public void setupAnim(FrostSpearProjectileEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // wisps: gentle sway and length shimmer
        for (int i = 0; i < WISP_COUNT; i++) {
            float phase = i * 1.9F;
            wisps[i].xRot = Mth.sin(ageInTicks * 0.4F + phase) * 0.06F;
            wisps[i].zRot = Mth.cos(ageInTicks * 0.33F + phase * 1.3F) * 0.06F;
            wisps[i].yScale = 1.0F + Mth.sin(ageInTicks * 0.7F + phase * 2.1F) * 0.10F;
        }

        // motes: slow flurry drifting away behind the spear
        for (int i = 0; i < MOTE_COUNT; i++) {
            ModelPart m = motes[i];
            float speed = 0.008F + (i % 3) * 0.003F;
            float p = (ageInTicks * speed + i * 0.6180339F) % 1.0F;
            float dir = (i % 2 == 0) ? 1.0F : -1.0F;
            float angle = i * 2.399F + ageInTicks * 0.09F * dir;
            float radius = (3.0F + (i % 3) * 1.6F) * (0.5F + p * 1.4F);
            m.x = Mth.cos(angle) * radius;
            m.z = Mth.sin(angle) * radius;
            m.y = MOTE_START + p * MOTE_RANGE;
            float s = Math.max(0.05F, Mth.sin(p * Mth.PI) * (0.7F + (i % 3) * 0.25F));
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
