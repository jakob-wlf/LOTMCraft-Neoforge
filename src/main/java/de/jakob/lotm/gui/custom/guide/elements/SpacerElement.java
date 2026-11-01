package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import net.minecraft.util.GsonHelper;

import java.util.List;

public class SpacerElement implements GuideElement {
    private final int height;

    public SpacerElement(JsonObject json) {
        this.height = GsonHelper.getAsInt(json, "height", 8);
    }

    @Override
    public int spacingBefore() {
        return 0;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        return List.of(GuidePiece.of(height, (gg, x, y, w) -> {}));
    }
}
