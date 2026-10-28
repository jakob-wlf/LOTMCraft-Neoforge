package de.jakob.lotm.item.models;// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class HolyArmour extends HumanoidModel<LivingEntity> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "holy_armor"), "main");
	private final ModelPart hat;
	private final ModelPart right_leg;
	private final ModelPart left_leg;
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart right_arm;
	private final ModelPart left_arm;

	public HolyArmour(ModelPart root) {
        super(root);
        this.hat = root.getChild("hat");
		this.right_leg = root.getChild("right_leg");
		this.left_leg = root.getChild("left_leg");
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.right_arm = root.getChild("right_arm");
		this.left_arm = root.getChild("left_arm");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition hat = partdefinition.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));

		PartDefinition right_leg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(84, 67).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F))
		.texOffs(24, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.05F)), PartPose.offset(-1.9F, 12.0F, 0.0F));

		PartDefinition left_leg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(84, 67).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)).mirror(false)
		.texOffs(24, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.05F)).mirror(false), PartPose.offset(1.9F, 12.0F, 0.0F));

		PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 66).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 112).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.15F))
		.texOffs(30, 31).addBox(-4.5F, 10.2F, -2.5F, 9.0F, 2.0F, 5.0F, new CubeDeformation(-0.1F))
		.texOffs(30, 92).addBox(-4.5F, 10.0F, -2.5F, 9.0F, 2.0F, 5.0F, new CubeDeformation(0.05F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition Body1_r1 = body.addOrReplaceChild("Body1_r1", CubeListBuilder.create().texOffs(54, 13).addBox(-2.0F, -7.0F, -2.0F, 4.0F, 11.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 18.0F, -0.85F, -0.0873F, 0.0F, 0.0F));

		PartDefinition Left_Leg_r1 = body.addOrReplaceChild("Left_Leg_r1", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -6.0F, -2.0F, 3.0F, 8.0F, 4.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(4.9F, 16.5F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition Right_Leg_r1 = body.addOrReplaceChild("Right_Leg_r1", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -6.0F, -2.0F, 3.0F, 8.0F, 4.0F, new CubeDeformation(0.2F)).mirror(false), PartPose.offsetAndRotation(-4.9F, 16.5F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition right_arm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(32, 0).mirror().addBox(-3.0F, -2.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(96, 115).mirror().addBox(-2.8F, 2.2F, -2.6F, 4.0F, 8.0F, 5.0F, new CubeDeformation(-0.1F)).mirror(false), PartPose.offset(-5.0F, 2.0F, 0.0F));

		PartDefinition Right_Arm5_r1 = right_arm.addOrReplaceChild("Right_Arm5_r1", CubeListBuilder.create().texOffs(40, 50).mirror().addBox(-1.5F, -1.5F, -2.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.15F)).mirror(false), PartPose.offsetAndRotation(-0.8222F, -3.3586F, 0.0F, 0.0F, 0.0F, 1.3963F));

		PartDefinition Right_Arm4_r1 = right_arm.addOrReplaceChild("Right_Arm4_r1", CubeListBuilder.create().texOffs(46, 44).mirror().addBox(-3.5F, -0.5F, -2.0F, 5.0F, 2.0F, 4.0F, new CubeDeformation(0.1F)).mirror(false), PartPose.offsetAndRotation(-1.2F, -2.5F, 0.0F, 0.0F, 0.0F, -0.5672F));

		PartDefinition Right_Arm3_r1 = right_arm.addOrReplaceChild("Right_Arm3_r1", CubeListBuilder.create().texOffs(112, 115).mirror().addBox(-1.0F, -5.5F, -3.0F, 3.0F, 8.0F, 5.0F, new CubeDeformation(-0.09F)).mirror(false), PartPose.offsetAndRotation(-2.4F, 7.6F, 0.4F, 0.0F, 0.0F, -0.1745F));

		PartDefinition left_arm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 0).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(96, 115).addBox(-1.2F, 2.2F, -2.6F, 4.0F, 8.0F, 5.0F, new CubeDeformation(-0.1F)), PartPose.offset(5.0F, 2.0F, 0.0F));

		PartDefinition Left_Arm5_r1 = left_arm.addOrReplaceChild("Left_Arm5_r1", CubeListBuilder.create().texOffs(46, 44).addBox(-1.5F, -0.5F, -2.0F, 5.0F, 2.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(1.2F, -2.5F, 0.0F, 0.0F, 0.0F, 0.5672F));

		PartDefinition Left_Arm4_r1 = left_arm.addOrReplaceChild("Left_Arm4_r1", CubeListBuilder.create().texOffs(40, 50).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.8222F, -3.3586F, 0.0F, 0.0F, 0.0F, -1.3963F));

		PartDefinition Left_Arm2_r1 = left_arm.addOrReplaceChild("Left_Arm2_r1", CubeListBuilder.create().texOffs(112, 115).addBox(-2.0F, -5.5F, -3.0F, 3.0F, 8.0F, 5.0F, new CubeDeformation(-0.09F)), PartPose.offsetAndRotation(2.4F, 7.6F, 0.4F, 0.0F, 0.0F, 0.1745F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		packedLight = LightTexture.FULL_BRIGHT;
		hat.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		right_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		left_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		head.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		right_arm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		left_arm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}