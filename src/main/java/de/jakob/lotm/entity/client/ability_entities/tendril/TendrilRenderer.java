package de.jakob.lotm.entity.client.ability_entities.tendril;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.entity.custom.ability_entities.TendrilEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a {@link TendrilEntity}: vines as tapered, textured tubes with leaves, threads as thin glowing ribbons.
 * Both grow along a curve from the entity to the target and may coil around the target.
 */
public class TendrilRenderer extends EntityRenderer<TendrilEntity> {

    private static final ResourceLocation VINE = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/tendril/vine.png");
    private static final ResourceLocation LEAF = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/fx/leaf.png");
    private static final int SIDES = 6;

    public TendrilRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TendrilEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        List<Vec3> path = buildPath(entity, partialTick, age);
        if (path.size() < 2) return;

        float grow = Mth.clamp(age / entity.getGrow(), 0, 1);
        grow = 1 - (1 - grow) * (1 - grow);
        float wither = Mth.clamp((age - (entity.getLife() - entity.getFade())) / entity.getFade(), 0, 1);

        poseStack.pushPose();
        Vec3 origin = entity.getPosition(partialTick);
        poseStack.translate(-origin.x, -origin.y, -origin.z);
        if (entity.getKind() == TendrilEntity.Kind.VINE) {
            renderVine(entity, path, grow, wither, poseStack, buffers);
        } else {
            renderThread(entity, path, grow, wither, age, partialTick, poseStack, buffers);
        }
        poseStack.popPose();
    }

    // ==================== shape ====================

    private static List<Vec3> buildPath(TendrilEntity entity, float partialTick, float age) {
        RandomSource random = RandomSource.create(entity.getSeed());
        boolean vine = entity.getKind() == TendrilEntity.Kind.VINE;
        Vec3 start = entity.getPosition(partialTick);
        Entity target = entity.getTarget();
        double sway = Math.sin(age * .12 + (entity.getSeed() & 255));
        List<Vec3> points = new ArrayList<>();

        if (target != null && entity.getCoil() > 0 && entity.getShell() > 0) {
            shell(points, entity, target, start, random, partialTick, sway);
        } else if (target != null && entity.getCoil() > 0) {
            Vec3 base = target.getPosition(partialTick);
            double height = target.getBbHeight();
            double radius = target.getBbWidth() * .5 + (vine ? .2 : .1);
            double angle = Math.atan2(start.z - base.z, start.x - base.x) + (random.nextDouble() - .5) * .9;
            double fromY = height * (vine ? .05 + .1 * random.nextDouble() : .15 + .5 * random.nextDouble());
            double toY = Math.min(height * .92, fromY + height * (vine ? .55 + .25 * random.nextDouble() : .2 + .2 * random.nextDouble()));
            int direction = random.nextBoolean() ? 1 : -1;

            Vec3 attach = base.add(Math.cos(angle) * radius, fromY, Math.sin(angle) * radius);
            approach(points, start, attach, base, vine, random, sway);

            float turns = entity.getCoil();
            int steps = Math.max(10, (int) (turns * 22));
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                double a = angle + direction * t * turns * Math.PI * 2;
                double r = radius * (1 - .12 * t);
                points.add(base.add(Math.cos(a) * r, Mth.lerp(t, fromY, toY), Math.sin(a) * r));
            }
        } else {
            Vec3 end = target != null ? target.getPosition(partialTick).add(0, target.getBbHeight() * .5, 0) : entity.getRenderEnd(partialTick);
            approach(points, start, end, null, vine, random, sway);
        }
        return points;
    }

    /** Spirals over an egg shaped shell around the target, like one strand of a cocoon. */
    private static void shell(List<Vec3> points, TendrilEntity entity, Entity target, Vec3 start, RandomSource random, float partialTick, double sway) {
        Vec3 base = target.getPosition(partialTick);
        double bottom = -.15;
        double top = target.getBbHeight() + .3;
        double radius = entity.getShell();
        Vec3 range = entity.getEnd();
        double from = range.x;
        double to = range.y;
        double angle = Math.atan2(start.z - base.z, start.x - base.x) + (random.nextDouble() - .5) * .6;
        int direction = random.nextBoolean() ? 1 : -1;
        double wobble = random.nextDouble() * Math.PI * 2;

        Vec3 attach = shellPoint(base, bottom, top, radius, from, angle, 0);
        approach(points, start, attach, base, false, random, sway);

        float turns = entity.getCoil();
        int steps = Math.max(16, (int) (turns * 26));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double a = angle + direction * t * turns * Math.PI * 2;
            double bulge = .06 * Math.sin(t * 9 + wobble);
            points.add(shellPoint(base, bottom, top, radius, Mth.lerp(t, from, to), a, bulge));
        }
    }

    private static Vec3 shellPoint(Vec3 base, double bottom, double top, double radius, double height, double angle, double bulge) {
        double y = Mth.lerp(height, bottom, top);
        double middle = (bottom + top) / 2;
        double half = (top - bottom) / 2;
        // slightly wider below the middle, like an egg standing up
        double k = (y - middle) / half;
        double r = radius * Math.sqrt(Math.max(0, 1 - k * k)) * (1 - .12 * k) + bulge;
        return base.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
    }

    /** Cubic curve to {@code end}: vines shoot up and arc over into it, threads run almost straight. */
    private static void approach(List<Vec3> points, Vec3 start, Vec3 end, @Nullable Vec3 around, boolean vine, RandomSource random, double sway) {
        Vec3 d = end.subtract(start);
        double length = d.length();
        if (length < 1e-4) return;
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        double bend = random.nextDouble() - .5;

        Vec3 c1;
        Vec3 c2;
        if (vine) {
            Vec3 out = around == null ? d.normalize().reverse() : new Vec3(end.x - around.x, 0, end.z - around.z).normalize();
            c1 = start.add(0, length * .55 + .4, 0).add(side.scale(bend * length * .45 + sway * .08 * length));
            c2 = end.add(out.scale(length * .3)).add(0, length * .3, 0).add(side.scale(-bend * length * .2));
        } else {
            // long free threads stay taut instead of bowing out by metres
            double slack = Math.min(length, 6);
            Vec3 bow = side.scale(bend * slack * .12 + sway * .01 * slack).add(0, -slack * .04, 0);
            c1 = start.add(d.scale(1 / 3.0)).add(bow);
            c2 = start.add(d.scale(2 / 3.0)).add(bow);
        }

        int steps = Mth.clamp((int) (length * 5), 10, 48);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double u = 1 - t;
            points.add(start.scale(u * u * u)
                    .add(c1.scale(3 * u * u * t))
                    .add(c2.scale(3 * u * t * t))
                    .add(end.scale(t * t * t)));
        }
    }

    /** The part of the path up to {@code visible} blocks along it, with the running length at every point. */
    private record Trimmed(List<Vec3> points, float[] lengths) {
    }

    private static Trimmed trim(List<Vec3> path, float visible) {
        List<Vec3> points = new ArrayList<>();
        List<Float> lengths = new ArrayList<>();
        points.add(path.getFirst());
        lengths.add(0f);
        float travelled = 0;
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1);
            Vec3 b = path.get(i);
            float segment = (float) a.distanceTo(b);
            if (travelled + segment >= visible) {
                float t = segment < 1e-5f ? 0 : (visible - travelled) / segment;
                points.add(a.lerp(b, t));
                lengths.add(visible);
                break;
            }
            travelled += segment;
            points.add(b);
            lengths.add(travelled);
        }
        float[] array = new float[lengths.size()];
        for (int i = 0; i < array.length; i++) array[i] = lengths.get(i);
        return new Trimmed(points, array);
    }

    private static float totalLength(List<Vec3> path) {
        float total = 0;
        for (int i = 1; i < path.size(); i++) total += (float) path.get(i - 1).distanceTo(path.get(i));
        return total;
    }

    private static Vec3 tangent(List<Vec3> points, int i) {
        Vec3 a = points.get(Math.max(0, i - 1));
        Vec3 b = points.get(Math.min(points.size() - 1, i + 1));
        Vec3 t = b.subtract(a);
        return t.lengthSqr() < 1e-10 ? new Vec3(0, 1, 0) : t.normalize();
    }

    // ==================== vines ====================

    private void renderVine(TendrilEntity entity, List<Vec3> path, float grow, float wither, PoseStack poseStack, MultiBufferSource buffers) {
        // withering vines brown and sink back the way they came
        float visible = totalLength(path) * grow * (1 - wither * .85f);
        if (visible < .05f) return;
        Trimmed trimmed = trim(path, visible);
        List<Vec3> points = trimmed.points();
        float[] lengths = trimmed.lengths();
        int n = points.size();
        if (n < 2) return;

        // parallel transport frames so the tube does not twist
        Vec3[] normals = new Vec3[n];
        Vec3[] binormals = new Vec3[n];
        Vec3[] tangents = new Vec3[n];
        Vec3 previous = null;
        for (int i = 0; i < n; i++) {
            Vec3 t = tangent(points, i);
            Vec3 normal = previous == null ? anyPerpendicular(t) : previous.subtract(t.scale(previous.dot(t)));
            normal = normal.lengthSqr() < 1e-8 ? anyPerpendicular(t) : normal.normalize();
            tangents[i] = t;
            normals[i] = normal;
            binormals[i] = t.cross(normal);
            previous = normal;
        }

        float[] radii = new float[n];
        int[] light = new int[n];
        for (int i = 0; i < n; i++) {
            float along = lengths[i] / visible;
            radii[i] = entity.getWidth() * (1 - .72f * along) * (1 - wither * .3f) * (i == n - 1 ? .25f : 1);
            light[i] = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(points.get(i)));
        }

        int r = (int) Mth.lerp(wither, 255, 150);
        int g = (int) Mth.lerp(wither, 255, 118);
        int b = (int) Mth.lerp(wither, 255, 72);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer stem = buffers.getBuffer(RenderType.entityCutoutNoCull(VINE));
        for (int i = 0; i < n - 1; i++) {
            for (int k = 0; k < SIDES; k++) {
                float u0 = k / (float) SIDES;
                float u1 = (k + 1) / (float) SIDES;
                tubeVertex(stem, pose, points.get(i), normals[i], binormals[i], radii[i], u0, lengths[i] * 1.6f, light[i], r, g, b);
                tubeVertex(stem, pose, points.get(i + 1), normals[i + 1], binormals[i + 1], radii[i + 1], u0, lengths[i + 1] * 1.6f, light[i + 1], r, g, b);
                tubeVertex(stem, pose, points.get(i + 1), normals[i + 1], binormals[i + 1], radii[i + 1], u1, lengths[i + 1] * 1.6f, light[i + 1], r, g, b);
                tubeVertex(stem, pose, points.get(i), normals[i], binormals[i], radii[i], u1, lengths[i] * 1.6f, light[i], r, g, b);
            }
        }

        // leaves sprout behind the growing tip
        VertexConsumer leaves = buffers.getBuffer(RenderType.entityCutoutNoCull(LEAF));
        RandomSource random = RandomSource.create(entity.getSeed() * 31L);
        int segment = 0;
        for (float s = .35f; s < visible - .1f; s += .42f * (.8f + .4f * random.nextFloat())) {
            while (segment < n - 2 && lengths[segment + 1] < s) segment++;
            float t = Mth.clamp((s - lengths[segment]) / Math.max(1e-4f, lengths[segment + 1] - lengths[segment]), 0, 1);
            Vec3 at = points.get(segment).lerp(points.get(segment + 1), t);
            Vec3 tan = tangents[segment];
            double phi = random.nextFloat() * Math.PI * 2;
            Vec3 radial = normals[segment].scale(Math.cos(phi)).add(binormals[segment].scale(Math.sin(phi)));
            float size = .2f * Mth.clamp((visible - s) / .5f, 0, 1) * (1 - wither * .5f) * (.8f + .4f * random.nextFloat());
            int tint = random.nextInt(50);
            if (size < .01f) continue;

            Vec3 axis = radial.scale(.75).add(tan.scale(.65)).normalize();
            Vec3 across = tan.cross(radial).normalize();
            Vec3 stemPoint = at.add(radial.scale(radii[segment] * .8f));
            Vec3 middle = stemPoint.add(axis.scale(size * .7f));
            int leafLight = light[segment];
            int lr = (int) Mth.lerp(wither, 80 + tint, 140);
            int lg = (int) Mth.lerp(wither, 170 + tint, 110);
            int lb = (int) Mth.lerp(wither, 60, 55);
            leafVertex(leaves, pose, stemPoint, 0, 1, radial, leafLight, lr, lg, lb);
            leafVertex(leaves, pose, middle.add(across.scale(size * .7f)), 1, 1, radial, leafLight, lr, lg, lb);
            leafVertex(leaves, pose, stemPoint.add(axis.scale(size * 1.4f)), 1, 0, radial, leafLight, lr, lg, lb);
            leafVertex(leaves, pose, middle.subtract(across.scale(size * .7f)), 0, 0, radial, leafLight, lr, lg, lb);
        }
    }

    private static Vec3 anyPerpendicular(Vec3 t) {
        Vec3 helper = Math.abs(t.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return t.cross(helper).normalize();
    }

    private static void tubeVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 normal, Vec3 binormal,
                                   float radius, float u, float v, int light, int r, int g, int b) {
        double angle = u * Math.PI * 2;
        Vec3 out = normal.scale(Math.cos(angle)).add(binormal.scale(Math.sin(angle)));
        Vec3 p = center.add(out.scale(radius));
        consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) out.x, (float) out.y, (float) out.z);
    }

    private static void leafVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, float u, float v, Vec3 normal,
                                   int light, int r, int g, int b) {
        consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    // ==================== threads ====================

    private void renderThread(TendrilEntity entity, List<Vec3> path, float grow, float wither, float age, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        float visible = totalLength(path) * grow;
        if (visible < .05f) return;
        Trimmed trimmed = trim(path, visible);
        float alpha = 1 - wither;
        Matrix4f matrix = poseStack.last().pose();
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());

        float width = entity.getWidth();
        ribbon(consumer, matrix, trimmed, camera, width * 2.8f, 120, 190, 205, alpha * .3f, age);
        ribbon(consumer, matrix, trimmed, camera, width, 220, 245, 250, alpha * .95f, age);

        // a bright bead runs ahead while the thread is still shooting out
        if (grow < 1 || entity.isEndMoving(partialTick)) {
            Vec3 tip = trimmed.points().getLast();
            Quaternionf orientation = entityRenderDispatcher.cameraOrientation();
            Vector3f right = new Vector3f(1, 0, 0).rotate(orientation).mul(width * 4);
            Vector3f up = new Vector3f(0, 1, 0).rotate(orientation).mul(width * 4);
            int a = (int) (230 * alpha);
            colorVertex(consumer, matrix, tip.x - right.x - up.x, tip.y - right.y - up.y, tip.z - right.z - up.z, 240, 255, 255, a);
            colorVertex(consumer, matrix, tip.x + right.x - up.x, tip.y + right.y - up.y, tip.z + right.z - up.z, 240, 255, 255, a);
            colorVertex(consumer, matrix, tip.x + right.x + up.x, tip.y + right.y + up.y, tip.z + right.z + up.z, 240, 255, 255, a);
            colorVertex(consumer, matrix, tip.x - right.x + up.x, tip.y - right.y + up.y, tip.z - right.z + up.z, 240, 255, 255, a);
        }
    }

    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Trimmed trimmed, Vec3 camera, float width,
                               int r, int g, int b, float alpha, float age) {
        List<Vec3> points = trimmed.points();
        float[] lengths = trimmed.lengths();
        int n = points.size();
        Vec3[] offsets = new Vec3[n];
        int[] alphas = new int[n];
        for (int i = 0; i < n; i++) {
            Vec3 p = points.get(i);
            Vec3 across = tangent(points, i).cross(camera.subtract(p));
            offsets[i] = across.lengthSqr() < 1e-10 ? Vec3.ZERO : across.normalize().scale(width);
            float shimmer = .7f + .3f * Mth.sin(lengths[i] * 7 - age * .8f);
            alphas[i] = (int) (255 * Mth.clamp(alpha * shimmer, 0, 1));
        }
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 c = points.get(i + 1);
            colorVertex(consumer, matrix, a.x - offsets[i].x, a.y - offsets[i].y, a.z - offsets[i].z, r, g, b, alphas[i]);
            colorVertex(consumer, matrix, a.x + offsets[i].x, a.y + offsets[i].y, a.z + offsets[i].z, r, g, b, alphas[i]);
            colorVertex(consumer, matrix, c.x + offsets[i + 1].x, c.y + offsets[i + 1].y, c.z + offsets[i + 1].z, r, g, b, alphas[i + 1]);
            colorVertex(consumer, matrix, c.x - offsets[i + 1].x, c.y - offsets[i + 1].y, c.z - offsets[i + 1].z, r, g, b, alphas[i + 1]);
        }
    }

    private static void colorVertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, int r, int g, int b, int a) {
        consumer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(TendrilEntity entity) {
        return VINE;
    }
}
