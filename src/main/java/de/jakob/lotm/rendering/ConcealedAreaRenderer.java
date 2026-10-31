package de.jakob.lotm.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public class ConcealedAreaRenderer {
    private static final float VOID_R = 0.035f, VOID_G = 0.02f, VOID_B = 0.06f;
    private static final int STARS = 420;
    private static final float[] STAR_DIRS = new float[STARS * 4];

    private static float blend;
    private static float lastBlend;
    private static double insideRadius = 16;

    static {
        Random random = new Random(0x10C4L);
        for (int i = 0; i < STARS; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double y = random.nextDouble() * 1.6 - 0.6;
            double r = Math.sqrt(Math.max(0, 1 - y * y));
            STAR_DIRS[i * 4] = (float) (Math.cos(theta) * r);
            STAR_DIRS[i * 4 + 1] = (float) y;
            STAR_DIRS[i * 4 + 2] = (float) (Math.sin(theta) * r);
            STAR_DIRS[i * 4 + 3] = 0.12f + random.nextFloat() * 0.35f;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        lastBlend = blend;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            blend = 0;
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double radius = cameraVeil(camera);
        if (radius > 0) insideRadius = radius;
        blend = Mth.clamp(blend + (radius > 0 ? 0.08f : -0.08f), 0f, 1f);
    }

    private static double cameraVeil(Vec3 camera) {
        List<Double> veils = ClientHandler.concealmentSpheres();
        int stride = ClientHandler.VEIL_STRIDE;
        for (int i = 0; i + stride - 1 < veils.size(); i += stride) {
            if (veils.get(i + 5) > 0.5 || veils.get(i + 7) < 0.5) continue;
            double r = veils.get(i + 3);
            if (camera.distanceToSqr(veils.get(i), veils.get(i + 1), veils.get(i + 2)) < r * r) return r;
        }
        return -1;
    }

    private static float blend(float partialTick) {
        float t = Mth.lerp(partialTick, lastBlend, blend);
        return t * t * (3 - 2 * t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return;
        float t = blend((float) event.getPartialTick());
        if (t <= 0) return;

        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            event.setNearPlaneDistance(Mth.lerp(t, event.getNearPlaneDistance(), 0f));
            event.setFarPlaneDistance(Mth.lerp(t, event.getFarPlaneDistance(), 2f));
        } else {
            float far = (float) insideRadius * 1.6f + 4f;
            event.setNearPlaneDistance(Mth.lerp(t, event.getNearPlaneDistance(), 0f));
            event.setFarPlaneDistance(Mth.lerp(t, event.getFarPlaneDistance(), far));
            event.setFogShape(FogShape.SPHERE);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float t = blend((float) event.getPartialTick());
        if (t <= 0) return;
        event.setRed(Mth.lerp(t, event.getRed(), VOID_R));
        event.setGreen(Mth.lerp(t, event.getGreen(), VOID_G));
        event.setBlue(Mth.lerp(t, event.getBlue(), VOID_B));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float time = (mc.level.getGameTime() + partialTick) / 20f;

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            float t = blend(partialTick);
            if (t > 0) renderVoidSky(event.getPoseStack(), time, t);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderVeils(event.getPoseStack(), time);
        }
    }

    private static void renderVoidSky(PoseStack poseStack, float time, float t) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float distance = 90f;

        for (int i = 0; i < STARS; i++) {
            float x = STAR_DIRS[i * 4] * distance;
            float y = STAR_DIRS[i * 4 + 1] * distance;
            float z = STAR_DIRS[i * 4 + 2] * distance;
            float size = STAR_DIRS[i * 4 + 3];
            float twinkle = 0.45f + 0.55f * Mth.sin(time * (1.3f + (i % 7) * 0.31f) + i * 2.17f);
            float alpha = t * Mth.clamp(twinkle, 0.1f, 1f);
            float red = 0.75f + (i % 3) * 0.08f, green = 0.7f, blue = 0.95f;
            billboard(buffer, matrix, x, y, z, size, red, green, blue, alpha);
        }

        for (int ring = 0; ring < 3; ring++) {
            float height = 34f + ring * 10f;
            float radius = 46f - ring * 13f;
            float spin = time * (0.05f + ring * 0.03f) * (ring % 2 == 0 ? 1 : -1);
            int glyphs = 48 - ring * 12;
            for (int g = 0; g < glyphs; g++) {
                if ((g + ring) % 4 == 3) continue;
                float angle = spin + (float) (Math.PI * 2 * g / glyphs);
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.8f + g * 0.6f + ring);
                billboard(buffer, matrix, Mth.cos(angle) * radius, height, Mth.sin(angle) * radius,
                        0.35f, 0.55f, 0.35f, 0.85f, t * 0.35f * pulse);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void billboard(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float size,
                                  float red, float green, float blue, float alpha) {
        Vec3 dir = new Vec3(x, y, z).normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize().scale(size);
        Vec3 top = right.cross(dir).normalize().scale(size);
        buffer.addVertex(matrix, (float) (x - right.x - top.x), (float) (y - right.y - top.y), (float) (z - right.z - top.z)).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) (x + right.x - top.x), (float) (y + right.y - top.y), (float) (z + right.z - top.z)).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) (x + right.x + top.x), (float) (y + right.y + top.y), (float) (z + right.z + top.z)).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) (x - right.x + top.x), (float) (y - right.y + top.y), (float) (z - right.z + top.z)).setColor(red, green, blue, alpha);
    }

    private static void renderVeils(PoseStack poseStack, float time) {
        List<Double> veils = ClientHandler.concealmentSpheres();
        if (veils.isEmpty()) return;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();

        int stride = ClientHandler.VEIL_STRIDE;
        for (int i = 0; i + stride - 1 < veils.size(); i += stride) {
            double look = veils.get(i + 6);
            if (look < 0.25) continue;
            Vec3 center = new Vec3(veils.get(i), veils.get(i + 1), veils.get(i + 2));
            float radius = veils.get(i + 3).floatValue();
            boolean inside = camera.distanceToSqr(center) < (double) radius * radius;
            if (veils.get(i + 7) < 0.5 && inside) continue;
            boolean dark = look > 0.75;
            float haze = dark ? 0.3f : 0.1f;

            RenderSystem.defaultBlendFunc();
            if (!inside) {
                for (int layer = 0; layer < 4; layer++) {
                    shell(matrix, center, camera, radius - layer * 0.7f, time, layer, haze, false);
                }
            }
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            shell(matrix, center, camera, radius + 0.15f, time, 7, inside ? 0.12f : dark ? 0.35f : 0.18f, true);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void shell(Matrix4f matrix, Vec3 center, Vec3 camera, float radius, float time, int layer,
                              float strength, boolean rim) {
        int stacks = 24, slices = 48;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int s = 0; s < stacks; s++) {
            for (int l = 0; l < slices; l++) {
                vertex(buffer, matrix, center, camera, radius, time, layer, strength, rim, s, l, stacks, slices);
                vertex(buffer, matrix, center, camera, radius, time, layer, strength, rim, s + 1, l, stacks, slices);
                vertex(buffer, matrix, center, camera, radius, time, layer, strength, rim, s + 1, l + 1, stacks, slices);
                vertex(buffer, matrix, center, camera, radius, time, layer, strength, rim, s, l + 1, stacks, slices);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 camera, float radius,
                               float time, int layer, float strength, boolean rim, int s, int l, int stacks, int slices) {
        float phi = (float) Math.PI * s / stacks;
        float theta = (float) Math.PI * 2 * l / slices;
        float nx = Mth.sin(phi) * Mth.cos(theta);
        float ny = Mth.cos(phi);
        float nz = Mth.sin(phi) * Mth.sin(theta);

        float wave = Mth.sin(time * 1.7f + phi * 5f + theta * 3f + layer * 1.3f)
                * Mth.cos(time * 1.1f - theta * 4f + layer);
        float r = radius + wave * (rim ? 0.12f : 0.3f);

        double x = center.x + nx * r;
        double y = center.y + ny * r;
        double z = center.z + nz * r;

        Vec3 view = new Vec3(camera.x - x, camera.y - y, camera.z - z).normalize();
        float facing = Math.abs((float) (view.x * nx + view.y * ny + view.z * nz));
        float edge = 1f - facing;

        float red, green, blue, alpha;
        if (rim) {
            float shimmer = 0.5f + 0.5f * Mth.sin(time * 3f + theta * 9f + phi * 6f);
            alpha = strength * edge * edge * edge * (0.4f + 0.6f * shimmer);
            red = 0.55f;
            green = 0.4f;
            blue = 0.9f;
        } else {
            float shade = 0.5f + 0.5f * wave;
            alpha = strength * (0.6f + 0.4f * edge) * (0.75f + 0.25f * shade);
            red = VOID_R + 0.04f * shade;
            green = VOID_G + 0.02f * shade;
            blue = VOID_B + 0.07f * shade;
        }
        buffer.addVertex(matrix, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)).setColor(red, green, blue, Mth.clamp(alpha, 0f, 1f));
    }
}
