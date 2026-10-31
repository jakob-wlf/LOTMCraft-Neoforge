package de.jakob.lotm.gui.custom.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.*;

public final class GuideBook {
    public static final String FOLDER = "guide/chapters";

    private final List<GuideChapter> chapters;

    private GuideBook(List<GuideChapter> chapters) {
        this.chapters = chapters;
    }

    public List<GuideChapter> chapters() {
        return chapters;
    }

    public static GuideBook load(ResourceManager resources) {
        List<GuideChapter> chapters = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources(FOLDER, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            try (Reader reader = entry.getValue().openAsReader()) {
                chapters.add(parseChapter(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                LOTMCraft.LOGGER.error("Failed to load guide chapter {}", file, e);
            }
        }
        chapters.sort(Comparator.comparingInt(GuideChapter::order).thenComparing(c -> c.id().toString()));
        return new GuideBook(chapters);
    }

    private static GuideChapter parseChapter(ResourceLocation id, JsonObject json) {
        int order = GsonHelper.getAsInt(json, "order", 1000);
        Component title = GuideText.parse(GsonHelper.getAsString(json, "title", id.getPath()));
        ItemStack icon = GuideElements.stack(GsonHelper.getAsString(json, "icon", "minecraft:book"));
        if (icon.isEmpty()) icon = new ItemStack(Items.BOOK);
        int color = GuideElements.color(GsonHelper.getAsString(json, "color", "#6A3A86"));
        Component quote = json.has("quote") ? GuideText.parse(GsonHelper.getAsString(json, "quote")) : null;
        EntityType<?> cover = null;
        if (json.has("cover_entity")) {
            ResourceLocation rl = ResourceLocation.tryParse(GsonHelper.getAsString(json, "cover_entity"));
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) cover = BuiltInRegistries.ENTITY_TYPE.get(rl);
        }
        List<GuideElement> elements = GuideElements.parseAll(GsonHelper.getAsJsonArray(json, "elements", new com.google.gson.JsonArray()), id);
        return new GuideChapter(id, order, title, icon, color, quote, cover, elements);
    }

    public record Pagination(List<GuidePage> pages, Map<ResourceLocation, Integer> chapterStart) {}

    public Pagination paginate(Font font, int contentW, int contentH) {
        List<GuidePage> pages = new ArrayList<>();
        Map<ResourceLocation, Integer> starts = new LinkedHashMap<>();
        GuideElement.Layout layout = new GuideElement.Layout(font, contentW);

        pages.add(new GuidePage(GuidePage.Kind.TITLE, null));
        flow(pages, GuidePage.Kind.CONTENTS, null, contentsElements(), layout, contentH);

        for (GuideChapter chapter : chapters) {
            if (pages.size() % 2 == 1) pages.add(new GuidePage(GuidePage.Kind.FILLER, null));
            starts.put(chapter.id(), pages.size());
            pages.add(new GuidePage(GuidePage.Kind.CHAPTER_COVER, chapter));
            flow(pages, GuidePage.Kind.CONTENT, chapter, chapter.elements(), layout, contentH);
        }
        if (pages.size() % 2 == 1) pages.add(new GuidePage(GuidePage.Kind.FILLER, null));
        return new Pagination(pages, starts);
    }

    private static void flow(List<GuidePage> pages, GuidePage.Kind kind, GuideChapter chapter,
                             List<GuideElement> elements, GuideElement.Layout layout, int contentH) {
        GuidePage page = new GuidePage(kind, chapter);
        pages.add(page);
        for (GuideElement element : elements) {
            if (element.isPageBreak()) {
                if (!page.pieces.isEmpty()) {
                    page = new GuidePage(kind, chapter);
                    pages.add(page);
                }
                continue;
            }
            List<GuidePiece> pieces = element.layout(layout);
            for (int i = 0; i < pieces.size(); i++) {
                GuidePiece piece = pieces.get(i);
                int gap = i == 0 && !page.pieces.isEmpty() ? element.spacingBefore() : 0;
                int need = gap + piece.height() + (piece.keepWithNext() ? 3 * GuideGraphics.LINE_H : 0);
                if (page.used + need > contentH && !page.pieces.isEmpty()) {
                    page = new GuidePage(kind, chapter);
                    pages.add(page);
                    gap = 0;
                }
                page.add(piece, gap);
            }
        }
    }

    private List<GuideElement> contentsElements() {
        List<GuideElement> list = new ArrayList<>();
        list.add(layout -> List.of(GuidePiece.of(26, (gg, x, y, w) -> {
            gg.centered(Component.translatable("guide.lotmcraft.contents").withStyle(ChatFormatting.BOLD), x + w / 2f, y, 1.3f, GuideGraphics.INK);
            gg.divider(x + w / 2, y + 16);
        })));
        for (GuideChapter chapter : chapters) {
            list.add(new GuideElement() {
                @Override
                public List<GuidePiece> layout(Layout layout) {
                    return List.of(GuidePiece.of(CONTENTS_ROW, (gg, x, y, w) -> drawContentsEntry(gg, chapter, x, y, w)));
                }

                @Override
                public int spacingBefore() {
                    return 0;
                }
            });
        }
        return list;
    }

    private static final int CONTENTS_ROW = 14;

    private static void drawContentsEntry(GuideGraphics gg, GuideChapter chapter, int x, int y, int w) {
        int h = CONTENTS_ROW;
        boolean hov = gg.hovered(x - 2, y, w + 4, h);
        if (hov) gg.g.fill(x - 2, y, x + w + 2, y + h, 0x22A06020);

        gg.g.pose().pushPose();
        gg.g.pose().translate(x, y + 1, 0);
        gg.g.pose().scale(0.75f, 0.75f, 1f);
        gg.g.renderItem(chapter.icon(), 0, 0);
        gg.g.pose().popPose();

        int tx = x + 16;
        String num = String.valueOf(gg.screen.chapterStart(chapter.id()) + 1);
        int numX = x + w - (int) (gg.font.width(num) * 0.85f);
        FormattedCharSequence title = Component.empty().append(chapter.title()).getVisualOrderText();
        float scale = 0.85f;
        gg.text(title, tx, y + 4, scale, hov ? GuideGraphics.GOLD_DARK : GuideGraphics.INK);
        gg.text(Component.literal(num), numX, y + 4, scale, GuideGraphics.INK);

        int dotsStart = tx + (int) (gg.font.width(title) * scale) + 4;
        for (int dx = dotsStart; dx < numX - 3; dx += 3) gg.g.fill(dx, y + 10, dx + 1, y + 11, GuideGraphics.INK_FAINT);

        gg.click(x - 2, y, w + 4, h, () -> gg.screen.goToChapter(chapter.id()));
    }
}
