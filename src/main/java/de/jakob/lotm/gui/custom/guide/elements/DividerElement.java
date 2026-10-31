package de.jakob.lotm.gui.custom.guide.elements;

import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;

import java.util.List;

public class DividerElement implements GuideElement {
    @Override
    public int spacingBefore() {
        return 6;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        return List.of(GuidePiece.of(GuideGraphics.DIVIDER_H + 2, (gg, x, y, w) -> gg.divider(x + w / 2, y + GuideGraphics.DIVIDER_H / 2)));
    }
}
