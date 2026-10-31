package de.jakob.lotm.gui.custom.guide.elements;

import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class ErrorElement implements GuideElement {
    private final String message;

    public ErrorElement(String message) {
        this.message = message;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        List<GuidePiece> pieces = new ArrayList<>();
        for (FormattedCharSequence line : layout.font().split(Component.literal(message), (int) (layout.width() / 0.6f)))
            pieces.add(GuidePiece.of(6, (gg, x, y, w) -> gg.text(line, x, y, 0.6f, 0xFFAA2222)));
        return pieces;
    }
}
