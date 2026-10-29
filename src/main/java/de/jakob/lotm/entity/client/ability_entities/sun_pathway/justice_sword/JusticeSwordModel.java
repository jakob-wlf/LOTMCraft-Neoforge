package de.jakob.lotm.entity.client.ability_entities.sun_pathway.justice_sword;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class JusticeSwordModel<T extends Entity> extends EntityModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "justice_sword"), "main");

	private static final int[] GOLD      = {0, 0};
	private static final int[] BLADE     = {40, 0};
	private static final int[] DARK_GOLD = {80, 0};
	private static final int[] GLOW      = {0, 40};
	private static final int[] GEM       = {40, 40};
	private static final int[] AURA      = {80, 40};

	private static final float HALO_Y   = 24.0F - 68.0F;
	private static final float SHARDS_Y = 24.0F - 30.0F;

	private final ModelPart root;
	private final ModelPart halo;
	private final ModelPart shards;

	public JusticeSwordModel(ModelPart root) {
		this.root = root;
		this.halo = root.getChild("halo");
		this.shards = root.getChild("shards");
	}

	private static void box(CubeListBuilder b, int[] m, float x1, float y1, float z1, float x2, float y2, float z2) {
		b.texOffs(m[0], m[1]).addBox(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1, CubeDeformation.NONE);
	}

	private static void boxMirrorZ(CubeListBuilder b, int[] m, float x1, float y1, float zNear, float x2, float y2, float zFar) {
		box(b, m, x1, y1, zNear, x2, y2, zFar);
		box(b, m, x1, y1, -zFar, x2, y2, -zNear);
	}

	private static void centered(CubeListBuilder b, int[] m, float hx, float y1, float hz, float y2) {
		box(b, m, -hx, y1, -hz, hx, y2, hz);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		CubeListBuilder blade = CubeListBuilder.create();
		box(blade, BLADE, -1.0F, -50, -3.0F, 1.0F, -24, 3.0F);
		box(blade, BLADE, -1.0F, -24, -2.5F, 1.0F, -16, 2.5F);
		box(blade, BLADE, -1.0F, -16, -2.0F, 1.0F, -9, 2.0F);
		box(blade, BLADE, -0.75F, -9, -1.5F, 0.75F, -4, 1.5F);
		box(blade, BLADE, -0.5F, -4, -1.0F, 0.5F, -1, 1.0F);
		box(blade, BLADE, -0.5F, -1, -0.5F, 0.5F, 0, 0.5F);
		box(blade, AURA, -1.5F, -48, -0.75F, 1.5F, -14, 0.75F);
		box(blade, AURA, -1.25F, -14, -0.5F, 1.25F, -8, 0.5F);
		boxMirrorZ(blade, AURA, -0.4F, -49, 3.0F, 0.4F, -24, 3.5F);
		boxMirrorZ(blade, AURA, -0.4F, -24, 2.5F, 0.4F, -17, 3.0F);
		boxMirrorZ(blade, AURA, -0.4F, -17, 2.0F, 0.4F, -10, 2.5F);
		for (int i = 0; i < 5; i++) {
			float c = -44 + i * 6;
			box(blade, GLOW, -1.75F, c - 0.75F, -1.25F, 1.75F, c + 0.75F, 1.25F);
		}
		root.addOrReplaceChild("blade", blade, PartPose.offset(0.0F, 24.0F, 0.0F));

		CubeListBuilder hilt = CubeListBuilder.create();
		box(hilt, GOLD, -3.0F, -56, -4.0F, 3.0F, -48, 4.0F);
		box(hilt, DARK_GOLD, -3.25F, -53, -4.25F, 3.25F, -51, 4.25F);
		box(hilt, GEM, -3.5F, -55, -1.5F, 3.5F, -49, 1.5F);
		box(hilt, DARK_GOLD, -2.0F, -48, -3.5F, 2.0F, -45.5F, 3.5F);
		boxMirrorZ(hilt, GOLD, -2.0F, -55, 4.0F, 2.0F, -50, 10.0F);
		boxMirrorZ(hilt, GOLD, -1.75F, -58, 10.0F, 1.75F, -52.5F, 15.0F);
		boxMirrorZ(hilt, GOLD, -1.5F, -61, 15.0F, 1.5F, -54.5F, 19.0F);
		boxMirrorZ(hilt, GOLD, -1.0F, -66, 19.0F, 1.0F, -58, 22.5F);
		boxMirrorZ(hilt, GLOW, -0.5F, -57, 10.0F, 0.5F, -56, 15.0F);
		boxMirrorZ(hilt, GLOW, -0.5F, -60, 15.0F, 0.5F, -59, 19.0F);
		boxMirrorZ(hilt, DARK_GOLD, -1.5F, -50, 4.0F, 1.5F, -46, 8.0F);
		boxMirrorZ(hilt, DARK_GOLD, -1.0F, -46, 8.0F, 1.0F, -43, 10.5F);
		centered(hilt, DARK_GOLD, 1.75F, -64, 1.75F, -56);
		centered(hilt, GOLD, 2.25F, -63, 2.25F, -62);
		centered(hilt, GOLD, 2.25F, -60.5F, 2.25F, -59.5F);
		centered(hilt, GOLD, 2.25F, -58, 2.25F, -57);
		centered(hilt, GOLD, 3.5F, -66, 3.5F, -64);
		centered(hilt, GOLD, 4.0F, -69, 4.0F, -66);
		centered(hilt, GOLD, 3.0F, -72, 3.0F, -69);
		centered(hilt, GEM, 1.75F, -75, 1.75F, -72);
		centered(hilt, GLOW, 0.75F, -77, 0.75F, -75);
		box(hilt, GEM, -4.4F, -68.5F, -1.5F, 4.4F, -66.5F, 1.5F);
		box(hilt, GEM, -1.5F, -68.5F, -4.4F, 1.5F, -66.5F, 4.4F);
		root.addOrReplaceChild("hilt", hilt, PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition halo = root.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offset(0.0F, HALO_Y, 0.0F));
		for (int k = 0; k < 8; k++) {
			CubeListBuilder ray = CubeListBuilder.create();
			if (k % 2 == 0) { // long ray
				box(ray, GLOW, -0.75F, -0.75F, 7.0F, 0.75F, 0.75F, 13.0F);
				box(ray, GEM, -1.25F, -1.25F, 13.0F, 1.25F, 1.25F, 14.5F);
			} else {          // short ray
				box(ray, GLOW, -0.5F, -0.5F, 7.0F, 0.5F, 0.5F, 10.5F);
				box(ray, AURA, -0.75F, -0.75F, 10.5F, 0.75F, 0.75F, 11.5F);
			}
			halo.addOrReplaceChild("ray" + k, ray, PartPose.rotation(0.0F, k * Mth.PI / 4.0F, 0.0F));
		}

		PartDefinition shards = root.addOrReplaceChild("shards", CubeListBuilder.create(), PartPose.offset(0.0F, SHARDS_Y, 0.0F));
		for (int k = 0; k < 4; k++) {
			CubeListBuilder shard = CubeListBuilder.create();
			box(shard, AURA, -0.6F, -4, 8.0F, 0.6F, 4, 9.5F);
			box(shard, AURA, -0.35F, -7, 8.4F, 0.35F, -4, 9.1F);
			box(shard, AURA, -0.35F, 4, 8.4F, 0.35F, 9, 9.1F);
			box(shard, GLOW, -0.3F, -2, 9.5F, 0.3F, 2, 9.9F);
			shards.addOrReplaceChild("shard" + k, shard, PartPose.rotation(0.0F, k * Mth.HALF_PI + Mth.PI / 4.0F, 0.0F));
		}

		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		halo.yRot = ageInTicks * 0.04F;
		float pulse = 1.0F + 0.06F * Mth.sin(ageInTicks * 0.15F);
		halo.xScale = pulse;
		halo.zScale = pulse;

		shards.yRot = -ageInTicks * 0.025F;
		shards.y = SHARDS_Y + Mth.sin(ageInTicks * 0.08F) * 1.5F;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		root.render(poseStack, vertexConsumer, LightTexture.FULL_BRIGHT, packedOverlay, color);
	}
}
