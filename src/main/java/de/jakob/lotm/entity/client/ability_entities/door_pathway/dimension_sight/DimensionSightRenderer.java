package de.jakob.lotm.entity.client.ability_entities.door_pathway.dimension_sight;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.jakob.lotm.entity.custom.ability_entities.door_pathway.DimensionSightEntity;
import de.jakob.lotm.network.packets.toClient.SyncDimensionSightPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.*;

public class DimensionSightRenderer extends EntityRenderer<DimensionSightEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float RADIUS = 1.6f;
    private static final float SCALE = 0.11f;
    private static final int SEGMENTS = 64;
    private static final float R = 0.63f, G = 0.89f, B = 0.98f;

    private final Map<DimensionSightEntity, View> views = new WeakHashMap<>();

    private static final class View {
        int version = -1;
        BlockPos center;
        final Map<Integer, Shown> shown = new HashMap<>();
    }

    private static final class Shown {
        final Entity entity;
        Vec3 prev;
        Vec3 cur;

        Shown(Entity entity, Vec3 at) {
            this.entity = entity;
            this.prev = at;
            this.cur = at;
        }
    }

    public DimensionSightRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(DimensionSightEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(DimensionSightEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        float open = Math.min(1f, age / 10f);

        poseStack.pushPose();
        poseStack.translate(0, DimensionSightEntity.HALF, 0);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.scale(open, open, open);
        renderVeil(poseStack, buffer, age);
        poseStack.popPose();

        View view = views.computeIfAbsent(entity, e -> new View());
        sync(entity, view);

        poseStack.pushPose();
        poseStack.translate(0, -0.3, 0);
        poseStack.scale(SCALE * open, SCALE * open, SCALE * open);
        renderTerrain(entity, poseStack, buffer);
        renderSighted(view, partialTick, poseStack, buffer);
        poseStack.popPose();

        poseStack.popPose();
    }

    private void renderVeil(PoseStack poseStack, MultiBufferSource buffer, float age) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f pose = poseStack.last().pose();
        float pulse = 0.5f + 0.5f * Mth.sin(age * 0.12f);

        for (int i = 0; i < SEGMENTS; i++) {
            float a1 = i * Mth.TWO_PI / SEGMENTS;
            float a2 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1), c2 = Mth.cos(a2), s2 = Mth.sin(a2);

            ring(consumer, pose, c1, s1, c2, s2, RADIUS, RADIUS + 0.07f, 0.85f);
            ring(consumer, pose, c1, s1, c2, s2, RADIUS + 0.07f, RADIUS + 0.22f, 0.25f * pulse);
            float inner = RADIUS * (0.9f + 0.04f * Mth.sin(age * 0.2f + a1 * 6));
            ring(consumer, pose, c1, s1, c2, s2, inner - 0.02f, inner, 0.3f);
        }
    }

    private static void ring(VertexConsumer consumer, Matrix4f pose, float c1, float s1, float c2, float s2,
                             float inner, float outer, float alpha) {
        quad(consumer, pose, c1 * inner, s1 * inner, c2 * inner, s2 * inner, c2 * outer, s2 * outer, c1 * outer, s1 * outer, alpha);
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose,
                             float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, float alpha) {
        consumer.addVertex(pose, x1, y1, 0).setColor(R, G, B, alpha);
        consumer.addVertex(pose, x2, y2, 0).setColor(R, G, B, alpha);
        consumer.addVertex(pose, x3, y3, 0).setColor(R, G, B, alpha);
        consumer.addVertex(pose, x4, y4, 0).setColor(R, G, B, alpha);

        consumer.addVertex(pose, x4, y4, 0).setColor(R, G, B, alpha);
        consumer.addVertex(pose, x3, y3, 0).setColor(R, G, B, alpha);
        consumer.addVertex(pose, x2, y2, 0).setColor(R, G, B, alpha);
        consumer.addVertex(pose, x1, y1, 0).setColor(R, G, B, alpha);
    }

    private void renderTerrain(DimensionSightEntity entity, PoseStack poseStack, MultiBufferSource buffer) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        int[] offsets = entity.offsets();
        BlockState[] states = entity.states();
        for (int i = 0; i < offsets.length; i++) {
            int packed = offsets[i];
            int dx = (packed & 127) - 64;
            int dy = (packed >> 7 & 127) - 64;
            int dz = (packed >> 14 & 127) - 64;

            BlockState state = states[i];
            if (state.is(Blocks.WATER)) state = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            else if (state.is(Blocks.LAVA)) state = Blocks.MAGMA_BLOCK.defaultBlockState();

            poseStack.pushPose();
            poseStack.translate(dx - 0.5, dy, dz - 0.5);
            blocks.renderSingleBlock(state, poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    private void renderSighted(View view, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        if (view.shown.isEmpty()) return;
        boolean shadows = Minecraft.getInstance().options.entityShadows().get();
        entityRenderDispatcher.setRenderShadow(false);
        Iterator<Shown> it = view.shown.values().iterator();
        while (it.hasNext()) {
            Shown shown = it.next();
            Vec3 at = shown.prev.lerp(shown.cur, partialTick);
            try {
                entityRenderDispatcher.render(shown.entity, at.x, at.y, at.z, 0, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
            } catch (RuntimeException e) {
                it.remove();
            }
        }
        entityRenderDispatcher.setRenderShadow(shadows);
    }

    private static void sync(DimensionSightEntity veil, View view) {
        if (view.version == veil.version()) return;
        view.version = veil.version();

        BlockPos center = veil.center();
        if (view.center != null && !view.center.equals(center)) {
            Vec3 shift = Vec3.atLowerCornerOf(view.center.subtract(center));
            for (Shown shown : view.shown.values()) {
                shown.prev = shown.prev.add(shift);
                shown.cur = shown.cur.add(shift);
            }
        }
        view.center = center;

        Set<Integer> seen = new HashSet<>();
        for (SyncDimensionSightPacket.Sighted sighted : veil.sighted()) {
            Vec3 at = new Vec3(sighted.x(), sighted.y(), sighted.z());
            Shown shown = view.shown.get(sighted.id());
            boolean first = shown == null;
            if (first) {
                Entity dummy = create(veil, sighted);
                if (dummy == null) continue;
                shown = new Shown(dummy, at);
                view.shown.put(sighted.id(), shown);
            } else {
                if (sighted.data() != null) load(shown.entity, sighted.data());
                shown.prev = shown.cur;
                shown.cur = at;
            }
            seen.add(sighted.id());
            pose(veil, shown, sighted, first);
        }
        view.shown.keySet().retainAll(seen);
    }

    private static Entity create(DimensionSightEntity veil, SyncDimensionSightPacket.Sighted sighted) {
        if (sighted.player() != null) {
            return veil.level() instanceof ClientLevel level ? new RemotePlayer(level, new GameProfile(sighted.player(), sighted.name())) : null;
        }
        Entity entity = BuiltInRegistries.ENTITY_TYPE.byId(sighted.type()).create(veil.level());
        if (entity != null && sighted.data() != null) load(entity, sighted.data());
        return entity;
    }

    private static void load(Entity entity, CompoundTag data) {
        try {
            entity.load(data);
        } catch (Exception ignored) {
        }
    }

    private static void pose(DimensionSightEntity veil, Shown shown, SyncDimensionSightPacket.Sighted sighted, boolean first) {
        Entity entity = shown.entity;
        entity.setPos(veil.getX(), veil.getY(), veil.getZ());
        entity.setOldPosAndRot();
        entity.yRotO = first ? sighted.body() : entity.getYRot();
        entity.setYRot(sighted.body());
        entity.xRotO = first ? sighted.pitch() : entity.getXRot();
        entity.setXRot(sighted.pitch());
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = first ? sighted.body() : living.yBodyRot;
            living.yBodyRot = sighted.body();
            living.yHeadRotO = first ? sighted.head() : living.yHeadRot;
            living.yHeadRot = sighted.head();
            float moved = (float) shown.cur.subtract(shown.prev).horizontalDistance();
            living.walkAnimation.update(Math.min(moved * 4f, 1f), 0.4f);
        }
        entity.tickCount++;
    }

    @Override
    public ResourceLocation getTextureLocation(DimensionSightEntity entity) {
        return TEXTURE;
    }
}
