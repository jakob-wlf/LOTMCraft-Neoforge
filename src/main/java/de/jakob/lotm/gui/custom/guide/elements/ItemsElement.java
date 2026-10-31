package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideElements;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemsElement implements GuideElement {
    private static final int STEP = GuideGraphics.SLOT + 3;
    private final List<ItemStack> items;
    private final Component caption;

    public ItemsElement(JsonObject json) {
        this.items = GuideElements.stacks(json.get("items"));
        this.caption = json.has("caption") ? GuideText.parse(json.get("caption").getAsString()) : null;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        int perRow = Math.max(1, (layout.width() + 3) / STEP);
        List<GuidePiece> pieces = new ArrayList<>();
        for (int start = 0; start < items.size(); start += perRow) {
            List<ItemStack> row = items.subList(start, Math.min(items.size(), start + perRow));
            pieces.add(GuidePiece.of(STEP, (gg, x, y, w) -> {
                int rx = x + (w - (row.size() * STEP - 3)) / 2;
                for (int i = 0; i < row.size(); i++) gg.slotItem(row.get(i), rx + i * STEP, y);
            }));
        }
        if (caption != null) pieces.add(Captions.piece(caption));
        return pieces;
    }
}
