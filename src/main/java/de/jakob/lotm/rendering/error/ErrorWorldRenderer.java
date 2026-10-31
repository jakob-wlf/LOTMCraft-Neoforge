package de.jakob.lotm.rendering.error;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Map;

/**
 * World space visuals of the Error pathway: sanity bars, the Quick Theft mark, highlighted containers and items,
 * and the strings between marionettes / second bodies and their masters.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class ErrorWorldRenderer {

    private static final Matrix4f lastModelView = new Matrix4f();
    private static final Matrix4f lastProjection = new Matrix4f();
    private static Vec3 lastCamera = Vec3.ZERO;
    private static boolean hasMatrices = false;

    private ErrorWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Camera camera = event.getCamera();
        lastModelView.set(event.getModelViewMatrix());
        lastProjection.set(event.getProjectionMatrix());
        lastCamera = camera.getPosition();
        hasMatrices = true;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        PoseStack poseStack = event.getPoseStack();

        renderSanityBars(mc, poseStack, camera, partial);
        renderTheftMark(mc, poseStack, camera, partial, now);
        if (ErrorClientFlags.observationFocusActive) renderHighlights(mc, poseStack, camera, now);
        if (now - ErrorClientState.linksReceived < 40) renderLinks(mc, poseStack, camera, partial, now);
    }

    // ==================== SCREEN PROJECTION ====================

    /**
     * Projects a world position onto the gui. Returns null if the point is behind the camera.
     */
    public static float[] project(Vec3 world, int guiWidth, int guiHeight) {
        if (!hasMatrices) return null;
        Vector4f vector = new Vector4f((float) (world.x - lastCamera.x), (float) (world.y - lastCamera.y), (float) (world.z - lastCamera.z), 1f);
        lastModelView.transform(vector);
        lastProjection.transform(vector);
        if (vector.w <= 0.05f) return null;
        float x = vector.x / vector.w;
        float y = vector.y / vector.w;
        return new float[]{(x * 0.5f + 0.5f) * guiWidth, (1f - (y * 0.5f + 0.5f)) * guiHeight};
    }

    // ==================== SANITY BARS ====================

    private static void renderSanityBars(Minecraft mc, PoseStack poseStack, Camera camera, float partial) {
        if (ErrorClientState.sanityReveals.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (Map.Entry<Integer, ErrorClientState.SanityReveal> entry : ErrorClientState.sanityReveals.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)) continue;

            Vec3 pos = living.getPosition(partial).add(0, living.getBbHeight() + 0.45, 0);
            poseStack.pushPose();
            poseStack.translate(pos.x - camera.getPosition().x, pos.y - camera.getPosition().y, pos.z - camera.getPosition().z);
            poseStack.mulPose(camera.rotation());
            Matrix4f matrix = poseStack.last().pose();

            float width = 0.9f;
            float height = 0.06f;
            float fill = Math.max(0, Math.min(1, entry.getValue().sanity()));

            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            quad(builder, matrix, -width / 2 - 0.02f, -height / 2 - 0.02f, width / 2 + 0.02f, height / 2 + 0.02f, 0xB0000000);
            quad(builder, matrix, -width / 2, -height / 2, -width / 2 + width * fill, height / 2, 0xF0FF8C1A);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            poseStack.popPose();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float x2, float y2, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        builder.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y2, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x2, y1, 0).setColor(r, g, b, a);
    }

    // ==================== QUICK THEFT MARK ====================

    private static void renderTheftMark(Minecraft mc, PoseStack poseStack, Camera camera, float partial, long now) {
        if (ErrorClientState.theftMark < 0 || now - ErrorClientState.theftMarkReceived > 25) return;
        Entity entity = mc.level.getEntity(ErrorClientState.theftMark);
        if (entity == null) return;

        double lift = entity instanceof ItemEntity ? 0.45 : entity.getBbHeight() * 0.5;
        Vec3 pos = entity.getPosition(partial).add(0, lift, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(pos.x - camera.getPosition().x, pos.y - camera.getPosition().y, pos.z - camera.getPosition().z);
        poseStack.mulPose(camera.rotation());
        float pulse = 0.8f + 0.2f * (float) Math.sin(now * 0.4);
        poseStack.scale(pulse, pulse, pulse);
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float size = 0.22f;
        float thickness = 0.035f;
        diagonal(builder, matrix, -size, -size, size, size, thickness + 0.02f, 0xC0000000);
        diagonal(builder, matrix, -size, size, size, -size, thickness + 0.02f, 0xC0000000);
        diagonal(builder, matrix, -size, -size, size, size, thickness, 0xFFE0323C);
        diagonal(builder, matrix, -size, size, size, -size, thickness, 0xFFE0323C);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void diagonal(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float x2, float y2, float thickness, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = -dy / length * thickness, ny = dx / length * thickness;
        builder.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(r, g, b, a);
    }

    // ==================== SUPERIOR OBSERVATION FOCUS ====================

    private static void renderHighlights(Minecraft mc, PoseStack poseStack, Camera camera, long now) {
        Vec3 cam = camera.getPosition();
        int radius = 32;

        record Highlight(AABB box, float r, float g, float b, float distance) {}
        java.util.List<Highlight> highlights = new java.util.ArrayList<>();

        int chunkX = mc.player.blockPosition().getX() >> 4;
        int chunkZ = mc.player.blockPosition().getZ() >> 4;
        for (int cx = chunkX - 2; cx <= chunkX + 2; cx++) {
            for (int cz = chunkZ - 2; cz <= chunkZ + 2; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof Container)) continue;
                    double distance = Math.sqrt(blockEntity.getBlockPos().distSqr(mc.player.blockPosition()));
                    if (distance > radius) continue;
                    highlights.add(new Highlight(new AABB(blockEntity.getBlockPos()).deflate(0.02), 1f, 0.78f, 0.28f, (float) distance));
                }
            }
        }

        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(radius))) {
            // items glow in the colour of their rarity, worthless ones in a pale silver
            Integer rarity = item.getItem().getRarity().color().getColor();
            int color = rarity == null || item.getItem().getRarity() == net.minecraft.world.item.Rarity.COMMON ? 0xC8D2E6 : rarity;
            float bob = (float) Math.sin((now + item.getId() * 7) * 0.1) * 0.05f;
            highlights.add(new Highlight(item.getBoundingBox().inflate(0.12).move(0, bob, 0),
                    ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, item.distanceTo(mc.player)));
        }

        if (highlights.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();

        // faint tinted volume
        BufferBuilder faces = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Highlight h : highlights) {
            float a = 0.1f * fadeWithDistance(h.distance, radius) * (0.75f + 0.25f * (float) Math.sin(now * 0.12 + h.distance));
            boxFaces(faces, matrix, h.box, h.r, h.g, h.b, a);
        }
        BufferUploader.drawWithShader(faces.buildOrThrow());

        // glowing corner brackets
        RenderSystem.lineWidth(2.5f);
        BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (Highlight h : highlights) {
            float a = (0.55f + 0.3f * (float) Math.sin(now * 0.15 + h.distance)) * fadeWithDistance(h.distance, radius);
            corners(lines, matrix, h.box, h.r, h.g, h.b, a);
        }
        BufferUploader.drawWithShader(lines.buildOrThrow());
        poseStack.popPose();

        RenderSystem.lineWidth(1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static float fadeWithDistance(float distance, float radius) {
        return Math.max(0.15f, 1f - (distance / radius) * (distance / radius));
    }

    private static void boxFaces(BufferBuilder builder, Matrix4f m, AABB box, float r, float g, float b, float a) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
        float[][] faces = {
                {x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1}, {x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2},
                {x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2}, {x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1},
                {x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2}, {x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1}
        };
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) builder.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, b, a);
        }
    }

    /** Only the corners of the box, like a jeweller's loupe marking something worth a look. */
    private static void corners(BufferBuilder builder, Matrix4f m, AABB box, float r, float g, float b, float a) {
        double lx = (box.maxX - box.minX) * 0.3, ly = (box.maxY - box.minY) * 0.3, lz = (box.maxZ - box.minZ) * 0.3;
        for (double x : new double[]{box.minX, box.maxX}) {
            for (double y : new double[]{box.minY, box.maxY}) {
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    double sx = x == box.minX ? 1 : -1, sy = y == box.minY ? 1 : -1, sz = z == box.minZ ? 1 : -1;
                    segment(builder, m, x, y, z, x + sx * lx, y, z, r, g, b, a);
                    segment(builder, m, x, y, z, x, y + sy * ly, z, r, g, b, a);
                    segment(builder, m, x, y, z, x, y, z + sz * lz, r, g, b, a);
                }
            }
        }
    }

    private static void segment(BufferBuilder builder, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2,
                                float r, float g, float b, float a) {
        builder.addVertex(m, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        builder.addVertex(m, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a * 0.25f);
    }

    // ==================== STRINGS (Seq 3) ====================

    private static void renderLinks(Minecraft mc, PoseStack poseStack, Camera camera, float partial, long now) {
        if (ErrorClientState.links.isEmpty()) return;
        Vec3 cam = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(1.5f);

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;

        for (ErrorClientState.Link link : ErrorClientState.links) {
            Entity a = mc.level.getEntity(link.a());
            if (a == null) continue;
            Entity b = link.b() >= 0 ? mc.level.getEntity(link.b()) : null;

            Vec3 from = a.getPosition(partial).add(0, a.getBbHeight() + 0.3, 0);
            Vec3 to = b != null ? b.getPosition(partial).add(0, b.getBbHeight() + 0.3, 0) : from.add(0, 6, 0);

            float[] color = switch (link.type()) {
                case "parasite" -> new float[]{0.35f, 0.4f, 1f};
                case "avatar" -> new float[]{0.8f, 0.5f, 1f};
                case "summon" -> new float[]{0.9f, 0.9f, 0.6f};
                default -> new float[]{0.85f, 0.85f, 0.95f};
            };

            // the string sags a little and trembles
            int segments = 16;
            Vec3 previous = from;
            for (int i = 1; i <= segments; i++) {
                double t = i / (double) segments;
                double sag = Math.sin(t * Math.PI) * Math.min(1.5, from.distanceTo(to) * 0.05);
                double tremble = Math.sin(now * 0.3 + t * 12) * 0.03;
                Vec3 point = from.lerp(to, t).add(tremble, -sag, tremble);
                float alpha = b == null ? (float) (0.7 * (1 - t)) : 0.75f;
                builder.addVertex(matrix, (float) previous.x, (float) previous.y, (float) previous.z).setColor(color[0], color[1], color[2], alpha);
                builder.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(color[0], color[1], color[2], alpha);
                previous = point;
                any = true;
            }
        }

        if (any) BufferUploader.drawWithShader(builder.buildOrThrow());
        else builder.build();
        poseStack.popPose();

        RenderSystem.lineWidth(1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
