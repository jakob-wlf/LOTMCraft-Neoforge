package de.jakob.lotm.gui.custom.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.gui.custom.guide.elements.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class GuideElements {
    private static final Map<String, Function<JsonObject, GuideElement>> TYPES = new LinkedHashMap<>();

    static {
        register("text", TextElement::new);
        register("heading", HeadingElement::new);
        register("steps", StepsElement::new);
        register("note", NoteElement::new);
        register("items", ItemsElement::new);
        register("model", ModelElement::new);
        register("entity", EntityElement::new);
        register("entity_grid", EntityGridElement::new);
        register("keybinds", KeybindsElement::new);
        register("crafting", CraftingElement::new);
        register("potion_recipe", PotionRecipeElement::new);
        register("link", LinkElement::new);
        register("redacted", RedactedElement::new);
        register("image", ImageElement::new);
        register("spacer", SpacerElement::new);
        register("divider", json -> new DividerElement());
        register("page_break", json -> new GuideElement() {
            @Override public List<GuidePiece> layout(Layout layout) { return List.of(); }
            @Override public boolean isPageBreak() { return true; }
        });
    }

    private GuideElements() {}

    public static void register(String type, Function<JsonObject, GuideElement> factory) {
        TYPES.put(type, factory);
    }

    public static List<GuideElement> parseAll(JsonArray array, ResourceLocation chapter) {
        List<GuideElement> out = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement raw = array.get(i);
            try {
                if (raw.isJsonPrimitive()) {
                    JsonObject json = new JsonObject();
                    json.addProperty("text", raw.getAsString());
                    out.add(new TextElement(json));
                    continue;
                }
                JsonObject json = raw.getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "text";
                Function<JsonObject, GuideElement> factory = TYPES.get(type);
                if (factory == null) throw new IllegalArgumentException("Unknown element type '" + type + "'");
                out.add(factory.apply(json));
            } catch (Exception e) {
                String msg = chapter + " element #" + i + ": " + e.getMessage();
                LOTMCraft.LOGGER.error("Guide book: {}", msg);
                out.add(new ErrorElement(msg));
            }
        }
        return out;
    }


    public static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    public static List<ItemStack> stacks(JsonElement json) {
        List<ItemStack> out = new ArrayList<>();
        for (String id : strings(json)) {
            ItemStack s = stack(id);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public static List<EntityType<?>> entities(JsonElement json) {
        List<EntityType<?>> out = new ArrayList<>();
        for (String id : strings(json)) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) out.add(BuiltInRegistries.ENTITY_TYPE.get(rl));
        }
        return out;
    }

    public static List<String> strings(JsonElement json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;
        if (json.isJsonArray()) json.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
        else out.add(json.getAsString());
        return out;
    }

    public static int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFF6A3A86;
        }
    }
}
