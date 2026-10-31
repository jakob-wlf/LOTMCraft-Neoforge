package de.jakob.lotm.rendering.error;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * Screen space visuals of the Error pathway.
 */
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class ErrorHudRenderer {

    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");

    private ErrorHudRenderer() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "error_pathway_overlay"), ErrorHudRenderer::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        long now = mc.level.getGameTime();
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if (now - ErrorClientState.valuablesReceived < 80 || !shownHints.isEmpty()) renderValuables(graphics, mc.player, width, height, now, partial);
        if (now - ErrorClientState.threatsReceived < 40) renderThreats(graphics, width, now);
        if (ErrorClientFlags.treasureHunterActive && now - ErrorClientState.treasureReceived < 20) renderTreasure(graphics, mc, width, height, partial);
    }

    // ==================== VALUABLES ====================

    private static final ResourceLocation FLASH = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/error/observation_flash.png");

    /** A valuable as it is currently drawn; fades in and out and glides instead of jumping between server updates. */
    private static final class ShownHint {
        Vec3 pos;
        float strength;
        float alpha;
        boolean alive;
        final float phase;

        ShownHint(Vec3 pos, float strength, float phase) {
            this.pos = pos;
            this.strength = strength;
            this.phase = phase;
        }
    }

    private static final List<ShownHint> shownHints = new java.util.ArrayList<>();
    private static long lastHintFrame = -1;
    private static long lastHintSync = -1;

    private static void updateShownHints(long now, float partial) {
        if (lastHintSync != ErrorClientState.valuablesReceived) {
            lastHintSync = ErrorClientState.valuablesReceived;
            shownHints.forEach(h -> h.alive = false);
            for (ErrorClientState.ValuableHint hint : ErrorClientState.valuables) {
                ShownHint match = null;
                for (ShownHint shown : shownHints) {
                    if (shown.pos.distanceToSqr(hint.pos()) < 9 && (match == null || shown.pos.distanceToSqr(hint.pos()) < match.pos.distanceToSqr(hint.pos()))) {
                        match = shown;
                    }
                }
                if (match == null) {
                    match = new ShownHint(hint.pos(), hint.strength(), (float) (Math.random() * Math.PI * 2));
                    shownHints.add(match);
                }
                match.alive = true;
                match.strength = hint.strength();
                match.pos = match.pos.lerp(hint.pos(), 0.5);
            }
        }

        // stale data: the passive stopped sending, everything fades
        boolean stale = now - ErrorClientState.valuablesReceived > 40;
        float frame = now + partial;
        float dt = lastHintFrame < 0 ? 0 : Math.min(5, frame - lastHintFrame);
        lastHintFrame = (long) frame;
        for (ShownHint hint : shownHints) {
            float target = hint.alive && !stale ? 1f : 0f;
            hint.alpha += (target - hint.alpha) * Math.min(1f, dt * (target > hint.alpha ? 0.12f : 0.06f));
        }
        shownHints.removeIf(h -> !h.alive && h.alpha < 0.01f);
    }

    /**
     * A soft golden glow with light streaks at the edge of the screen in the direction of each valuable – never on the
     * thing itself. Overlapping glows add up, so a direction with many valuables shines brighter.
     */
    private static void renderValuables(GuiGraphics graphics, Player player, int width, int height, long now, float partial) {
        updateShownHints(now, partial);
        if (shownHints.isEmpty()) return;

        Vec3 eye = player.getEyePosition(partial);
        float yaw = player.getViewYRot(partial);
        float pitch = player.getViewXRot(partial);
        float time = now + partial;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);

        for (ShownHint hint : shownHints) {
            Vec3 to = hint.pos.subtract(eye);
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            float targetYaw = (float) (Math.toDegrees(Math.atan2(-to.x, to.z)));
            float targetPitch = (float) (-Math.toDegrees(Math.atan2(to.y, horizontal)));
            float dYaw = Mth.wrapDegrees(targetYaw - yaw);
            float dPitch = Mth.clamp(targetPitch - pitch, -60, 60);
            if (Math.abs(dYaw) < 1 && Math.abs(dPitch) < 1) dYaw = 1;

            // direction on screen: right is positive yaw, down is positive pitch
            double angle = Math.atan2(dPitch * 1.4, dYaw);
            float closeness = (float) Mth.clamp(1 - to.length() / 48, 0.2, 1);
            // irregular shimmer, like light catching on gold
            float shimmer = 0.62f + 0.24f * Mth.sin(time * 0.21f + hint.phase) + 0.14f * Mth.sin(time * 0.53f + hint.phase * 2.3f);
            float alpha = Mth.clamp(hint.alpha * shimmer * (0.35f + 0.65f * hint.strength) * (0.45f + 0.55f * closeness), 0, 1);
            if (alpha <= 0.02f) continue;

            float size = 0.75f + 0.45f * closeness * hint.strength;
            glowAtEdge(graphics, width, height, angle, alpha, size);
        }

        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void glowAtEdge(GuiGraphics graphics, int width, int height, double angle, float alpha, float size) {
        double cx = width / 2.0, cy = height / 2.0;
        double dx = Math.cos(angle), dy = Math.sin(angle);
        double scale = Math.min(Math.abs(dx) < 1e-4 ? Double.MAX_VALUE : cx / Math.abs(dx), Math.abs(dy) < 1e-4 ? Double.MAX_VALUE : cy / Math.abs(dy));
        float ex = (float) (cx + dx * scale);
        float ey = (float) (cy + dy * scale);

        // the texture glows from its left edge towards the right; turn it so it shines into the screen
        boolean sideEdge = Math.abs(dx) * cy >= Math.abs(dy) * cx;
        float inward = sideEdge ? (dx > 0 ? (float) Math.PI : 0f) : (dy > 0 ? (float) (-Math.PI / 2) : (float) (Math.PI / 2));

        int w = (int) (64 * size);
        int h = (int) (128 * size);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(ex, ey, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(inward));
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        graphics.blit(FLASH, 0, -h / 2, w, h, 0, 0, 64, 128, 64, 128);
        pose.popPose();
    }

    // ==================== THREATS (Seq 3) ====================

    private static void renderThreats(GuiGraphics graphics, int width, long now) {
        List<ErrorClientState.Threat> threats = ErrorClientState.threats;
        if (threats.isEmpty()) return;

        int size = 26;
        int x = width / 2 - (threats.size() * (size + 6)) / 2;
        int y = 34;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (ErrorClientState.Threat threat : threats) {
            float closeness = 1f - Math.max(0, Math.min(1, threat.distance()));
            float alpha = 0.06f + 0.94f * closeness * closeness;
            alpha *= 0.85f + 0.15f * (float) Math.sin(now * 0.2);

            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/icons/" + threat.pathway() + "_icon.png");
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            graphics.blit(icon, x, y, 0, 0, size, size, size, size);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            x += size + 6;
        }
        RenderSystem.disableBlend();
    }

    // ==================== TREASURE HUNTER ====================

    private static void renderTreasure(GuiGraphics graphics, Minecraft mc, int width, int height, float partial) {
        if (ErrorClientState.treasureTarget < 0) return;
        Entity entity = mc.level.getEntity(ErrorClientState.treasureTarget);
        if (entity == null) return;

        List<ItemStack> items = ErrorClientState.treasureItems;
        int columns = Math.max(1, Math.min(8, items.size()));
        int rows = Math.max(1, (items.size() + 7) / 8);
        int panelWidth = Math.max(110, columns * 18 + 10);
        int panelHeight = 28 + rows * 18 + 4;

        float[] screen = ErrorWorldRenderer.project(entity.getPosition(partial).add(0, entity.getBbHeight() * 0.75, 0), width, height);
        int x;
        int y;
        if (screen != null) {
            x = (int) screen[0] + 24;
            y = (int) screen[1] - panelHeight / 2;
        } else {
            x = width - panelWidth - 8;
            y = height / 2 - panelHeight / 2;
        }
        x = Math.max(4, Math.min(width - panelWidth - 4, x));
        y = Math.max(4, Math.min(height - panelHeight - 4, y));

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xC0100C1E);
        graphics.fill(x, y, x + panelWidth, y + 1, 0xFFC8A040);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFFC8A040);
        graphics.fill(x, y, x + 1, y + panelHeight, 0xFFC8A040);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, 0xFFC8A040);

        graphics.drawString(mc.font, ErrorClientState.treasureName, x + 5, y + 4, 0xFFF0E6C8, false);
        graphics.drawString(mc.font, "≈ " + ErrorClientState.treasureValue + " ◆", x + 5, y + 15, 0xFFE8C058, false);

        if (items.isEmpty()) {
            graphics.drawString(mc.font, "—", x + 5, y + 28, 0xFF908870, false);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            int ix = x + 5 + (i % 8) * 18;
            int iy = y + 27 + (i / 8) * 18;
            int value = i < ErrorClientState.treasureItemValues.size() ? ErrorClientState.treasureItemValues.get(i) : 0;
            if (value >= 300) graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x70E0A030);
            else if (value >= 60) graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x4080A0E0);
            graphics.renderItem(items.get(i), ix, iy);
            graphics.renderItemDecorations(mc.font, items.get(i), ix, iy);
        }
    }

    // ==================== HEALTH & HUNGER DECEIT ====================

    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = mc.level.getGameTime();

        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) && ErrorClientState.fakeHealthUntil > now && !mc.player.isCreative()) {
            event.setCanceled(true);
            renderFakeHearts(event.getGuiGraphics(), mc, now);
        } else if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL) && ErrorClientState.hungerUntil > now
                && ErrorClientState.hungerMode != 3 && !mc.player.isCreative()) {
            event.setCanceled(true);
            renderFakeFood(event.getGuiGraphics(), mc, now);
        }
    }

    private static void renderFakeHearts(GuiGraphics graphics, Minecraft mc, long now) {
        float max = mc.player.getMaxHealth();
        float shown = Math.max(0, ErrorClientState.fakeHealth * max);
        int hearts = Math.min(10, Mth.ceil(max / 2f));
        int left = graphics.guiWidth() / 2 - 91;
        int top = graphics.guiHeight() - mc.gui.leftHeight;

        for (int i = 0; i < hearts; i++) {
            int x = left + i * 8;
            int y = top + (shown <= 4 && (now + i) % 3 == 0 ? 1 : 0);
            graphics.blitSprite(HEART_CONTAINER, x, y, 9, 9);
            float value = shown - i * 2;
            if (value >= 2) graphics.blitSprite(HEART_FULL, x, y, 9, 9);
            else if (value >= 1) graphics.blitSprite(HEART_HALF, x, y, 9, 9);
        }
        mc.gui.leftHeight += 10;
    }

    private static void renderFakeFood(GuiGraphics graphics, Minecraft mc, long now) {
        int real = mc.player.getFoodData().getFoodLevel();
        long elapsed = now - ErrorClientState.hungerStart;
        int shown = switch (ErrorClientState.hungerMode) {
            case 0 -> (int) Math.max(0, real - elapsed / 60);
            case 1 -> (int) Math.max(real, 20 - elapsed / 1200);
            default -> 0;
        };

        int right = graphics.guiWidth() / 2 + 91;
        int top = graphics.guiHeight() - mc.gui.rightHeight;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            int y = top + (shown == 0 && (now + i) % 4 == 0 ? 1 : 0);
            graphics.blitSprite(FOOD_EMPTY, x, y, 9, 9);
            int value = shown - i * 2;
            if (value >= 2) graphics.blitSprite(FOOD_FULL, x, y, 9, 9);
            else if (value == 1) graphics.blitSprite(FOOD_HALF, x, y, 9, 9);
        }
        mc.gui.rightHeight += 10;
    }
}
