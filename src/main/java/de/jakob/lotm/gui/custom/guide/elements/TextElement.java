package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

public class TextElement implements GuideElement {
    private final String text;
    private final boolean center;
    private final float scale;

    public TextElement(JsonObject json) {
        this.text = GsonHelper.getAsString(json, "text");
        this.center = "center".equals(GsonHelper.getAsString(json, "align", "left"));
        this.scale = GsonHelper.getAsFloat(json, "scale", GuideGraphics.TEXT_SCALE);
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        int lineH = Math.round(GuideGraphics.LINE_H * scale / GuideGraphics.TEXT_SCALE);
        List<GuidePiece> pieces = new ArrayList<>();
        for (FormattedCharSequence line : layout.font().split(GuideText.parse(text), (int) (layout.width() / scale))) {
            pieces.add(GuidePiece.of(lineH, (gg, x, y, w) -> {
                if (center) gg.centered(line, x + w / 2f, y, scale, GuideGraphics.INK_TEXT);
                else gg.text(line, x, y, scale, GuideGraphics.INK_TEXT);
            }));
        }
        return pieces;
    }
}
