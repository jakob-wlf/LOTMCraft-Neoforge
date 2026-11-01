package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.List;

public class ImageElement implements GuideElement {
    private final ResourceLocation texture;
    private final int width, height, texWidth, texHeight;
    private final Component caption;

    public ImageElement(JsonObject json) {
        this.texture = ResourceLocation.parse(GsonHelper.getAsString(json, "texture"));
        this.width = GsonHelper.getAsInt(json, "width");
        this.height = GsonHelper.getAsInt(json, "height");
        this.texWidth = GsonHelper.getAsInt(json, "tex_width", width);
        this.texHeight = GsonHelper.getAsInt(json, "tex_height", height);
        this.caption = json.has("caption") ? GuideText.parse(json.get("caption").getAsString()) : null;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        int w = Math.min(width, layout.width());
        int h = Math.round(height * (w / (float) width));
        GuidePiece image = GuidePiece.of(h, (gg, x, y, pw) ->
                gg.blit(texture, x + (pw - w) / 2, y, w, h, 0, 0, texWidth, texHeight, texWidth, texHeight));
        return caption == null ? List.of(image) : List.of(image, Captions.piece(caption));
    }
}
