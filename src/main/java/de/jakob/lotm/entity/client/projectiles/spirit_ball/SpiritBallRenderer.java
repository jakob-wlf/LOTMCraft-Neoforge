package de.jakob.lotm.entity.client.projectiles.spirit_ball;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.projectiles.SpiritBallEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Random;

public class SpiritBallRenderer extends EntityRenderer<SpiritBallEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/spirit_ball/spirit_ball.png");
    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/spirit_ball/spirit_ball_glow.png");

    private static final float RADIUS = 0.125F;
    private static final float LIFETIME_TICKS = 100.0F;
    private static final int WISP_COUNT = 3;
    private static final int TRAIL_SEGMENTS = 6;

    private static final SphereMesh SPHERE = SphereMesh.build(8, 12);
    private static final SphereMesh SPHERE_LOW = SphereMesh.build(5, 8);

    public SpiritBallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpiritBallEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        // ── stable per-entity colour (same hue as the particles the entity spawns) ──
        Random random = new Random(entity.getUUID().getMostSignificantBits());
        float hue = random.nextFloat();
        float saturation = 0.5F + random.nextFloat() * 0.3F;
        float brightness = 0.92F + random.nextFloat() * 0.08F;
        int rgb = Mth.hsvToRgb(hue, saturation, brightness);
        float baseR = ((rgb >> 16) & 0xFF) / 255F;
        float baseG = ((rgb >> 8) & 0xFF) / 255F;
        float baseB = (rgb & 0xFF) / 255F;

        float time = entity.tickCount + partialTicks;
        float wave = Mth.sin(time * 0.18F * Mth.TWO_PI);
        float pulse = 1.0F + 0.15F * wave;

        float life = Math.min(Mth.clamp(time / 3.0F, 0.0F, 1.0F),
                Mth.clamp((LIFETIME_TICKS - time) / 10.0F, 0.0F, 1.0F));

        poseStack.pushPose();
        poseStack.scale(RADIUS * life, RADIUS * life, RADIUS * life);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 7.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 3.0F));
        VertexConsumer bodyVc = buffer.getBuffer(RenderType.entityTranslucentCull(TEXTURE));
        drawSphere(poseStack.last().pose(), bodyVc, SPHERE, baseR * pulse, baseG * pulse, baseB * pulse, 215);
        poseStack.popPose();

        VertexConsumer glowSurface = buffer.getBuffer(RenderType.eyes(TEXTURE));

        float cr = Mth.lerp(0.7F, baseR, 1.0F), cg = Mth.lerp(0.7F, baseG, 1.0F), cb = Mth.lerp(0.7F, baseB, 1.0F);
        poseStack.pushPose();
        float coreScale = RADIUS * 0.55F * life * (1.0F + 0.12F * wave);
        poseStack.scale(coreScale, coreScale, coreScale);
        drawSphere(poseStack.last().pose(), glowSurface, SPHERE_LOW, cr, cg, cb, 255);
        poseStack.popPose();

        for (int shell = 0; shell < 2; shell++) {
            float breathe = 1.0F + 0.10F * Mth.sin(time * 0.35F + shell * Mth.PI);
            float s = RADIUS * (1.35F + shell * 0.3F) * breathe * life;
            poseStack.pushPose();
            poseStack.scale(s, s, s);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (shell == 0 ? -9.0F : 5.0F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(time * (shell == 0 ? 4.0F : -6.0F)));
            int alpha = shell == 0 ? 70 : 38;
            drawSphere(poseStack.last().pose(), glowSurface, SPHERE_LOW,
                    Math.min(1F, baseR * 1.1F), Math.min(1F, baseG * 1.1F), Math.min(1F, baseB * 1.1F), alpha);
            poseStack.popPose();
        }

        // orbiting wisps
        for (int k = 0; k < WISP_COUNT; k++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 16.0F + k * (360.0F / WISP_COUNT)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(28.0F * (k - 1)));
            poseStack.translate(0.21F + 0.02F * Mth.sin(time * 0.3F + k * 2.0F), 0.0F, 0.0F);
            float ws = 0.03F * life;
            poseStack.scale(ws, ws, ws);
            drawSphere(poseStack.last().pose(), glowSurface, SPHERE_LOW, cr, cg, cb, 230);
            poseStack.popPose();
        }

        VertexConsumer spriteVc = buffer.getBuffer(RenderType.eyes(GLOW_TEXTURE));
        int tr = toByte(Mth.lerp(0.25F, baseR, 1.0F)), tg = toByte(Mth.lerp(0.25F, baseG, 1.0F)), tb = toByte(Mth.lerp(0.25F, baseB, 1.0F));

        Vec3 vel = entity.getDeltaMovement();
        double speed = vel.length();
        if (speed > 0.02) {
            Vec3 step = vel.scale(Math.min(1.0, 1.5 / speed) * 0.5);   // cap very fast balls
            for (int i = 1; i <= TRAIL_SEGMENTS; i++) {
                float f = 1.0F - i / (float) (TRAIL_SEGMENTS + 1);
                poseStack.pushPose();
                poseStack.translate(-step.x * i, -step.y * i, -step.z * i);
                billboard(poseStack, spriteVc, 0.30F * f * life, 0.0F, 0.0F, 0.5F, tr, tg, tb, (int) (95 * f));
                poseStack.popPose();
            }
        }

        billboard(poseStack, spriteVc, 0.46F * (1.0F + 0.10F * wave) * life, 0.0F, 0.0F, 0.5F,
                toByte(baseR), toByte(baseG), toByte(baseB), (int) (165 * Math.min(1F, pulse)));
        billboard(poseStack, spriteVc, 0.62F * (1.0F + 0.18F * wave) * life, time * 2.5F, 0.5F, 1.0F, tr, tg, tb, 140);
        billboard(poseStack, spriteVc, 0.40F * (1.0F - 0.15F * wave) * life, 45.0F - time * 3.5F, 0.5F, 1.0F, 255, 255, 255, 110);

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected int getBlockLightLevel(@NotNull SpiritBallEntity entity, @NotNull BlockPos pos) {
        return 15;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SpiritBallEntity entity) {
        return TEXTURE;
    }

    private static int toByte(float v) {
        return Mth.clamp((int) (v * 255.0F), 0, 255);
    }

    private static void drawSphere(Matrix4f matrix, VertexConsumer vc, SphereMesh mesh,
                                   float red, float green, float blue, int alpha) {
        int r = toByte(red), g = toByte(green), b = toByte(blue);
        float[] d = mesh.data;
        for (int i = 0; i < d.length; i += 5) {
            float x = d[i], y = d[i + 1], z = d[i + 2];
            vertex(vc, matrix, x, y, z, d[i + 3], d[i + 4], r, g, b, alpha, x, y, z);
        }
    }

    private void billboard(PoseStack poseStack, VertexConsumer vc, float size, float rollDegrees,
                           float u0, float u1, int r, int g, int b, int a) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(rollDegrees));
        Matrix4f m = poseStack.last().pose();
        float h = size * 0.5F;
        vertex(vc, m, -h, -h, 0, u0, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, m,  h, -h, 0, u1, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, m,  h,  h, 0, u1, 0, r, g, b, a, 0, 0, 1);
        vertex(vc, m, -h,  h, 0, u0, 0, r, g, b, a, 0, 0, 1);
        vertex(vc, m, -h,  h, 0, u0, 0, r, g, b, a, 0, 0, -1);
        vertex(vc, m,  h,  h, 0, u1, 0, r, g, b, a, 0, 0, -1);
        vertex(vc, m,  h, -h, 0, u1, 1, r, g, b, a, 0, 0, -1);
        vertex(vc, m, -h, -h, 0, u0, 1, r, g, b, a, 0, 0, -1);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }

    private static final class SphereMesh {
        final float[] data;

        private SphereMesh(float[] data) {
            this.data = data;
        }

        static SphereMesh build(int rings, int segments) {
            float[] out = new float[rings * segments * 4 * 5];
            int p = 0;
            for (int i = 0; i < rings; i++) {
                float theta1 = Mth.PI * i / rings;
                float theta2 = Mth.PI * (i + 1) / rings;
                float vTop = (float) i / rings, vBot = (float) (i + 1) / rings;
                for (int j = 0; j < segments; j++) {
                    float phi1 = Mth.TWO_PI * j / segments;
                    float phi2 = Mth.TWO_PI * (j + 1) / segments;
                    float u1 = (float) j / segments, u2 = (float) (j + 1) / segments;
                    p = put(out, p, theta1, phi1, u1, vTop);
                    p = put(out, p, theta2, phi1, u1, vBot);
                    p = put(out, p, theta2, phi2, u2, vBot);
                    p = put(out, p, theta1, phi2, u2, vTop);
                }
            }
            return new SphereMesh(out);
        }

        private static int put(float[] out, int p, float theta, float phi, float u, float v) {
            out[p++] = (float) (Math.sin(theta) * Math.cos(phi));
            out[p++] = (float) Math.cos(theta);
            out[p++] = (float) (Math.sin(theta) * Math.sin(phi));
            out[p++] = u;
            out[p++] = v;
            return p;
        }
    }
}
