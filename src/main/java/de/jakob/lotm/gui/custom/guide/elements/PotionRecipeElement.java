package de.jakob.lotm.gui.custom.guide.elements;

import com.google.gson.JsonObject;
import de.jakob.lotm.beyonders.potions.PotionRecipe;
import de.jakob.lotm.beyonders.potions.PotionRecipeItemHandler;
import de.jakob.lotm.beyonders.potions.PotionRecipes;
import de.jakob.lotm.gui.custom.guide.GuideElement;
import de.jakob.lotm.gui.custom.guide.GuideGraphics;
import de.jakob.lotm.gui.custom.guide.GuidePiece;
import de.jakob.lotm.gui.custom.guide.GuideText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PotionRecipeElement implements GuideElement {
    private static final int S = GuideGraphics.SLOT;
    private static final int HEIGHT = 46;

    private final int sequence;
    private final String pathway;
    private final Component caption;
    private List<PotionRecipe> recipes;
    private final Map<PotionRecipe, Item> recipeItems = new HashMap<>();

    public PotionRecipeElement(JsonObject json) {
        this.sequence = GsonHelper.getAsInt(json, "sequence", -1);
        this.pathway = GsonHelper.getAsString(json, "pathway", "");
        this.caption = json.has("caption") ? GuideText.parse(json.get("caption").getAsString()) : null;
    }

    @Override
    public int spacingBefore() {
        return 6;
    }

    @Override
    public List<GuidePiece> layout(Layout layout) {
        GuidePiece main = GuidePiece.of(HEIGHT, this::draw);
        return caption == null ? List.of(main) : List.of(main, Captions.piece(caption));
    }

    private List<PotionRecipe> recipes() {
        if (recipes == null) {
            if (!PotionRecipes.initialized) {
                PotionRecipes.initPotionRecipes();
                PotionRecipeItemHandler.initializeRecipes();
            }
            recipes = new ArrayList<>(PotionRecipes.RECIPES.stream()
                    .filter(r -> sequence < 0 || r.potion().getSequence() == sequence)
                    .filter(r -> pathway.isEmpty() || r.potion().getPathway().equals(pathway))
                    .sorted(Comparator.comparing((PotionRecipe r) -> r.potion().getPathway())
                            .thenComparing(r -> -r.potion().getSequence()))
                    .toList());
        }
        return recipes;
    }

    private void draw(GuideGraphics gg, int x, int y, int w) {
        List<PotionRecipe> all = recipes();
        if (all.isEmpty()) return;
        PotionRecipe recipe = gg.cycle(all, 3f);

        int total = 4 * S + 6 + S + 4 + 22 + 4 + S;
        int sx = x + (w - total) / 2, sy = y + 8;
        float label = 0.5f;
        gg.centered(Component.translatable("guide.lotmcraft.brewing.supplementary"), sx + 2 * S, y + 1, label, GuideGraphics.INK_SOFT);

        List<ItemStack> supplementary = recipe.supplementaryIngredients();
        for (int i = 0; i < 4; i++) gg.slotItem(supplementary.get(i), sx + i * S, sy);

        int mx = sx + 4 * S + 6;
        gg.centered(Component.literal("+"), mx - 3, sy + 5, 0.8f, GuideGraphics.INK_SOFT);
        gg.centered(Component.translatable("guide.lotmcraft.brewing.main"), mx + S / 2f, y + 1, label, GuideGraphics.INK_SOFT);
        gg.slotItem(recipe.mainIngredient(), mx, sy);

        int ax = mx + S + 4;
        gg.blit(GuideGraphics.CRAFT_ARROW, ax, sy + 1, 22, 15);

        int px = ax + 22 + 4;
        ItemStack potion = new ItemStack(recipe.potion());
        gg.centered(Component.translatable("guide.lotmcraft.brewing.result"), px + S / 2f, y + 1, label, GuideGraphics.INK_SOFT);
        gg.slotItem(potion, px, sy);

        Item recipeItem = recipeItems.computeIfAbsent(recipe, r ->
                PotionRecipeItemHandler.selectRecipeOfPathwayAndSequence(r.potion().getPathway(), r.potion().getSequence()));
        Component name = potion.getHoverName().copy().withStyle(ChatFormatting.BOLD);
        Component seq = Component.translatable("guide.lotmcraft.brewing.sequence", recipe.potion().getSequence());
        int ty = sy + S + 4;
        float nameW = gg.font.width(name) * 0.7f;
        int iconX = (int) (x + w / 2f - nameW / 2f) - 12;
        if (recipeItem != null) {
            ItemStack recipeStack = new ItemStack(recipeItem);
            gg.g.pose().pushPose();
            gg.g.pose().translate(iconX, ty - 2, 0);
            gg.g.pose().scale(0.62f, 0.62f, 1f);
            gg.g.renderItem(recipeStack, 0, 0);
            gg.g.pose().popPose();
            gg.tooltip(iconX, ty - 2, 10, 10, recipeStack);
        }
        gg.centered(name, x + w / 2f, ty, 0.7f, GuideGraphics.INK);
        gg.centered(seq, x + w / 2f, ty + 8, 0.55f, GuideGraphics.INK_SOFT);
    }
}
