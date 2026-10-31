package de.jakob.lotm.gui.custom.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GuideBookScreen extends Screen {
    public static final int BOOK_W = 380, BOOK_H = 236, COVER = 10, GUTTER = 6;
    public static final int PAGE_W = (BOOK_W - 2 * COVER - GUTTER) / 2;
    public static final int PAGE_H = BOOK_H - 2 * COVER;
    public static final int MARGIN = 14, TOP = 22, BOTTOM = 20;
    public static final int CONTENT_W = PAGE_W - 2 * MARGIN;
    public static final int CONTENT_H = PAGE_H - TOP - BOTTOM;

    private static final int TAB_H = 20, TAB_STEP = 22, TAB_TOP = 12, TAB_TEX_W = 28;
    private static final int TABS_PER_SIDE = (BOOK_H - TAB_TOP) / TAB_STEP;
    private static final int RIBBON_W = 10, RIBBON_H = 56;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};

    private static int lastSpread = 0;

    private record Click(int x, int y, int w, int h, Runnable action) {}

    private final GuideBook book;
    private GuideBook.Pagination pagination;
    private final List<Click> clicks = new ArrayList<>();
    private final long openedAt = Util.getMillis();

    private int spread;
    private float scale = 1f;
    private float originX, originY;
    @Nullable private List<Component> tooltipLines;
    private ItemStack tooltipStack = ItemStack.EMPTY;

    public GuideBookScreen(GuideBook book) {
        super(Component.translatable("item.lotmcraft.guiding_book"));
        this.book = book;
    }

    @Override
    protected void init() {
        super.init();
        if (pagination == null) {
            pagination = book.paginate(font, CONTENT_W, CONTENT_H);
            spread = Mth.clamp(lastSpread, 0, maxSpread());
        }
        float fit = Math.min(width / (float) (BOOK_W + 2 * 30), height / (float) (BOOK_H + 12));
        scale = fit >= 1f ? Math.min(1.5f, Mth.floor(fit * 4f) / 4f) : fit;
        originX = (width - BOOK_W * scale) / 2f;
        originY = (height - BOOK_H * scale) / 2f;
    }

    // ---------------------------------------------------------------- api for pieces

    void setTooltip(List<Component> lines) {
        tooltipLines = lines;
        tooltipStack = ItemStack.EMPTY;
    }

    void setTooltip(ItemStack stack) {
        tooltipStack = stack;
        tooltipLines = null;
    }

    void addClick(int x, int y, int w, int h, Runnable action) {
        clicks.add(new Click(x, y, w, h, action));
    }

    public int chapterStart(ResourceLocation chapter) {
        return pagination.chapterStart().getOrDefault(chapter, -1);
    }

    public void goToChapter(ResourceLocation chapter) {
        int page = chapterStart(chapter);
        if (page >= 0) setSpread(page / 2);
    }

    public void goToPage(int page) {
        setSpread(page / 2);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        clicks.clear();
        tooltipLines = null;
        tooltipStack = ItemStack.EMPTY;

        int bmx = (int) Math.floor((mouseX - originX) / scale);
        int bmy = (int) Math.floor((mouseY - originY) / scale);
        float time = (Util.getMillis() - openedAt) / 1000f;
        GuideGraphics gg = new GuideGraphics(g, font, this, bmx, bmy, time);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(originX, originY, 0);
        ps.scale(scale, scale, 1f);

        drawTabs(gg);
        g.fill(4, 5, BOOK_W + 4, BOOK_H + 5, 0x70000000);
        gg.blit(GuideGraphics.COVER, 0, 0, BOOK_W, BOOK_H);

        int lx = COVER, rx = COVER + PAGE_W + GUTTER, py = COVER;
        drawPage(gg, spread * 2, lx, py, true);
        drawPage(gg, spread * 2 + 1, rx, py, false);
        drawRibbon(gg, rx);
        drawArrows(gg, lx, rx, py);

        ps.popPose();

        if (!tooltipStack.isEmpty()) {
            g.renderTooltip(font, tooltipStack, mouseX, mouseY);
        } else if (tooltipLines != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : tooltipLines) lines.addAll(font.split(line, 200));
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Nullable
    private GuidePage page(int index) {
        return index >= 0 && index < pagination.pages().size() ? pagination.pages().get(index) : null;
    }

    private void drawPage(GuideGraphics gg, int index, int x, int y, boolean left) {
        gg.blit(left ? GuideGraphics.PAGE_LEFT : GuideGraphics.PAGE_RIGHT, x, y, PAGE_W, PAGE_H);
        GuidePage page = page(index);
        if (page == null) return;

        switch (page.kind) {
            case TITLE -> drawTitlePage(gg, x, y);
            case CHAPTER_COVER -> drawChapterCover(gg, page.chapter, x, y);
            case FILLER -> gg.tinted(0x30FFFFFF, () -> gg.blit(GuideGraphics.EMBLEM, x + PAGE_W / 2 - 24, y + PAGE_H / 2 - 24, 48, 48));
            case CONTENTS, CONTENT -> {
                drawRunningHeader(gg, page, x, y, left);
                for (GuidePage.Placed placed : page.pieces)
                    placed.piece().render(gg, x + MARGIN, y + TOP + placed.y(), CONTENT_W);
                gg.centered(Component.literal("— " + (index + 1) + " —"), x + PAGE_W / 2f, y + PAGE_H - 13, 0.7f, GuideGraphics.INK_SOFT);
            }
        }
    }

    private void drawRunningHeader(GuideGraphics gg, GuidePage page, int x, int y, boolean left) {
        Component text = left || page.chapter == null ? Component.translatable("guide.lotmcraft.title") : page.chapter.title();
        gg.centered(Component.empty().append(text).withStyle(ChatFormatting.ITALIC), x + PAGE_W / 2f, y + 8, 0.65f, GuideGraphics.INK_SOFT);
        gg.g.hLine(x + MARGIN, x + PAGE_W - MARGIN - 1, y + 16, GuideGraphics.INK_FAINT);
    }

    private void drawTitlePage(GuideGraphics gg, int x, int y) {
        int cx = x + PAGE_W / 2;
        gg.blit(GuideGraphics.FRAME, x, y, PAGE_W, PAGE_H);

        List<FormattedCharSequence> title = gg.wrap(Component.translatable("guide.lotmcraft.title").withStyle(ChatFormatting.BOLD), PAGE_W - 44, 1.5f);
        int ty = y + 30;
        for (FormattedCharSequence line : title) {
            gg.centered(line, cx + 1, ty + 1, 1.5f, 0x33000000);
            gg.centered(line, cx, ty, 1.5f, GuideGraphics.INK);
            ty += 15;
        }
        gg.divider(cx, ty + 4);
        int sy = ty + 12;
        for (FormattedCharSequence line : gg.wrap(Component.translatable("guide.lotmcraft.subtitle").withStyle(ChatFormatting.ITALIC), PAGE_W - 44, 0.75f)) {
            gg.centered(line, cx, sy, 0.75f, GuideGraphics.INK_SOFT);
            sy += 8;
        }

        int ey = y + PAGE_H - 70;
        PoseStack ps = gg.g.pose();
        ps.pushPose();
        ps.translate(cx, ey, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(gg.time * 6f));
        gg.blit(GuideGraphics.CIRCLE, -38, -38, 76, 76, 0, 0, 96, 96, 96, 96);
        ps.popPose();
        gg.blit(GuideGraphics.EMBLEM, cx - 24, ey - 24, 48, 48);

        gg.centered(Component.translatable("guide.lotmcraft.hint"), cx, y + PAGE_H - 24, 0.6f, GuideGraphics.INK_SOFT);
    }

    private void drawChapterCover(GuideGraphics gg, GuideChapter chapter, int x, int y) {
        int cx = x + PAGE_W / 2;
        gg.blit(GuideGraphics.FRAME, x, y, PAGE_W, PAGE_H);

        int number = book.chapters().indexOf(chapter);
        String numeral = number < ROMAN.length ? ROMAN[number] : String.valueOf(number + 1);
        gg.centered(Component.translatable("guide.lotmcraft.chapter", numeral), cx, y + 24, 0.7f, chapter.color());

        int ty = y + 35;
        for (FormattedCharSequence line : gg.wrap(Component.empty().append(chapter.title()).withStyle(ChatFormatting.BOLD), PAGE_W - 40, 1.25f)) {
            gg.centered(line, cx, ty, 1.25f, GuideGraphics.INK);
            ty += 13;
        }
        gg.divider(cx, ty + 3);

        int stageTop = ty + 12, stageBottom = y + PAGE_H - 52;
        int stageH = stageBottom - stageTop;
        gg.circle(cx, stageBottom - 4, 110, true);
        LivingEntity entity = chapter.coverEntity() == null ? null : GuideGraphics.entity(chapter.coverEntity());
        if (entity != null) {
            gg.entity(entity, cx - stageH / 2, stageTop, stageH, stageH - 4, gg.time * 25f);
        } else {
            float bob = (float) Math.sin(gg.time * 1.4f) * 2f;
            gg.model(chapter.icon(), cx, stageTop + stageH * 0.45f + bob, stageH * 0.55f, gg.time * 30f, 22f);
        }

        if (chapter.quote() != null) {
            int qy = y + PAGE_H - 44;
            for (FormattedCharSequence line : gg.wrap(Component.empty().append(chapter.quote()).withStyle(ChatFormatting.ITALIC), PAGE_W - 44, 0.65f)) {
                gg.centered(line, cx, qy, 0.65f, GuideGraphics.INK_SOFT);
                qy += 7;
            }
        }
    }

    private void drawTabs(GuideGraphics gg) {
        GuideChapter active = activeChapter();
        List<GuideChapter> chapters = book.chapters();
        for (int i = 0; i < chapters.size(); i++) {
            GuideChapter chapter = chapters.get(i);
            boolean right = i < TABS_PER_SIDE;
            int ty = TAB_TOP + (i % TABS_PER_SIDE) * TAB_STEP;
            boolean act = chapter == active;
            int out = act ? 22 : 17;
            int hx = right ? BOOK_W - 2 : -out - 2;
            boolean hov = gg.hovered(hx, ty, out + 4, TAB_H);
            if (hov && !act) out = 20;

            int tx = right ? BOOK_W - 8 : 8 - (out + 6);
            int w = out + 6;
            float u = right ? TAB_TEX_W - w : TAB_TEX_W;
            gg.tinted(chapter.color(), () -> gg.blit(GuideGraphics.TAB, tx, ty, w, TAB_H, right ? u : TAB_TEX_W, 0, w, TAB_H, 2 * TAB_TEX_W, TAB_H));

            int iconX = right ? BOOK_W + out - 15 : -out + 3;
            PoseStack ps = gg.g.pose();
            ps.pushPose();
            ps.translate(iconX, ty + 4, 0);
            ps.scale(0.75f, 0.75f, 1f);
            gg.g.renderItem(chapter.icon(), 0, 0);
            ps.popPose();

            gg.tooltip(hx, ty, out + 4, TAB_H, List.of(chapter.title()));
            gg.click(hx, ty, out + 4, TAB_H, () -> goToChapter(chapter.id()));
        }
    }

    private void drawRibbon(GuideGraphics gg, int rx) {
        int x = rx + PAGE_W - 30, y = -4;
        boolean hov = gg.hovered(x, y, RIBBON_W, RIBBON_H);
        gg.blit(GuideGraphics.RIBBON, x, y + (hov ? 3 : 0), RIBBON_W, RIBBON_H);
        gg.tooltip(x, y, RIBBON_W, RIBBON_H, List.of(Component.translatable("guide.lotmcraft.contents")));
        gg.click(x, y, RIBBON_W, RIBBON_H, () -> setSpread(0));
    }

    private void drawArrows(GuideGraphics gg, int lx, int rx, int py) {
        int ay = py + PAGE_H - 16;
        if (spread > 0) arrow(gg, lx + 10, ay, false, () -> flip(-1));
        if (spread < maxSpread()) arrow(gg, rx + PAGE_W - 28, ay, true, () -> flip(1));
    }

    private void arrow(GuideGraphics gg, int x, int y, boolean right, Runnable action) {
        boolean hov = gg.hovered(x - 2, y - 3, 22, 16);
        gg.blit(GuideGraphics.ARROWS, x, y, 18, 10, right ? 18 : 0, hov ? 10 : 0, 18, 10, 36, 20);
        gg.click(x - 2, y - 3, 22, 16, action);
    }

    @Nullable
    private GuideChapter activeChapter() {
        GuidePage left = page(spread * 2), right = page(spread * 2 + 1);
        if (left != null && left.chapter != null) return left.chapter;
        return right != null ? right.chapter : null;
    }


    private int maxSpread() {
        return Math.max(0, (pagination.pages().size() - 1) / 2);
    }

    private void flip(int delta) {
        setSpread(spread + delta);
    }

    private void setSpread(int s) {
        s = Mth.clamp(s, 0, maxSpread());
        if (s == spread) return;
        spread = s;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double bx = (mouseX - originX) / scale, by = (mouseY - originY) / scale;
            for (Click c : List.copyOf(clicks)) {
                if (bx >= c.x && bx < c.x + c.w && by >= c.y && by < c.y + c.h) {
                    c.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0) flip(1);
        else if (scrollY > 0) flip(-1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_PAGE_DOWN -> flip(1);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_PAGE_UP -> flip(-1);
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_BACKSPACE -> setSpread(0);
            case GLFW.GLFW_KEY_END -> setSpread(maxSpread());
            default -> {
                if (minecraft != null && minecraft.options.keyInventory.matches(key, scancode)) {
                    onClose();
                    return true;
                }
                return super.keyPressed(key, scancode, mods);
            }
        }
        return true;
    }

    @Override
    public void removed() {
        lastSpread = spread;
        GuideGraphics.clearEntityCache();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
