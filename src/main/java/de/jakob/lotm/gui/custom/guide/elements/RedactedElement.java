package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;

import java.util.List;

public class RedactedElement implements GuideElement {
    private static final int BAR_H = 5, BAR_GAP = 3;
    private final int lines;
    private final long seed;
    private final Component stamp;
    private final Component tooltip;

    public RedactedElement(JsonObject json) {
        this.lines = GsonHelper.getAsInt(json, "lines", 3);
        this.seed = GsonHelper.getAsLong(json, "seed", json.toString().hashCode());
        this.stamp = GuideText.parse(GsonHelper.getAsString(json, "stamp", "guide.lotmcraft.redacted"));
        this.tooltip = GuideText.parse(GsonHelper.getAsString(json, "tooltip", "guide.lotmcraft.redacted.tooltip"));
    }

    @Override
    public int spacingBefore() {
        return 6;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        int height = Math.max(22, lines * (BAR_H + BAR_GAP));
        return List.of(GuidePiece.of(height, (gg, x, y, w) -> {
            RandomSource random = RandomSource.create(seed);
            for (int i = 0; i < lines; i++) {
                int by = y + i * (BAR_H + BAR_GAP);
                int bw = i == lines - 1 ? w / 2 + random.nextInt(w / 3) : w - random.nextInt(14);
                int jitter = random.nextInt(3) - 1;
                gg.g.fill(x, by + 1, x + bw, by + BAR_H - 1, 0xFF15100A);
                gg.g.fill(x + 1, by + jitter, x + bw - 2 - random.nextInt(6), by + BAR_H + jitter, 0xF0221A10);
            }
            int cx = x + w / 2, cy = y + height / 2;
            gg.g.pose().pushPose();
            gg.g.pose().translate(cx, cy, 10);
            gg.g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-7));
            gg.blit(GuideGraphics.STAMP, -40, -10, 80, 20);
            gg.centered(stamp.copy().withStyle(ChatFormatting.BOLD), 0, -3.5f, 0.85f, 0xFFB0281E);
            gg.g.pose().popPose();
            gg.tooltip(x, y, w, height, List.of(tooltip.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)));
        }));
    }
}
