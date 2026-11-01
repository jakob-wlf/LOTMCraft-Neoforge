package de.jakob.lotm.gui.custom.guide;

public interface GuidePiece {

    int height();

    void render(GuideGraphics gg, int x, int y, int width);

    default boolean keepWithNext() {
        return false;
    }

    static GuidePiece of(int height, Renderer renderer) {
        return new GuidePiece() {
            @Override public int height() { return height; }
            @Override public void render(GuideGraphics gg, int x, int y, int width) { renderer.render(gg, x, y, width); }
        };
    }

    @FunctionalInterface
    interface Renderer {
        void render(GuideGraphics gg, int x, int y, int width);
    }
}
