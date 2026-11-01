package de.jakob.lotm.gui.custom.guide;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GuidePage {
    public enum Kind { TITLE, CONTENTS, CHAPTER_COVER, CONTENT, FILLER }

    public final Kind kind;
    @Nullable public final GuideChapter chapter;
    public final List<Placed> pieces = new ArrayList<>();
    int used;

    GuidePage(Kind kind, @Nullable GuideChapter chapter) {
        this.kind = kind;
        this.chapter = chapter;
    }

    void add(GuidePiece piece, int gap) {
        pieces.add(new Placed(piece, used + gap));
        used += gap + piece.height();
    }

    public record Placed(GuidePiece piece, int y) {}
}
