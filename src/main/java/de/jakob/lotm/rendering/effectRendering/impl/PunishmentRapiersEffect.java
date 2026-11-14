package de.jakob.lotm.rendering.effectRendering.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.rendering.effectRendering.ActiveEffect;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.PunishmentRapierVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public class PunishmentRapiersEffect extends ActiveEffect {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID,
            "models/effect/punishment_rapier.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID,
            "textures/effect/punishment_rapier.png");
    private static List<float[]> vertices;
    private static final float MODEL_SCALE = .6f;
    private static final double BLADE_TIP = 30.5 / 16 * MODEL_SCALE;

    public PunishmentRapiersEffect(Location location, int duration, boolean infinite) {
        super(location, duration, infinite);
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> vertices = null);
    }

    private static List<float[]> geometry() {
        if (vertices != null) return vertices;
        List<float[]> loaded = new ArrayList<>();
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(MODEL)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonArray row = entry.getAsJsonArray();
                float[] vertex = new float[8];
                for (int i = 0; i < 8; i++) vertex[i] = row.get(i).getAsFloat();
                loaded.add(vertex);
            }
        } catch (Exception error) {
            LOTMCraft.LOGGER.error("Unable to load Punishment rapier model", error);
        }
        return vertices = loaded;
    }

    @Override
    protected void render(PoseStack stack, float tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || location.getLevel() != mc.level) { cancel(); return; }
        if (!(mc.level.getEntity((int) params[0]) instanceof LivingEntity caster)
                || !(mc.level.getEntity((int) params[1]) instanceof LivingEntity target)) return;
        if (!caster.isAlive() || !target.isAlive()) { cancel(); return; }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(caster.position()) > 96 * 96
                && camera.distanceToSqr(target.position()) > 96 * 96) return;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 center = caster.getPosition(partial);
        float yaw = Mth.rotLerp(partial, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        float age = params[2] + tick;
        int slot = (int) params[3];
        int count = Mth.clamp((int) params[4], 3, 9);
        List<Vec3[]> strikes = new ArrayList<>();
        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int light = LevelRenderer.getLightColor(mc.level, caster.blockPosition());
        for (int i = 0; i < count; i++) {
            // Fixed slots fill clockwise from the top as the caster advances.
            double angle = Math.toRadians(90 - i * 22.5);
            double bob = Math.sin((mc.level.getGameTime() + partial) * .06 + i * .6) * .035;
            Vec3 radial = right.scale(Math.cos(angle)).add(0, Math.sin(angle), 0);
            Vec3 rest = center.add(forward.scale(-.4))
                    .add(0, 1.8 + bob, 0).add(radial.scale(.6));
            Vec3 point = rest;
            Vec3 direction = radial;
            Quaternionf orientation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                    new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
            if (i == slot && age >= 0 && age < PunishmentRapierVisuals.STRIKE_TICKS) {
                Vec3 hit = target.getPosition(partial).add(0, target.getBbHeight() * .55, 0);
                Vec3 attackDirection = hit.subtract(rest).normalize();
                // Tip reaches the target, then the complete rapier returns to its slot.
                Vec3 impact = hit.subtract(attackDirection.scale(BLADE_TIP));
                float amount = age < 4 ? smooth(age / 4) : age < 5 ? 1 : 1 - smooth((age - 5) / 7);
                point = rest.lerp(impact, amount);
                strikes.add(new Vec3[]{rest, point, hit, attackDirection});
                orientation.slerp(new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                        new Vector3f((float) attackDirection.x, (float) attackDirection.y,
                                (float) attackDirection.z)), amount);
            }
            stack.pushPose();
            stack.translate(point.x, point.y, point.z);
            stack.mulPose(orientation);
            stack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            for (float[] v : geometry()) {
                buffer.addVertex(stack.last().pose(), v[0], v[1], v[2]).setColor(255,255,255,255)
                        .setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(i == slot && age < 5 ? LightTexture.FULL_BRIGHT : light)
                        .setNormal(stack.last(), v[5], v[6], v[7]);
            }
            stack.popPose();
        }
        VertexConsumer glow = mc.renderBuffers().bufferSource().getBuffer(RenderType.lightning());
        float pulse = .85f + .15f * Mth.sin((mc.level.getGameTime() + partial) * .08f);
        Vec3 arcCenter = center.add(forward.scale(-.4)).add(0, 1.8, 0);
        // Thin brass arc connects only the occupied slots, preserving the gradual formation.
        Vec3 previous = null;
        int segments = (count - 1) * 8;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = Math.toRadians(90 - segment * 22.5 / 8);
            Vec3 point = arcCenter.add(right.scale(Math.cos(angle) * .6)).add(0, Math.sin(angle) * .6, 0);
            if (previous != null) ribbon(glow, stack, previous, point, camera, .012, .42f * pulse);
            previous = point;
        }
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(90 - i * 22.5);
            Vec3 node = arcCenter.add(right.scale(Math.cos(angle) * .6)).add(0, Math.sin(angle) * .6, 0);
            ribbon(glow, stack, node.add(0,-.035,0), node.add(0,.035,0), camera, .035, .7f * pulse);
        }
        // A low, warm corona and narrow rising rays suggest dawn without hiding the player.
        ring(glow, stack, center.add(0,.06,0), new Vec3(1,0,0), new Vec3(0,0,1), .48, camera, .018, .24f * pulse);
        boolean firstPerson = mc.player == caster && mc.options.getCameraType().isFirstPerson();
        if (!firstPerson) {
            Vec3 coronaCenter = center.add(0,.95,0);
            Vec3 horizontal = camera.subtract(coronaCenter).cross(new Vec3(0,1,0)).normalize();
            if (horizontal.lengthSqr() < .001) horizontal = right;
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI * 2 / 40, b = (i + 1) * Math.PI * 2 / 40;
                Vec3 outerA = coronaCenter.add(horizontal.scale(Math.cos(a) * .67)).add(0, Math.sin(a) * 1.05, 0);
                Vec3 outerB = coronaCenter.add(horizontal.scale(Math.cos(b) * .67)).add(0, Math.sin(b) * 1.05, 0);
                glowVertex(glow, stack, coronaCenter, .1f * pulse);
                glowVertex(glow, stack, outerA, 0);
                glowVertex(glow, stack, outerB, 0);
                glowVertex(glow, stack, coronaCenter, .1f * pulse);
            }
        }
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI * 2 / 12;
            double rise = ((mc.level.getGameTime() + partial) * .012 + i / 12.0) % 1;
            Vec3 ray = center.add(Math.cos(angle) * .42, .1 + rise * (firstPerson ? .65 : 1.25), Math.sin(angle) * .42);
            ribbon(glow, stack, ray, ray.add(0,.18,0), camera, .012,
                    (float) (Math.sin(rise * Math.PI) * .2 * pulse));
        }
        for (Vec3[] strike : strikes) {
            if (age < 5) {
                Vec3 tail = strike[1].lerp(strike[0], .22);
                ribbon(glow, stack, tail, strike[1], camera, .045, .45f);
                ribbon(glow, stack, tail, strike[1], camera, .013, .9f);
            }
            if (age >= 4 && age < 10) {
                Vec3 normal = strike[3];
                Vec3 u = normal.cross(new Vec3(0,1,0));
                if (u.lengthSqr() < .001) u = normal.cross(new Vec3(1,0,0));
                u = u.normalize();
                Vec3 v = normal.cross(u).normalize();
                float fade = 1 - (age - 4) / 6;
                Vec3 hit = strike[2].subtract(normal.scale(.08));
                ring(glow, stack, hit, u, v, .28 + (age - 4) * .025, camera, .018, fade * .8f);
                ribbon(glow, stack, hit.subtract(u.scale(.4)), hit.add(u.scale(.4)), camera, .015, fade);
                ribbon(glow, stack, hit.subtract(v.scale(.4)), hit.add(v.scale(.4)), camera, .015, fade);
            }
        }
    }

    private static void ring(VertexConsumer buffer, PoseStack stack, Vec3 center, Vec3 u, Vec3 v,
                             double radius, Vec3 camera, double width, float alpha) {
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40, b = (i + 1) * Math.PI * 2 / 40;
            Vec3 start = center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            Vec3 end = center.add(u.scale(Math.cos(b) * radius)).add(v.scale(Math.sin(b) * radius));
            ribbon(buffer, stack, start, end, camera, width, alpha);
        }
    }

    private static void ribbon(VertexConsumer buffer, PoseStack stack, Vec3 start, Vec3 end,
                               Vec3 camera, double width, float alpha) {
        Vec3 side = end.subtract(start).cross(camera.subtract(start));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width);
        for (Vec3 point : new Vec3[]{start.subtract(side), start.add(side), end.add(side), end.subtract(side)}) {
            glowVertex(buffer, stack, point, alpha);
        }
    }

    private static void glowVertex(VertexConsumer buffer, PoseStack stack, Vec3 point, float alpha) {
        buffer.addVertex(stack.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(1f, .79f, .38f, alpha);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
