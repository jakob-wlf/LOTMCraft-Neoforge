package de.jakob.lotm.gui.custom.guide;

import net.minecraft.client.gui.Font;

import java.util.List;

public interface GuideElement {

    List<GuidePiece> layout(Layout layout);

    default int spacingBefore() {
        return 5;
    }

    default boolean isPageBreak() {
        return false;
    }

    record Layout(Font font, int width) {}
}
