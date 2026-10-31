package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideElements;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class EntityElement implements GuideElement {
    private final List<EntityType<?>> types;
    private final int height;
    private final Component caption;

    public EntityElement(JsonObject json) {
        this.types = GuideElements.entities(json.get("entity"));
        if (types.isEmpty()) throw new IllegalArgumentException("entity: no valid 'entity'");
        this.height = GsonHelper.getAsInt(json, "height", 72);
        this.caption = json.has("caption") ? GuideText.parse(json.get("caption").getAsString()) : null;
    }

    @Override
    public int spacingBefore() {
        return 6;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        return List.of(GuidePiece.of(height + Captions.HEIGHT, (gg, x, y, w) -> {
            EntityType<?> type = gg.cycle(types, 4f);
            int cx = x + w / 2;
            gg.circle(cx, y + height - 6, (int) (height * 1.3f), true);
            LivingEntity entity = GuideGraphics.entity(type);
            if (entity != null) gg.entity(entity, cx - height / 2, y + 2, height, height - 8, gg.time * 30f);
            gg.tooltip(cx - height / 2, y, height, height, List.of(type.getDescription()));
            Captions.draw(gg, caption != null ? caption : type.getDescription(), cx, y + height);
        }));
    }
}
