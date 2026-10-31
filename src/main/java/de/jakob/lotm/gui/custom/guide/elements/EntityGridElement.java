package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public class EntityGridElement implements GuideElement {
    private static final int CELL_H = 54;
    private static final int RENDER_H = 38;

    private record Entry(EntityType<?> type, Component note, List<Component> tooltip) {}

    private final List<Entry> entries = new ArrayList<>();
    private final int columns;

    public EntityGridElement(JsonObject json) {
        this.columns = GsonHelper.getAsInt(json, "columns", 3);
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "entities")) {
            String id = e.isJsonObject() ? GsonHelper.getAsString(e.getAsJsonObject(), "id") : e.getAsString();
            String note = e.isJsonObject() ? GsonHelper.getAsString(e.getAsJsonObject(), "note", "") : "";
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            Component noteC = note.isEmpty() ? null : GuideText.parse(note);

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(type.getDescription().copy().withStyle(ChatFormatting.GOLD));
            if (noteC != null) tooltip.add(noteC.copy().withStyle(ChatFormatting.GRAY));
            String descKey = "guide." + rl.getNamespace() + ".entity." + rl.getPath();
            if (Language.getInstance().has(descKey)) tooltip.add(GuideText.parse(descKey).copy().withStyle(ChatFormatting.WHITE));
            entries.add(new Entry(type, noteC, tooltip));
        }
    }

    @Override
    public int spacingBefore() {
        return 6;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        List<GuidePiece> pieces = new ArrayList<>();
        for (int start = 0; start < entries.size(); start += columns) {
            List<Entry> row = entries.subList(start, Math.min(entries.size(), start + columns));
            int rowIndex = start / columns;
            pieces.add(GuidePiece.of(CELL_H, (gg, x, y, w) -> {
                int cellW = w / columns;
                int rx = x + (w - cellW * row.size()) / 2;
                for (int i = 0; i < row.size(); i++) drawCell(gg, row.get(i), rx + i * cellW, y, cellW, rowIndex * columns + i);
            }));
        }
        return pieces;
    }

    private void drawCell(GuideGraphics gg, Entry entry, int x, int y, int w, int index) {
        boolean hov = gg.hovered(x, y, w, CELL_H);
        if (hov) gg.g.fill(x + 1, y, x + w - 1, y + CELL_H - 1, 0x1A6A3A86);
        int cx = x + w / 2;
        gg.circle(cx, y + RENDER_H - 2, w - 6, true);
        LivingEntity entity = GuideGraphics.entity(entry.type);
        float yaw = hov ? 0f : gg.time * 25f + index * 47f;
        if (entity != null) gg.entity(entity, x + 2, y + 1, w - 4, RENDER_H - 3, yaw);

        float nameScale = 0.6f;
        List<FormattedCharSequence> name = gg.font.split(entry.type.getDescription(), (int) ((w - 2) / nameScale));
        int ny = y + RENDER_H + 1;
        for (FormattedCharSequence line : name.subList(0, Math.min(1, name.size()))) {
            gg.centered(line, cx, ny, nameScale, hov ? GuideGraphics.GOLD_DARK : GuideGraphics.INK);
        }
        if (entry.note != null) gg.centered(entry.note, cx, ny + 6, 0.5f, GuideGraphics.INK_SOFT);
        gg.tooltip(x, y, w, CELL_H, entry.tooltip);
    }
}
