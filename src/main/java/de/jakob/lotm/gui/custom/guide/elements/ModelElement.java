package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideElements;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ModelElement implements GuideElement {
    private final List<ItemStack> items;
    private final int height;
    private final Component caption;
    private final boolean autoCaption;

    public ModelElement(JsonObject json) {
        this.items = GuideElements.stacks(json.get("item"));
        if (items.isEmpty()) throw new IllegalArgumentException("model: no valid 'item'");
        this.height = GsonHelper.getAsInt(json, "height", 64);
        this.caption = json.has("caption") ? GuideText.parse(json.get("caption").getAsString()) : null;
        this.autoCaption = GsonHelper.getAsBoolean(json, "auto_caption", caption == null);
    }

    @Override
    public int spacingBefore() {
        return 6;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        boolean captioned = caption != null || autoCaption;
        return List.of(GuidePiece.of(height + (captioned ? Captions.HEIGHT : 0), (gg, x, y, w) -> {
            ItemStack stack = gg.cycle(items, 3f);
            int cx = x + w / 2;
            int size = (int) (height * 0.5f);
            gg.circle(cx, y + height - size / 5, (int) (size * 2.1f), true);
            float bob = (float) Math.sin(gg.time * 1.6f) * 1.5f;
            gg.model(stack, cx, y + height * 0.47f + bob, size, gg.time * 35f, 22f);
            gg.tooltip(cx - size, y, size * 2, height, stack);
            if (captioned) Captions.draw(gg, caption != null ? caption : stack.getHoverName(), cx, y + height);
        }));
    }
}
