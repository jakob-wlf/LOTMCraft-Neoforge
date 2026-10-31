package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideElements;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

public class StepsElement implements GuideElement {
    private static final int INDENT = 16;
    private final List<String> steps;
    private final int start;

    public StepsElement(JsonObject json) {
        this.steps = GuideElements.strings(json.get("steps"));
        this.start = GsonHelper.getAsInt(json, "start", 1);
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        List<GuidePiece> pieces = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            String number = String.valueOf(start + i);
            List<FormattedCharSequence> lines = layout.font().split(GuideText.parse(steps.get(i)),
                    (int) ((layout.width() - INDENT) / GuideGraphics.TEXT_SCALE));
            int height = Math.max(12, lines.size() * GuideGraphics.LINE_H) + 3;
            pieces.add(GuidePiece.of(height, (gg, x, y, w) -> {
                gg.blit(GuideGraphics.SEAL, x, y - 2, 12, 12);
                gg.centered(Component.literal(number), x + 6.5f, y + 1.5f, 0.6f, 0xFFF3E2C0);
                for (int l = 0; l < lines.size(); l++)
                    gg.text(lines.get(l), x + INDENT, y + l * GuideGraphics.LINE_H, GuideGraphics.TEXT_SCALE, GuideGraphics.INK_TEXT);
            }));
        }
        return pieces;
    }
}
