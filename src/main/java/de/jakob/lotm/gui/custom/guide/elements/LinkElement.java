package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.List;

/** A clickable cross-reference to another chapter ({@code "chapter": "spirit_world"}). */
public class LinkElement implements GuideElement {
    private final ResourceLocation chapter;
    private final String text;

    public LinkElement(JsonObject json) {
        String id = GsonHelper.getAsString(json, "chapter");
        this.chapter = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, id);
        this.text = GsonHelper.getAsString(json, "text");
    }

    @Override
    public int spacingBefore() {
        return 4;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        Component label = Component.literal("→ ").append(GuideText.parse(text));
        return List.of(GuidePiece.of(GuideGraphics.LINE_H + 2, (gg, x, y, w) -> {
            int textW = (int) (gg.font.width(label) * 0.8f);
            boolean hov = gg.hovered(x - 1, y - 1, textW + 2, GuideGraphics.LINE_H + 2);
            int color = hov ? GuideGraphics.GOLD_DARK : 0xFF24407A;
            gg.text(label, x, y, 0.8f, color);
            if (hov) gg.g.fill(x + 8, y + 7, x + textW, y + 8, color);
            gg.click(x - 1, y - 1, textW + 2, GuideGraphics.LINE_H + 2, () -> gg.screen.goToChapter(chapter));
        }));
    }
}
