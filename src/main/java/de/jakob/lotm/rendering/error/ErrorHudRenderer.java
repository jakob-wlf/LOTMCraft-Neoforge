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

    private static final ResourceLocation FLASH = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/error/observation_flash.png");

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

            double angle = Math.atan2(dPitch * 1.4, dYaw);
            float closeness = (float) Mth.clamp(1 - to.length() / 48, 0.2, 1);
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

    private static void renderTreasure(GuiGraphics graphics, Minecraft mc, int width, int height, float partial) {
        if (ErrorClientState.treasureTarget < 0) return;
        Entity entity = mc.level.getEntity(ErrorClientState.treasureTarget);
        if (entity == null) return;

        List<ItemStack> items = ErrorClientState.treasureItems;
        var font = mc.font;
        String name = ErrorClientState.treasureName;
        String value = String.format("%,d ◆", ErrorClientState.treasureValue);
        int valueColor = tierColor(ErrorClientState.treasureValue / Math.max(1, items.size()));

        int pad = 6;
        int slot = 18;
        int columns = Math.max(1, Math.min(8, items.size()));
        int rows = Math.max(1, (items.size() + 7) / 8);
        int headerHeight = 18;
        int panelWidth = Math.max(Math.max(120, font.width(name) + font.width(value) + pad * 2 + 12), columns * slot + pad * 2);
        int panelHeight = headerHeight + 4 + (items.isEmpty() ? 12 : rows * slot) + pad;

        float[] screen = ErrorWorldRenderer.project(entity.getPosition(partial).add(0, entity.getBbHeight() * 0.75, 0), width, height);
        int x;
        int y;
        if (screen != null) {
            x = (int) screen[0] + 28;
            y = (int) screen[1] - panelHeight / 2;
        } else {
            x = width - panelWidth - 8;
            y = height / 2 - panelHeight / 2;
        }
        x = Math.max(6, Math.min(width - panelWidth - 6, x));
        y = Math.max(6, Math.min(height - panelHeight - 6, y));

        // Leader line from the mob to the panel
        if (screen != null) {
            int sx = (int) screen[0];
            int sy = (int) screen[1];
            if (sx + 6 < x && sy > y + 2 && sy < y + panelHeight - 2) {
                hGradient(graphics, sx + 4, x - 1, sy, 0x20C8A040, 0xA0C8A040);
                graphics.fill(sx - 1, sy - 2, sx + 2, sy + 3, 0xFFE8C468);
                graphics.fill(sx - 2, sy - 1, sx + 3, sy + 2, 0xFFE8C468);
                graphics.fill(sx, sy, sx + 1, sy + 1, 0xFFFFF4D0);
            }
        }

        int x2 = x + panelWidth;
        int y2 = y + panelHeight;

        // Body: dark outer rim, gradient fill, gilded inner frame
        graphics.fill(x - 1, y - 1, x2 + 1, y2 + 1, 0xE0050409);
        graphics.fillGradient(x, y, x2, y2, 0xE81C1630, 0xE80A0814);
        graphics.fillGradient(x + 1, y + 1, x2 - 1, y + headerHeight, 0x38E0B050, 0x08E0B050);
        graphics.fill(x, y, x2, y + 1, 0xFFE8C468);
        graphics.fill(x, y2 - 1, x2, y2, 0xFF7A5C22);
        graphics.fillGradient(x, y + 1, x + 1, y2 - 1, 0xFFE8C468, 0xFF7A5C22);
        graphics.fillGradient(x2 - 1, y + 1, x2, y2 - 1, 0xFFE8C468, 0xFF7A5C22);

        // Corner studs
        int stud = 0xFFF4D888;
        graphics.fill(x - 2, y - 2, x + 2, y + 2, stud);
        graphics.fill(x2 - 2, y - 2, x2 + 2, y + 2, stud);
        graphics.fill(x - 2, y2 - 2, x + 2, y2 + 2, 0xFFB08A3A);
        graphics.fill(x2 - 2, y2 - 2, x2 + 2, y2 + 2, 0xFFB08A3A);

        // Header
        graphics.drawString(font, name, x + pad, y + 5, 0xFFF4EAD0, true);
        graphics.drawString(font, value, x2 - pad - font.width(value), y + 5, valueColor, true);

        // Divider fading out towards the edges
        int mid = x + panelWidth / 2;
        hGradient(graphics, x + pad, mid, y + headerHeight, 0x00C8A040, 0xC0C8A040);
        hGradient(graphics, mid, x2 - pad, y + headerHeight, 0xC0C8A040, 0x00C8A040);

        int gridY = y + headerHeight + 4;
        if (items.isEmpty()) {
            graphics.drawCenteredString(font, "—", mid, gridY + 1, 0xFF7A7264);
            return;
        }

        int gridX = x + (panelWidth - columns * slot) / 2;
        for (int i = 0; i < items.size(); i++) {
            int sx = gridX + (i % 8) * slot;
            int sy = gridY + (i / 8) * slot;
            int itemValue = i < ErrorClientState.treasureItemValues.size() ? ErrorClientState.treasureItemValues.get(i) : 0;
            drawSlot(graphics, sx, sy, itemValue);
            graphics.renderItem(items.get(i), sx + 1, sy + 1);
            graphics.renderItemDecorations(font, items.get(i), sx + 1, sy + 1);
        }
    }

    private static int tierColor(int value) {
        if (value >= 300) return 0xFFFFC94A;
        if (value >= 60) return 0xFF9CC4FF;
        return 0xFFD8CCB0;
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y, int value) {
        // Recessed slot: dark top-left, light bottom-right
        graphics.fill(x, y, x + 18, y + 18, 0x90000000);
        graphics.fill(x, y, x + 17, y + 1, 0xA0000000);
        graphics.fill(x, y, x + 1, y + 17, 0xA0000000);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, 0x30FFFFFF);
        graphics.fill(x + 17, y + 1, x + 18, y + 18, 0x30FFFFFF);

        if (value < 60) return;
        int border = value >= 300 ? 0xD0F0B040 : 0xB07AA8F0;
        int glow = value >= 300 ? 0x50E0A030 : 0x386A98E0;
        graphics.fillGradient(x + 1, y + 1, x + 17, y + 17, 0x00000000, glow);
        graphics.fill(x, y, x + 18, y + 1, border);
        graphics.fill(x, y + 17, x + 18, y + 18, border);
        graphics.fill(x, y + 1, x + 1, y + 17, border);
        graphics.fill(x + 17, y + 1, x + 18, y + 17, border);
    }

    private static void hGradient(GuiGraphics graphics, int x1, int x2, int y, int from, int to) {
        int w = x2 - x1;
        if (w <= 0) return;
        for (int i = 0; i < w; i++) {
            float t = i / (float) w;
            int a = (int) Mth.lerp(t, from >>> 24, to >>> 24);
            graphics.fill(x1 + i, y, x1 + i + 1, y + 1, (a << 24) | (from & 0xFFFFFF));
        }
    }

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
