package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

public class KeybindsElement implements GuideElement {
    private static final float KEY_SCALE = 0.7f;

    private record Bind(String key, String text) {}

    private final List<Bind> binds = new ArrayList<>();

    public KeybindsElement(JsonObject json) {
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "binds")) {
            JsonObject o = e.getAsJsonObject();
            binds.add(new Bind(GsonHelper.getAsString(o, "key"), GsonHelper.getAsString(o, "text")));
        }
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        List<Component> labels = new ArrayList<>();
        int column = 18;
        for (Bind bind : binds) {
            KeyMapping mapping = GuideText.findKey(bind.key);
            Component label = mapping == null ? Component.literal("?") : mapping.getTranslatedKeyMessage();
            labels.add(label);
            column = Math.max(column, (int) (layout.font().width(label) * KEY_SCALE) + 10);
        }
        int keyColumn = Math.min(column, layout.width() / 2);

        List<GuidePiece> pieces = new ArrayList<>();
        for (int i = 0; i < binds.size(); i++) {
            Component label = labels.get(i);
            List<FormattedCharSequence> lines = layout.font().split(GuideText.parse(binds.get(i).text),
                    (int) ((layout.width() - keyColumn - 5) / GuideGraphics.TEXT_SCALE));
            int height = Math.max(14, lines.size() * GuideGraphics.LINE_H + 4) + 2;
            pieces.add(GuidePiece.of(height, (gg, x, y, w) -> {
                int capW = Math.min(keyColumn, (int) (gg.font.width(label) * KEY_SCALE) + 10);
                int capX = x + keyColumn - capW;
                gg.blit(GuideGraphics.KEYCAP, capX, y, 4, 14, 0, 0, 4, 14, 16, 14);
                gg.blit(GuideGraphics.KEYCAP, capX + 4, y, capW - 8, 14, 4, 0, 8, 14, 16, 14);
                gg.blit(GuideGraphics.KEYCAP, capX + capW - 4, y, 4, 14, 12, 0, 4, 14, 16, 14);
                gg.centered(label, capX + capW / 2f, y + 3.5f, KEY_SCALE, 0xFF2A2018);
                int ty = y + (lines.size() == 1 ? 4 : 1);
                for (int l = 0; l < lines.size(); l++)
                    gg.text(lines.get(l), x + keyColumn + 5, ty + l * GuideGraphics.LINE_H, GuideGraphics.TEXT_SCALE, GuideGraphics.INK_TEXT);
            }));
        }
        return pieces;
    }
}
