package de.jakob.lotm.gui.custom.guide.elements;

import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

final class Captions {
    static final int HEIGHT = 9;
    private static final float SCALE = 0.65f;

    private Captions() {}

    static void draw(GuideGraphics gg, Component caption, int cx, int y) {
        gg.centered(caption.copy().withStyle(ChatFormatting.ITALIC), cx, y + 1, SCALE, GuideGraphics.INK_SOFT);
    }

    static GuidePiece piece(Component caption) {
        return GuidePiece.of(HEIGHT, (gg, x, y, w) -> draw(gg, caption, x + w / 2, y));
    }
}
