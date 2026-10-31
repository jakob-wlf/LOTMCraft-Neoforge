package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import java.util.List;

public class HeadingElement implements GuideElement {
    private final String text;

    public HeadingElement(JsonObject json) {
        this.text = GsonHelper.getAsString(json, "text");
    }

    @Override
    public int spacingBefore() {
        return 9;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        Component title = GuideText.parse(text).copy().withStyle(ChatFormatting.BOLD);
        return List.of(new GuidePiece() {
            @Override public int height() { return 14; }
            @Override public boolean keepWithNext() { return true; }

            @Override
            public void render(GuideGraphics gg, int x, int y, int w) {
                gg.g.fill(x, y + 3, x + 3, y + 6, 0xFF8A1C1C);
                gg.g.fill(x + 1, y + 2, x + 2, y + 7, 0xFF8A1C1C);
                gg.text(title, x + 6, y + 1, 0.85f, GuideGraphics.INK);
                for (int dx = 0; dx < w; dx++) {
                    int a = (int) (0x88 * (1f - (float) dx / w));
                    gg.g.fill(x + dx, y + 10, x + dx + 1, y + 11, (a << 24) | 0x805030);
                }
            }
        });
    }
}
