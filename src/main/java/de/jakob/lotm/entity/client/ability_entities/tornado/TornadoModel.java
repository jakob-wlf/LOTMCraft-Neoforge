package de.jakob.lotm.entity.client.ability_entities.tornado;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class TornadoModel<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "tornado"), "main");

	private static final int TEX_SIZE = 1024;
	private static final int CORE_LAYERS = 14;
	private static final int SHELL_LAYERS = 7;
	private static final int DEBRIS_COUNT = 18;
	private static final float LAYER_H = 8.0F;
	private static final float TOTAL_H = CORE_LAYERS * LAYER_H;
	private static final float DUST_SIZE = 64.0F;

	private static final int[][] CORE = {
		{14, 872, 535},
        {16, 808, 535},
        {18, 736, 535},
        {24, 640, 535},
        {28, 528, 535},
        {36, 296, 535},
        {44, 0, 535},
        {52, 504, 464},
        {62, 0, 464},
        {72, 336, 371},
        {84, 0, 371},
        {96, 0, 266},
        {110, 0, 147},
        {124, 520, 0}
	};
	private static final int[][] SHELL = {
		{22, 440, 535},
        {30, 176, 535},
        {42, 712, 464},
        {58, 624, 371},
        {78, 384, 266},
        {102, 440, 147},
        {130, 0, 0}
	};
	private static final int[][] DEBRIS_TYPES = {
		{6, 5, 6, 928, 535},
        {10, 2, 4, 952, 535},
        {5, 1, 5, 980, 535}
	};
	private static final int DUST_U = 248;
	private static final int DUST_V = 464;

	private final ModelPart root;
	private final ModelPart dust;
	private final ModelPart[] core = new ModelPart[CORE_LAYERS];
	private final ModelPart[] shell = new ModelPart[SHELL_LAYERS];
	private final ModelPart[] debris = new ModelPart[DEBRIS_COUNT];
	private final ModelPart[] chunk = new ModelPart[DEBRIS_COUNT];

	public TornadoModel(ModelPart root) {
		this.root = root;
		this.dust = root.getChild("dust");
		for (int i = 0; i < CORE_LAYERS; i++) core[i] = root.getChild("core" + i);
		for (int i = 0; i < SHELL_LAYERS; i++) shell[i] = root.getChild("shell" + i);
		for (int i = 0; i < DEBRIS_COUNT; i++) {
			debris[i] = root.getChild("debris" + i);
			chunk[i] = debris[i].getChild("chunk");
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild("dust", CubeListBuilder.create().texOffs(DUST_U, DUST_V)
				.addBox(-DUST_SIZE / 2f, -3.0F, -DUST_SIZE / 2f, DUST_SIZE, 3.0F, DUST_SIZE),
				PartPose.offset(0.0F, 24.0F, 0.0F));

		float coreH = LAYER_H + 1.0F;
		for (int i = 0; i < CORE_LAYERS; i++) {
			float w = CORE[i][0];
			root.addOrReplaceChild("core" + i, CubeListBuilder.create().texOffs(CORE[i][1], CORE[i][2])
					.addBox(-w / 2f, -(i * LAYER_H) - coreH, -w / 2f, w, coreH, w),
					PartPose.offset(0.0F, 24.0F, 0.0F));
		}

		float shellH = 2 * LAYER_H + 1.0F;
		for (int i = 0; i < SHELL_LAYERS; i++) {
			float w = SHELL[i][0];
			root.addOrReplaceChild("shell" + i, CubeListBuilder.create().texOffs(SHELL[i][1], SHELL[i][2])
					.addBox(-w / 2f, -(2 * i * LAYER_H) - shellH, -w / 2f, w, shellH, w),
					PartPose.offset(0.0F, 24.0F, 0.0F));
		}

		for (int i = 0; i < DEBRIS_COUNT; i++) {
			int[] t = DEBRIS_TYPES[i % DEBRIS_TYPES.length];
			PartDefinition pivot = root.addOrReplaceChild("debris" + i, CubeListBuilder.create(),
					PartPose.offset(0.0F, 24.0F, 0.0F));
			pivot.addOrReplaceChild("chunk", CubeListBuilder.create().texOffs(t[3], t[4])
					.addBox(-t[0] / 2f, -t[1] / 2f, -t[2] / 2f, t[0], t[1], t[2]), PartPose.ZERO);
		}

		return LayerDefinition.create(mesh, TEX_SIZE, TEX_SIZE);
	}

	private static float radiusAt(float yUp) {
		float t = Mth.clamp(yUp / TOTAL_H, 0.0F, 1.0F);
		return (14.0F + 110.0F * (float) Math.pow(t, 1.7)) / 2.0F;
	}

	private static void sway(ModelPart part, float yUp, float age) {
		float t = Mth.clamp(yUp / TOTAL_H, 0.0F, 1.0F);
		float amp = 7.0F * t * t;
		part.x = amp * Mth.sin(age * 0.06F + t * 2.6F);
		part.z = amp * Mth.cos(age * 0.045F + t * 2.0F + 1.0F);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		float age = ageInTicks;

		dust.yRot = -age * 0.12F;

		for (int i = 0; i < CORE_LAYERS; i++) {
			float f = (i + 0.5F) / CORE_LAYERS;
			float degPerTick = 22.0F - 14.0F * f; // fast at the base, slower at the top
			core[i].yRot = -(float) Math.toRadians(age * degPerTick) + i * 0.9F;
			sway(core[i], (i + 0.5F) * LAYER_H, age);
		}

		for (int i = 0; i < SHELL_LAYERS; i++) {
			float f = (i + 0.5F) / SHELL_LAYERS;
			float degPerTick = 17.0F - 9.0F * f;
			shell[i].yRot = -(float) Math.toRadians(age * degPerTick) + i * 1.7F;
			sway(shell[i], (2 * i + 1) * LAYER_H, age);
		}

		for (int i = 0; i < DEBRIS_COUNT; i++) {
			float start = (i * 0.61803F) % 1.0F;
			float rise = 0.004F * (1.0F + (i % 3) * 0.35F);
			float progress = (start + age * rise) % 1.0F;
			float yUp = progress * TOTAL_H * 0.97F;

			float speed = 14.0F + (i % 5) * 2.5F;
			debris[i].yRot = i * 2.39996F - (float) Math.toRadians(age * speed);
			debris[i].y = 24.0F - yUp;
			sway(debris[i], yUp, age);

			ModelPart c = chunk[i];
			c.x = radiusAt(yUp) + 5.0F + (i % 4) * 3.0F;
			c.xRot = age * 0.21F * (1.0F + (i % 3) * 0.3F) + i;
			c.zRot = age * 0.17F + i * 2.0F;
			float s = Mth.clamp(Math.min(progress, 1.0F - progress) * 8.0F, 0.0F, 1.0F);
			c.xScale = s;
			c.yScale = s;
			c.zScale = s;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vc, int packedLight, int packedOverlay, int color) {
		dust.render(poseStack, vc, packedLight, packedOverlay, color);
		for (ModelPart p : core) p.render(poseStack, vc, packedLight, packedOverlay, color);
		for (ModelPart p : debris) p.render(poseStack, vc, packedLight, packedOverlay, color);
		for (ModelPart p : shell) p.render(poseStack, vc, packedLight, packedOverlay, color);
	}

	public ModelPart root() {
		return this.root;
	}
}
