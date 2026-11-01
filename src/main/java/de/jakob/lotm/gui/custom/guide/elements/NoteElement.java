package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;

import java.util.List;

public class NoteElement implements GuideElement {
    private static final int PAD = 6;
    private final String text;
    private final String title;
    private final int color;

    public NoteElement(JsonObject json) {
        this.text = GsonHelper.getAsString(json, "text");
        String style = GsonHelper.getAsString(json, "style", "tip");
        this.color = switch (style) {
            case "warning" -> 0xFF9E2A1E;
            case "lore" -> 0xFF5E3A86;
            default -> 0xFF4F6E22;
        };
        this.title = GsonHelper.getAsString(json, "title", style.equals("lore") ? "" : "guide.lotmcraft.note." + style);
    }

    @Override
    public int spacingBefore() {
        return 7;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        List<FormattedCharSequence> lines = layout.font().split(GuideText.parse(text),
                (int) ((layout.width() - 2 * PAD) / GuideGraphics.TEXT_SCALE));
        Component heading = title.isEmpty() ? null : GuideText.parse(title).copy().withStyle(ChatFormatting.BOLD);
        int titleH = heading == null ? 0 : 9;
        int height = PAD + titleH + lines.size() * GuideGraphics.LINE_H + PAD - 2;
        int fill = (color & 0x00FFFFFF) | 0x1C000000;
        return List.of(GuidePiece.of(height, (gg, x, y, w) -> {
            gg.g.fill(x + 2, y + 2, x + w - 2, y + height - 2, fill);
            gg.tinted(color, () -> gg.nineSlice(GuideGraphics.NOTE, x, y, w, height, 5, 16));
            int ty = y + PAD;
            if (heading != null) {
                gg.text(heading, x + PAD, ty - 1, 0.75f, color);
                ty += titleH;
            }
            for (int l = 0; l < lines.size(); l++)
                gg.text(lines.get(l), x + PAD, ty + l * GuideGraphics.LINE_H, GuideGraphics.TEXT_SCALE, GuideGraphics.INK_TEXT);
        }));
    }
}
