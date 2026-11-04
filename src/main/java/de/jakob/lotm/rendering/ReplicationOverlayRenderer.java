package de.jakob.lotm.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.network.packets.toClient.ReplicationAnalysisPacket;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public class ReplicationOverlayRenderer {

    private static final int ACCENT = 0xFF8FF4FF;
    private static final int ACCENT_DEEP = 0xFF2A8FA6;
    private static final int GOLD = 0xFFFFD97A;
    private static final int SEALED = 0xFFE0606E;
    private static final int INK = 0xFFE6FBFF;
    private static final int INK_DIM = 0xFF7FAAB5;
    private static final int BODY_LEFT = 0xE00A1D25;
    private static final int BODY_RIGHT = 0xC0050D12;
    private static final int DISK = 0xF0081820;
    private static final int DISK_CORE = 0xFF03090C;
    private static final int NODE_OFF = 0xFF1C4652;
    private static final int GHOST_SEALED = 0xFF8C9AA0;

    private static final int WIDTH = 184;
    private static final int HEIGHT = 44;
    private static final int GAP = 8;
    private static final int MARGIN = 8;
    private static final int MEDALLION = 22;
    private static final int ICON = 20;
    private static final int RING_TICKS = 40;
    private static final int ORBIT_TICKS = 56;
    private static final int MAX_ENTRIES = 3;

    private static final float HOLD_ANALYZING = 140f;
    private static final float HOLD_REPLICATED = 80f;
    private static final float HOLD_SEALED = 60f;
    private static final float SLIDE_IN = 8f;
    private static final float FADE_OUT = 12f;

    private static final List<Entry> entries = new ArrayList<>();

    private static final class Entry {
        final String abilityId;
        final float born;
        int uses;
        int needed;
        int state = ReplicationAnalysisPacket.ANALYZING;
        float shown;
        float updated;
        float resolved = -1f;
        float y = Float.NaN;

        Entry(String abilityId, float born) {
            this.abilityId = abilityId;
            this.born = born;
        }

        float hold() {
            return switch (state) {
                case ReplicationAnalysisPacket.REPLICATED -> HOLD_REPLICATED;
                case ReplicationAnalysisPacket.SEALED -> HOLD_SEALED;
                default -> HOLD_ANALYZING;
            };
        }

        float target() {
            if (state == ReplicationAnalysisPacket.REPLICATED) return 1f;
            if (state == ReplicationAnalysisPacket.SEALED || needed <= 0) return 0f;
            return HudTheme.clamp01((float) uses / needed);
        }
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "replication_overlay"),
                ReplicationOverlayRenderer::render);
    }

    public static void update(ReplicationAnalysisPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = mc.level.getGameTime();

        Entry entry = null;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.abilityId.equals(packet.abilityId())) {
                entry = e;
                it.remove();
                break;
            }
        }
        if (entry == null) entry = new Entry(packet.abilityId(), now);

        if (packet.state() == ReplicationAnalysisPacket.ANALYZING) {
            entry.resolved = -1f;
        } else if (entry.state != packet.state() || entry.resolved < 0) {
            entry.resolved = now;
        }
        entry.state = packet.state();
        entry.uses = packet.uses();
        entry.needed = packet.needed();
        entry.updated = now;

        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
    }

    public static void clearCache() {
        entries.clear();
    }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            entries.clear();
            return;
        }
        if (entries.isEmpty() || mc.options.hideGui) return;

        float time = HudTheme.time(mc, dt);
        entries.removeIf(e -> time - e.updated > e.hold() || time < e.updated - 20f
                || LOTMCraft.abilityHandler.getById(e.abilityId) == null);
        if (entries.isEmpty()) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float slotSmooth = HudTheme.smoothFactor(dt.getRealtimeDeltaTicks(), 0.75f);
        float fillSmooth = HudTheme.smoothFactor(dt.getRealtimeDeltaTicks(), 0.86f);
        int top = Math.round(screenHeight * 0.28f);

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float targetY = top + i * (HEIGHT + GAP);
            e.y = Float.isNaN(e.y) ? targetY : e.y + (targetY - e.y) * slotSmooth;
            e.shown += (e.target() - e.shown) * fillSmooth;

            Ability ability = LOTMCraft.abilityHandler.getById(e.abilityId);
            renderCard(g, mc.font, e, ability, screenWidth, time);
        }
    }

    private static void renderCard(GuiGraphics g, Font font, Entry e, Ability ability, int screenWidth, float time) {
        float in = HudTheme.smoothstep((time - e.born) / SLIDE_IN);
        float out = HudTheme.clamp01((e.hold() - (time - e.updated)) / FADE_OUT);
        float alpha = Math.min(in, out);
        if (alpha <= 0.01f) return;

        int slide = Math.round((1f - in) * (WIDTH + MARGIN) + (1f - HudTheme.smoothstep(out)) * 30f);
        int x = screenWidth - WIDTH - MARGIN + slide;
        int y = Math.round(e.y);
        int color = stateColor(e, time);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        renderBody(g, e, x, y, color, time, alpha);

        int cx = x + MEDALLION;
        int cy = y + HEIGHT / 2;
        fillDisk(g, cx, cy, 21, HudTheme.scaleAlpha(DISK, alpha));
        fillDisk(g, cx, cy, 14, HudTheme.scaleAlpha(DISK_CORE, alpha));
        renderOrbit(g, e, cx, cy, color, time, alpha);
        renderRing(g, e, cx, cy, color, alpha);
        renderIcon(g, font, e, ability, cx, cy, color, time, alpha);
        renderShockwave(g, e, cx, cy, time, alpha);

        if (alpha > 0.05f) {
            renderText(g, font, e, ability, x, y, color, time, alpha);
        }

        RenderSystem.disableBlend();
    }

    private static int stateColor(Entry e, float time) {
        return switch (e.state) {
            case ReplicationAnalysisPacket.REPLICATED ->
                    HudTheme.interpolate(0xFFFFFFFF, GOLD, HudTheme.clamp01((time - e.resolved) / 10f));
            case ReplicationAnalysisPacket.SEALED -> SEALED;
            default -> ACCENT;
        };
    }

    private static void renderBody(GuiGraphics g, Entry e, int x, int y, int color, float time, float alpha) {
        int bx = x + MEDALLION;
        int bw = WIDTH - MEDALLION;
        HudWidgets.drawGradientH(g, bx, y, bw, HEIGHT,
                HudTheme.scaleAlpha(BODY_LEFT, alpha), HudTheme.scaleAlpha(BODY_RIGHT, alpha));

        HudWidgets.drawGradientH(g, bx, y, bw, 1,
                HudTheme.withAlpha(color, 0f), HudTheme.withAlpha(color, 0.7f * alpha));
        HudWidgets.drawGradientH(g, bx, y + HEIGHT - 1, bw, 1,
                HudTheme.withAlpha(color, 0f), HudTheme.withAlpha(color, 0.35f * alpha));

        float edge = 0.55f + 0.3f * HudTheme.pulse(time, 0.1f);
        g.fill(x + WIDTH - 2, y, x + WIDTH, y + HEIGHT, HudTheme.withAlpha(color, edge * alpha));

        int ky = y + HEIGHT / 2;
        int keyhole = HudTheme.withAlpha(color, 0.6f * alpha);
        g.fill(x + WIDTH - 7, ky - 3, x + WIDTH - 4, ky, keyhole);
        g.fill(x + WIDTH - 6, ky, x + WIDTH - 5, ky + 3, keyhole);

        if (e.state == ReplicationAnalysisPacket.REPLICATED && e.resolved >= 0) {
            float flash = 1f - HudTheme.clamp01((time - e.resolved) / 10f);
            if (flash > 0f) {
                g.fill(bx, y, x + WIDTH, y + HEIGHT, HudTheme.withAlpha(GOLD, 0.25f * flash * alpha));
            }
        }
    }

    private static void renderOrbit(GuiGraphics g, Entry e, int cx, int cy, int color, float time, float alpha) {
        boolean sealed = e.state == ReplicationAnalysisPacket.SEALED;
        float head = time * 4f;
        for (int i = 0; i < ORBIT_TICKS; i++) {
            float deg = i * 360f / ORBIT_TICKS;
            float a;
            if (sealed) {
                a = 0.12f;
            } else {
                float behind = ((head - deg) % 360f + 360f) % 360f;
                float trail = 1f - behind / 360f;
                a = 0.08f + 0.55f * trail * trail * trail;
            }
            tick(g, cx, cy, deg, 20, 21, 1, HudTheme.withAlpha(color, a * alpha));
        }
    }

    private static void renderRing(GuiGraphics g, Entry e, int cx, int cy, int color, float alpha) {
        boolean sealed = e.state == ReplicationAnalysisPacket.SEALED;
        float lit = e.shown * RING_TICKS;
        int full = (int) lit;
        for (int i = 0; i < RING_TICKS; i++) {
            float deg = i * 360f / RING_TICKS;
            int c;
            if (sealed) {
                if (i % 3 == 0) continue;
                c = HudTheme.withAlpha(SEALED, 0.35f * alpha);
            } else if (i < full) {
                float along = (float) i / RING_TICKS;
                c = HudTheme.withAlpha(HudTheme.interpolate(ACCENT_DEEP, color, 0.45f + 0.55f * along), alpha);
            } else if (i == full && lit < RING_TICKS) {
                c = HudTheme.withAlpha(0xFFFFFFFF, (0.25f + 0.75f * (lit - full)) * alpha);
            } else {
                c = HudTheme.withAlpha(ACCENT_DEEP, 0.28f * alpha);
            }
            tick(g, cx, cy, deg, 15, 18, 2, c);
        }
    }

    private static void renderIcon(GuiGraphics g, Font font, Entry e, Ability ability, int cx, int cy,
                                   int color, float time, float alpha) {
        ResourceLocation tex = ability.getTextureLocation();
        int ix = cx - ICON / 2;
        int iy = cy - ICON / 2;

        if (e.state == ReplicationAnalysisPacket.SEALED) {
            blitIcon(g, tex, ix, iy, HudTheme.withAlpha(GHOST_SEALED, 0.45f * alpha));
            if (alpha > 0.05f) {
                g.drawString(font, "✖", cx + 6, cy + 4, HudTheme.withAlpha(SEALED, alpha), true);
            }
            return;
        }

        float p = e.shown;
        float spread = (1f - p) * 4f;
        if (spread > 0.3f) {
            int d = Math.round(spread + (float) Math.sin(time * 0.2f) * 0.5f);
            int ghost = HudTheme.withAlpha(color, 0.18f * alpha);
            blitIcon(g, tex, ix - d, iy, ghost);
            blitIcon(g, tex, ix + d, iy, ghost);
        }
        blitIcon(g, tex, ix, iy, HudTheme.withAlpha(color, 0.38f * alpha));

        int revealed = Math.round(ICON * p);
        if (revealed > 0) {
            g.enableScissor(ix, iy + ICON - revealed, ix + ICON, iy + ICON);
            blitIcon(g, tex, ix, iy, HudTheme.withAlpha(0xFFFFFFFF, alpha));
            g.disableScissor();
        }

        if (e.state == ReplicationAnalysisPacket.ANALYZING && revealed < ICON) {
            int ly = iy + ICON - revealed;
            float flicker = 0.55f + 0.45f * HudTheme.pulse(time, 0.9f);
            g.fill(ix - 1, ly, ix + ICON + 1, ly + 1, HudTheme.withAlpha(0xFFFFFFFF, 0.75f * flicker * alpha));
            g.fill(ix - 3, ly, ix - 1, ly + 1, HudTheme.withAlpha(color, flicker * alpha));
            g.fill(ix + ICON + 1, ly, ix + ICON + 3, ly + 1, HudTheme.withAlpha(color, flicker * alpha));
        }

        if (e.state == ReplicationAnalysisPacket.REPLICATED && alpha > 0.05f) {
            g.drawString(font, "✦", cx + 7, cy + 4, HudTheme.withAlpha(GOLD, alpha), true);
        }
    }

    private static void renderShockwave(GuiGraphics g, Entry e, int cx, int cy, float time, float alpha) {
        if (e.state != ReplicationAnalysisPacket.REPLICATED || e.resolved < 0) return;
        float t = (time - e.resolved) / 16f;
        if (t < 0f || t >= 1f) return;

        int r = Math.round(21 + t * 16);
        int c = HudTheme.withAlpha(GOLD, (1f - t) * 0.8f * alpha);
        for (int i = 0; i < 64; i++) {
            tick(g, cx, cy, i * 360f / 64, r, r + 1, 1, c);
        }
    }

    private static void renderText(GuiGraphics g, Font font, Entry e, Ability ability, int x, int y,
                                   int color, float time, float alpha) {
        int tx = x + MEDALLION + 24;
        int rx = x + WIDTH - 10;
        boolean sealed = e.state == ReplicationAnalysisPacket.SEALED;

        String status = switch (e.state) {
            case ReplicationAnalysisPacket.REPLICATED -> I18n.get("overlay.lotmcraft.replicating.replicated");
            case ReplicationAnalysisPacket.SEALED -> I18n.get("overlay.lotmcraft.replicating.sealed");
            default -> I18n.get("overlay.lotmcraft.replicating.analyzing") + ".".repeat((int) (time / 8f) % 4);
        };
        drawSmall(g, font, status, tx, y + 7, HudTheme.withAlpha(color, 0.85f * alpha));

        if (!sealed) {
            String count = Math.min(e.uses, e.needed) + "/" + e.needed;
            drawSmall(g, font, count, rx - font.width(count) * 0.75f, y + 7, HudTheme.withAlpha(INK_DIM, alpha));
        }

        String pct = sealed ? "—" : Math.round(e.shown * 100f) + "%";
        int pctW = font.width(pct);
        g.drawString(font, pct, rx - pctW, y + 16, HudTheme.withAlpha(sealed ? SEALED : color, alpha), true);

        String name = ability.getName().getString();
        int avail = rx - tx - pctW - 6;
        if (font.width(name) > avail) {
            name = font.plainSubstrByWidth(name, Math.max(0, avail - font.width("…"))) + "…";
        }
        g.drawString(font, name, tx, y + 16, HudTheme.withAlpha(nameColor(ability), alpha), true);

        if (sealed) {
            String hint = I18n.get("overlay.lotmcraft.replicating.sealed_hint");
            int hintAvail = rx - tx;
            if (font.width(hint) * 0.75f > hintAvail) {
                hint = font.plainSubstrByWidth(hint, (int) (hintAvail / 0.75f) - font.width("…")) + "…";
            }
            drawSmall(g, font, hint, tx, y + 31, HudTheme.withAlpha(INK_DIM, alpha));
        } else {
            renderTrack(g, e, tx + 1, rx - 2, y + 33, color, time, alpha);
        }
    }

    private static void renderTrack(GuiGraphics g, Entry e, int x0, int x1, int ny, int color, float time, float alpha) {
        int n = Math.max(1, e.needed);
        int lit = Math.min(Math.max(e.uses, 0), n);

        int[] nx = new int[n];
        for (int i = 0; i < n; i++) {
            nx[i] = n == 1 ? (x0 + x1) / 2 : x0 + Math.round(i * (x1 - x0) / (float) (n - 1));
        }

        if (n > 1) {
            g.fill(x0, ny, x1 + 1, ny + 1, HudTheme.withAlpha(NODE_OFF, alpha));
            if (lit > 1) {
                g.fill(nx[0], ny, nx[lit - 1] + 1, ny + 1, HudTheme.withAlpha(color, 0.8f * alpha));
            }
        }

        float fresh = 1f - HudTheme.clamp01((time - e.updated) / 12f);
        for (int i = 0; i < n; i++) {
            int c;
            if (i < lit) {
                c = i == lit - 1 ? HudTheme.interpolate(color, 0xFFFFFFFF, fresh) : color;
            } else {
                c = NODE_OFF;
            }
            node(g, nx[i], ny, HudTheme.withAlpha(c, alpha));
        }
    }

    private static int nameColor(Ability ability) {
        String pathway = ability.getRequirements().keySet().stream().sorted().findFirst().orElse(null);
        if (pathway == null) return INK;
        return HudTheme.lighten(HudTheme.pathwayColor(pathway), 0.2f);
    }

    private static void blitIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int tint) {
        HudWidgets.setTint(g, tint);
        RenderSystem.enableBlend();
        g.blit(tex, x, y, 0f, 0f, ICON, ICON, ICON, ICON);
        HudWidgets.resetTint(g);
    }

    private static void drawSmall(GuiGraphics g, Font font, String text, float x, float y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void tick(GuiGraphics g, int cx, int cy, float degrees, int r0, int r1, int w, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        int left = -w / 2;
        g.fill(left, -r1, left + w, -r0, color);
        g.pose().popPose();
    }

    private static void node(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y - 1, x + 1, y, color);
        g.fill(x - 1, y, x + 2, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 2, color);
    }

    private static void fillDisk(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy < r; dy++) {
            float fy = dy + 0.5f;
            int half = Math.round((float) Math.sqrt(r * r - fy * fy));
            if (half > 0) g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }
}
