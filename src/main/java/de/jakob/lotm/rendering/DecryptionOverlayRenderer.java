package de.jakob.lotm.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.error.handler.TheftHandler;
import de.jakob.lotm.rendering.error.ErrorClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public class DecryptionOverlayRenderer {

    public static HashMap<UUID, LivingEntity> entitiesLookedAtByPlayerWithActiveDecryption = new HashMap<>();

    private static final ResourceLocation VIGNETTE =
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/decryption_overlay.png");

    private static final int ACCENT = 0xFF5CFF68;
    private static final int ACCENT_DIM = 0xFF2E8A3A;
    private static final int TEXT = 0xFFE4F5E6;
    private static final int TEXT_DIM = 0xFF8FB894;
    private static final int PANEL_TOP = 0xE00C1610;
    private static final int PANEL_BOTTOM = 0xE0060A07;
    private static final int HEALTH_LOW = 0xFFB0102A;
    private static final int HEALTH_HIGH = 0xFFE43FA3;
    private static final String CIPHER = "#%&@$*+=?<>/\\0123456789ABCDEF";

    private static final int SLOT = 18;

    private static int lastTargetId = -1;
    private static float revealStart = 0f;
    private static float trailHealth = -1f;

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "decryption_overlay"),
                DecryptionOverlayRenderer::renderOverlay);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) {
            removeShader(mc);
            return;
        }

    }

    private static void applyShader(Minecraft mc, String shaderName) {
        if (mc.gameRenderer.currentEffect() == null ||
                !mc.gameRenderer.currentEffect().getName().equals(shaderName)) {
            try {
                mc.gameRenderer.loadEffect(
                        ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "shaders/post/" + shaderName + ".json")
                );
            } catch (Exception ignored) {
            }
        }
    }

    private static void removeShader(Minecraft mc) {
        if (mc.gameRenderer.currentEffect() != null) {
            mc.gameRenderer.shutdownEffect();
        }
    }

    private static void renderOverlay(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!entitiesLookedAtByPlayerWithActiveDecryption.containsKey(mc.player.getUUID())) {
            lastTargetId = -1;
            trailHealth = -1f;
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float time = HudTheme.time(mc, dt);

        renderVignette(g, screenWidth, screenHeight, time);

        LivingEntity entity = entitiesLookedAtByPlayerWithActiveDecryption.get(mc.player.getUUID());
        if (entity == null) {
            lastTargetId = -1;
            trailHealth = -1f;
            return;
        }

        if (entity.getId() != lastTargetId) {
            lastTargetId = entity.getId();
            revealStart = time;
            trailHealth = entity.getHealth();
        }

        Font font = mc.font;
        boolean showDecryption = ErrorClientState.decryptionTarget == entity.getId()
                && mc.level.getGameTime() - ErrorClientState.decryptionReceived <= 20;
        List<String> lines = showDecryption ? ErrorClientState.decryptionLines : List.of();

        int width = Math.max(190, Math.min(280, screenWidth / 3));
        int x = screenWidth / 2 - width / 2;
        int y = 10;
        int height = 40 + (showDecryption ? 18 + lines.size() * 10 + 2 : 0);

        drawPanel(g, x, y, width, height, time);

        String name = entity.getName().getString();
        String shown = scramble(name, (time - revealStart) / (6f + name.length() * 0.8f), time);
        g.drawString(font, "◆", x + 7, y + 6, ACCENT, false);
        g.drawString(font, shown, x + 17, y + 6, TEXT, true);
        String dist = String.format("%.1fm", mc.player.distanceTo(entity));
        g.drawString(font, dist, x + width - 7 - font.width(dist), y + 6, TEXT_DIM, false);

        renderHealthBar(g, font, entity, x + 7, y + 19, width - 14, 13, dt);

        if (showDecryption) {
            int sy = y + 38;
            for (int dx = x + 6; dx < x + width - 6; dx += 4) {
                g.fill(dx, sy, dx + 2, sy + 1, HudTheme.withAlpha(ACCENT_DIM, 0.7f));
            }
            renderDecryptedInformation(g, font, lines, x + 7, sy + 5, width - 14, time);
        }

        renderScanline(g, x, y, width, height, time);
        renderStealableItems(g, font, entity, screenWidth / 2, y + height + 4, width);
    }

    private static void renderVignette(GuiGraphics g, int screenWidth, int screenHeight, float time) {
        float a = 0.7f + 0.3f * HudTheme.pulse(time, 0.06f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(0.85f, 1f, 0.88f, a);
        g.blit(VIGNETTE, 0, 0, screenWidth, screenHeight, 0, 0, 128, 96, 128, 96);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h, float time) {
        HudWidgets.drawGradientV(g, x, y, w, h, PANEL_TOP, PANEL_BOTTOM);

        int rim = HudTheme.withAlpha(ACCENT, 0.25f);
        g.fill(x, y, x + w, y + 1, rim);
        g.fill(x, y + h - 1, x + w, y + h, rim);
        g.fill(x, y + 1, x + 1, y + h - 1, rim);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, rim);

        int len = 7;
        int o = Math.round(HudTheme.pulse(time, 0.12f));
        int c = ACCENT;
        corner(g, x - o, y - o, len, 1, 1, c);
        corner(g, x + w + o, y - o, len, -1, 1, c);
        corner(g, x - o, y + h + o, len, 1, -1, c);
        corner(g, x + w + o, y + h + o, len, -1, -1, c);
    }

    private static void corner(GuiGraphics g, int x, int y, int len, int dx, int dy, int color) {
        int x0 = dx > 0 ? x : x - len;
        int y0 = dy > 0 ? y : y - 2;
        g.fill(x0, y0, x0 + len, y0 + 2, color);
        int vx = dx > 0 ? x : x - 2;
        int vy = dy > 0 ? y : y - len;
        g.fill(vx, vy, vx + 2, vy + len, color);
    }

    private static void renderScanline(GuiGraphics g, int x, int y, int w, int h, float time) {
        float t = HudTheme.frac(time * 0.015f);
        int sy = y + 1 + (int) (t * (h - 2));
        float fade = (float) Math.sin(t * Math.PI);
        g.fill(x + 1, sy, x + w - 1, sy + 1, HudTheme.withAlpha(ACCENT, 0.18f * fade));
        if (sy - 1 > y) g.fill(x + 1, sy - 1, x + w - 1, sy, HudTheme.withAlpha(ACCENT, 0.07f * fade));
    }

    private static String scramble(String text, float progress, float time) {
        if (progress >= 1f) return text;
        int revealed = (int) (Math.max(0f, progress) * text.length());
        StringBuilder sb = new StringBuilder(text.length());
        long tick = (long) (time / 2f);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (i < revealed || ch == ' ') {
                sb.append(ch);
            } else {
                long h = (tick * 31 + i) * 0x9E3779B97F4A7C15L;
                sb.append(CIPHER.charAt((int) ((h >>> 33) % CIPHER.length())));
            }
        }
        return sb.toString();
    }

    private static void renderHealthBar(GuiGraphics g, Font font, LivingEntity entity, int x, int y, int w, int h,
                                        DeltaTracker dt) {
        float max = Math.max(1f, entity.getMaxHealth());
        float health = Math.max(0f, entity.getHealth());
        if (trailHealth < health) {
            trailHealth = health;
        } else {
            trailHealth += (health - trailHealth) * HudTheme.smoothFactor(dt.getRealtimeDeltaTicks(), 0.92f);
        }

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, HudTheme.withAlpha(ACCENT_DIM, 0.6f));
        g.fill(x, y, x + w, y + h, 0xCC050806);

        int trailW = Math.round(w * HudTheme.clamp01(trailHealth / max));
        int fillW = Math.round(w * HudTheme.clamp01(health / max));
        if (trailW > fillW) {
            g.fill(x + fillW, y, x + trailW, y + h, 0xAAFFB8C8);
        }
        if (fillW > 0) {
            HudWidgets.drawGradientH(g, x, y, fillW, h, HEALTH_LOW, HEALTH_HIGH);
            g.fill(x, y, x + fillW, y + 1, 0x55FFFFFF);
            g.fill(x, y + h - 2, x + fillW, y + h, 0x33000000);
        }

        for (int i = 1; i < 10; i++) {
            int tx = x + w * i / 10;
            g.fill(tx, y + h - 3, tx + 1, y + h, 0x44000000);
        }

        String text = "❤ " + formatNumber(health) + " / " + formatNumber(max);
        g.drawString(font, text, x + 4, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
        String pct = Math.round(100f * health / max) + "%";
        g.drawString(font, pct, x + w - 4 - font.width(pct), y + (h - font.lineHeight) / 2 + 1, 0xDDFFFFFF, true);
    }

    private static void renderDecryptedInformation(GuiGraphics g, Font font, List<String> lines,
                                                   int x, int y, int w, float time) {
        int level = ErrorClientState.decryptionLevel;
        float fraction = level >= 5 ? 1f : ErrorClientState.decryptionFraction;

        g.drawString(font, "DECRYPTION", x, y, TEXT_DIM, false);
        String count = level + "/5";
        int countX = x + w - font.width(count);
        g.drawString(font, count, countX, y, level >= 5 ? ACCENT : TEXT, false);

        int pipsX = x + font.width("DECRYPTION") + 6;
        int pipsW = countX - 6 - pipsX;
        int gap = 2;
        int pipW = (pipsW - gap * 4) / 5;
        float glow = HudTheme.pulse(time, 0.2f);
        for (int i = 0; i < 5; i++) {
            int px = pipsX + i * (pipW + gap);
            int py = y + 2;
            g.fill(px, py, px + pipW, py + 4, 0xFF13261A);
            if (i < level) {
                g.fill(px, py, px + pipW, py + 4, ACCENT);
                g.fill(px, py, px + pipW, py + 1, 0x88FFFFFF);
            } else if (i == level) {
                int fw = Math.round(pipW * HudTheme.clamp01(fraction));
                g.fill(px, py, px + fw, py + 4, HudTheme.withAlpha(ACCENT, 0.5f + 0.4f * glow));
                g.fill(px, py, px + pipW, py + 1, HudTheme.withAlpha(ACCENT, 0.3f));
            }
        }

        int lineY = y + 13;
        for (String line : lines) {
            g.drawString(font, "›", x + 1, lineY, ACCENT, false);
            g.drawString(font, line, x + 9, lineY, TEXT, false);
            lineY += 10;
        }
    }

    private static void renderStealableItems(GuiGraphics g, Font font, LivingEntity entity, int centerX, int y, int maxWidth) {
        List<Item> stealableItems = TheftHandler.getStealableItemsForEntity(entity);
        if (stealableItems.isEmpty()) {
            return;
        }

        String label = "STEALABLE";
        g.drawString(font, label, centerX - font.width(label) / 2, y, TEXT_DIM, true);
        y += 11;

        int perRow = Math.max(1, Math.min(stealableItems.size(), maxWidth / (SLOT + 2)));
        for (int i = 0; i < stealableItems.size(); i++) {
            int row = i / perRow;
            int col = i % perRow;
            int inRow = Math.min(perRow, stealableItems.size() - row * perRow);
            int rowW = inRow * (SLOT + 2) - 2;
            int sx = centerX - rowW / 2 + col * (SLOT + 2);
            int sy = y + row * (SLOT + 2);

            g.fill(sx, sy, sx + SLOT, sy + SLOT, HudTheme.withAlpha(ACCENT, 0.45f));
            g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0xDD08100B);
            g.renderItem(new ItemStack(stealableItems.get(i)), sx + 1, sy + 1);
        }
    }

    private static String formatNumber(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    public static void clearCache() {
        entitiesLookedAtByPlayerWithActiveDecryption.clear();
    }
}
