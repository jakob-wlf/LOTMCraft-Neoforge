package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class CraftingElement implements GuideElement {
    private static final int GRID = 3 * GuideGraphics.SLOT;
    private static final int HEIGHT = GRID + 2;

    private final ResourceLocation recipeId;
    private final Component caption;

    public CraftingElement(JsonObject json) {
        this.recipeId = ResourceLocation.parse(GsonHelper.getAsString(json, "recipe"));
        this.caption = json.has("caption") ? GuideText.parse(json.get("caption").getAsString()) : null;
    }

    @Override
    public int spacingBefore() {
        return 6;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        GuidePiece grid = GuidePiece.of(HEIGHT, this::draw);
        return caption == null ? List.of(grid) : List.of(grid, Captions.piece(caption));
    }

    private void draw(GuideGraphics gg, int x, int y, int w) {
        Minecraft mc = Minecraft.getInstance();
        Optional<RecipeHolder<?>> holder = mc.level == null ? Optional.empty() : mc.level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty()) {
            gg.text(Component.literal("Missing recipe " + recipeId), x, y, 0.6f, 0xFFAA2222);
            return;
        }
        Recipe<?> recipe = holder.get().value();
        int gridW = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;

        int total = GRID + 8 + 22 + 8 + 26;
        int gx = x + (w - total) / 2;
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < 9; i++) gg.slot(gx + (i % 3) * GuideGraphics.SLOT, y + (i / 3) * GuideGraphics.SLOT);
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack[] options = ingredients.get(i).getItems();
            if (options.length == 0) continue;
            ItemStack stack = gg.cycle(Arrays.asList(options), 1.2f);
            int sx = gx + (i % gridW) * GuideGraphics.SLOT, sy = y + (i / gridW) * GuideGraphics.SLOT;
            gg.item(stack, sx + 1, sy + 1);
            gg.tooltip(sx, sy, GuideGraphics.SLOT, GuideGraphics.SLOT, stack);
        }

        int ax = gx + GRID + 8;
        gg.blit(GuideGraphics.CRAFT_ARROW, ax, y + GRID / 2 - 7, 22, 15);

        ItemStack result = recipe.getResultItem(mc.level.registryAccess());
        int rx = ax + 22 + 8, ry = y + GRID / 2 - 13;
        gg.nineSlice(GuideGraphics.SLOT_TEX, rx, ry, 26, 26, 3, 18);
        gg.item(result, rx + 5, ry + 5);
        gg.tooltip(rx, ry, 26, 26, result);
    }
}
