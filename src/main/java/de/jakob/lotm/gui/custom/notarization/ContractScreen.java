package de.jakob.lotm.gui.custom.notarization;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractAction;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractRules;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractText;
import de.jakob.lotm.beyonders.abilities.sun.notarization.net.Candidate;
import de.jakob.lotm.network.packets.toServer.CreateContractPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public class ContractScreen extends AbstractContainerScreen<ContractMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/notarization/contract_bg.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/notarization/widgets.png");
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/notarization/icons.png");

    private static final int W = 256, H = 224;
    private static final int ROWS = 4, ROW_H = 11, LIST_W = 112;
    private static final int LIST_Y = 66, ACTION_Y = 116, DUR_Y = 145, SLIDER_Y = 156, ITEM_Y = 172, BTN_Y = 198;

    private final List<Candidate> candidates;
    private int left, top;
    private int selA = -1, selB = -1, scrollA, scrollB;
    private ContractAction action = ContractAction.DROP_ITEM;
    private int durIdx = 1;
    private int invPage = 0;
    private int selSlot = -1;
    private boolean dragging;
    private List<Component> tooltip;
    private ItemStack hoverStack = ItemStack.EMPTY;

    public ContractScreen(ContractMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.candidates = menu.candidates();
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        left = leftPos;
        top = topPos;
        if (selA < 0 && minecraft != null && minecraft.player != null) {
            UUID me = minecraft.player.getUUID();
            for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).id().equals(me)) selA = i;
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private int seconds() { return action.durations()[durIdx]; }
    private ItemStack selectedStack() {
        return selSlot >= 0 && minecraft != null && minecraft.player != null
                ? minecraft.player.getInventory().getItem(selSlot) : ItemStack.EMPTY;
    }
    private int pageStart() { return invPage == 0 ? 0 : 9 + (invPage - 1) * 9; }
    private void click() { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f)); }

    private Component problem() {
        if (selA < 0 || selB < 0) return Component.translatable("contractnotary.screen.problem.parties");
        if (action == ContractAction.DROP_ITEM && selectedStack().isEmpty()) return Component.translatable("contractnotary.screen.problem.item");
        return null;
    }

    private Component preview() {
        Component unchosen = Component.translatable("contractnotary.screen.unchosen");
        Component a = selA >= 0 ? Component.literal(candidates.get(selA).name()) : unchosen;
        Component b = selB >= 0 ? Component.literal(candidates.get(selB).name()) : unchosen;
        ItemStack st = selectedStack();
        Component item = action == ContractAction.DROP_ITEM && !st.isEmpty() ? st.getHoverName() : null;
        return ContractText.sentence(action, a, b, item, seconds(), ChatFormatting.DARK_RED, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_GREEN);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        tooltip = null;
        hoverStack = ItemStack.EMPTY;
        g.blit(BG, left, top, 0, 0, W, H, 256, 256);

        g.drawString(font, title, left + (W - font.width(title)) / 2, top + 11, 0xFFE9A0, true);

        int bx = left + 12, by = top + 26, bw = W - 24, bh = 28;
        g.fill(bx, by, bx + bw, by + bh, 0x33200C02);
        g.renderOutline(bx, by, bw, bh, 0x88784A12);
        float s = 0.8f;
        g.pose().pushPose();
        g.pose().translate(bx + 5, by + 3, 0);
        g.pose().scale(s, s, 1f);
        List<FormattedCharSequence> lines = font.split(preview(), (int) ((bw - 10) / s));
        for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(font, lines.get(i), 0, i * 10, 0x3B2A14, false);
        g.pose().popPose();

        g.drawString(font, Component.translatable("contractnotary.screen.party_a"), left + 12, top + 56, 0x5A3510, false);
        g.drawString(font, Component.translatable("contractnotary.screen.party_b"), left + 132, top + 56, 0x5A3510, false);
        drawList(g, mx, my, left + 12, top + LIST_Y, true);
        drawList(g, mx, my, left + 132, top + LIST_Y, false);

        ContractAction[] acts = ContractAction.values();
        for (int i = 0; i < acts.length; i++) {
            int x = left + 12 + i * 30, y = top + ACTION_Y;
            boolean hov = inside(mx, my, x, y, 24, 24), sel = acts[i] == action;
            g.blit(WIDGETS, x, y, sel ? 112 : hov ? 88 : 64, 0, 24, 24, 256, 64);
            g.blit(ICONS, x + 2, y + 2, 20, 20, i * 32, 0, 32, 32, 128, 32);
            if (hov) tooltip = List.of(acts[i].displayName(), acts[i].description().copy().withStyle(ChatFormatting.GRAY));
        }
        g.drawString(font, action.displayName().copy().withStyle(ChatFormatting.BOLD), left + 138, top + ACTION_Y, 0x7A1C10, false);
        List<FormattedCharSequence> desc = font.split(action.description(), 108);
        for (int i = 0; i < Math.min(2, desc.size()); i++) g.drawString(font, desc.get(i), left + 138, top + ACTION_Y + 10 + i * 9, 0x3B2A14, false);

        Component dl = Component.translatable(action.isDeadline() ? "contractnotary.screen.deadline" : "contractnotary.screen.duration",
                ContractRules.formatDuration(seconds()));
        g.drawString(font, dl, left + 12, top + DUR_Y, 0x3B2A14, false);
        int sx = left + 12, sw = 232, n = action.durations().length;
        g.fill(sx, top + SLIDER_Y + 5, sx + sw, top + SLIDER_Y + 7, 0xFF5A3A10);
        for (int i = 0; i < n; i++) {
            int nx = sx + 4 + i * (sw - 8) / (n - 1);
            g.fill(nx, top + SLIDER_Y + 3, nx + 1, top + SLIDER_Y + 9, 0xFF7A4A12);
        }
        int hx = sx + 4 + durIdx * (sw - 8) / (n - 1) - 4;
        boolean hh = dragging || inside(mx, my, sx, top + SLIDER_Y - 2, sw, 16);
        g.blit(WIDGETS, hx, top + SLIDER_Y - 1, hh ? 180 : 172, 0, 8, 14, 256, 64);

        boolean drop = action == ContractAction.DROP_ITEM;
        int iy = top + ITEM_Y;
        drawArrow(g, mx, my, left + 12, iy, true);
        drawArrow(g, mx, my, left + 192, iy, false);
        for (int i = 0; i < 9; i++) {
            int x = left + 28 + i * 18;
            int idx = pageStart() + i;
            boolean sel = drop && idx == selSlot;
            g.blit(WIDGETS, x, iy, sel ? 154 : 136, 0, 18, 18, 256, 64);
            Candidate a = candidates.get(selA);
            ItemStack st = a.inventory().get(idx);
            g.renderItem(st, x + 1, iy + 1);
            g.renderItemDecorations(font, st, x + 1, iy + 1);
            if (drop && inside(mx, my, x, iy, 18, 18)) {
                g.fill(x + 1, iy + 1, x + 17, iy + 17, 0x80FFFFFF);
                if (!st.isEmpty()) hoverStack = st;
            }
        }
        g.drawString(font, (invPage + 1) + "/4", left + 208, iy + 5, 0x3B2A14, false);
        if (!drop) {
            g.fill(left + 12, iy, left + W - 12, iy + 18, 0xAA3A2A10);
            Component none = Component.translatable("contractnotary.screen.no_item");
            g.drawString(font, none, left + (W - font.width(none)) / 2, iy + 5, 0xE8C060, false);
        }

        Component prob = problem();
        boolean sealHover = inside(mx, my, left + 12, top + BTN_Y, 150, 16);
        drawButton(g, left + 12, top + BTN_Y, 150, Component.translatable("contractnotary.screen.seal"), prob == null, sealHover);
        drawButton(g, left + 170, top + BTN_Y, 74, Component.translatable("contractnotary.screen.cancel"), true,
                inside(mx, my, left + 170, top + BTN_Y, 74, 16));
        if (sealHover && prob != null) tooltip = List.of(prob.copy().withStyle(ChatFormatting.RED));

    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt); // background, renderBg, menu slots
        if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
        else if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private void drawList(GuiGraphics g, int mx, int my, int x, int y, boolean first) {
        int sel = first ? selA : selB, other = first ? selB : selA, scroll = first ? scrollA : scrollB;
        int h = ROWS * ROW_H + 2;
        g.fill(x, y, x + LIST_W, y + h, 0x772A1806);
        g.renderOutline(x, y, LIST_W, h, 0xFF8A5A12);
        if (candidates.isEmpty()) {
            Component none = Component.translatable("contractnotary.screen.nobody");
            g.drawString(font, none, x + (LIST_W - font.width(none)) / 2, y + h / 2 - 4, 0xB89A60, false);
            return;
        }
        UUID me = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= candidates.size()) break;
            Candidate c = candidates.get(idx);
            int ey = y + 1 + i * ROW_H;
            boolean selected = idx == sel, disabled = idx == other;
            boolean hov = !disabled && inside(mx, my, x + 1, ey, LIST_W - 7, ROW_H);
            if (selected) g.fill(x + 1, ey, x + LIST_W - 5, ey + ROW_H, 0xCCE0A820);
            else if (hov) g.fill(x + 1, ey, x + LIST_W - 5, ey + ROW_H, 0x44FFFFFF);
            int col = selected ? 0x2A1800 : disabled ? 0x7A6A50 : 0xF4E4B8;
            String seq = "S" + c.sequence();
            int seqW = font.width(seq);
            String name = c.name() + (c.id().equals(me) ? "*" : "");
            name = font.plainSubstrByWidth(name, LIST_W - seqW - 14);
            g.drawString(font, name, x + 4, ey + 2, col, false);
            g.drawString(font, seq, x + LIST_W - 7 - seqW, ey + 2, selected ? 0x5A3000 : disabled ? 0x7A6A50 : 0xE8C060, false);
        }
        if (candidates.size() > ROWS) {
            int max = candidates.size() - ROWS;
            int th = Math.max(6, (h - 2) * ROWS / candidates.size());
            int ty = y + 1 + (h - 2 - th) * scroll / max;
            g.fill(x + LIST_W - 4, y + 1, x + LIST_W - 1, y + h - 1, 0x66000000);
            g.fill(x + LIST_W - 4, ty, x + LIST_W - 1, ty + th, 0xFFE0A820);
        }
    }

    private void drawArrow(GuiGraphics g, int mx, int my, int x, int y, boolean leftArrow) {
        boolean hov = inside(mx, my, x, y, 12, 18);
        g.blit(WIDGETS, x, y, leftArrow ? 188 : 200, hov ? 18 : 0, 12, 18, 256, 64);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, Component label, boolean enabled, boolean hover) {
        int v = !enabled ? 32 : hover ? 16 : 0;
        g.blit(WIDGETS, x, y, 0, v, 4, 16, 256, 64);
        g.blit(WIDGETS, x + 4, y, w - 8, 16, 4, v, 56, 16, 256, 64);
        g.blit(WIDGETS, x + w - 4, y, 60, v, 4, 16, 256, 64);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, enabled ? 0x3B1E00 : 0x6B5B45, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (clickList(mx, my, left + 12, top + LIST_Y, true) || clickList(mx, my, left + 132, top + LIST_Y, false)) return true;

        ContractAction[] acts = ContractAction.values();
        for (int i = 0; i < acts.length; i++)
            if (inside(mx, my, left + 12 + i * 30, top + ACTION_Y, 24, 24)) {
                if (acts[i] != action) { action = acts[i]; durIdx = action == ContractAction.DROP_ITEM ? 1 : 3; }
                click();
                return true;
            }

        if (inside(mx, my, left + 12, top + SLIDER_Y - 2, 232, 16)) { dragging = true; setSlider(mx); click(); return true; }

        if (inside(mx, my, left + 12, top + ITEM_Y, 12, 18)) { invPage = (invPage + 3) % 4; click(); return true; }
        if (inside(mx, my, left + 192, top + ITEM_Y, 12, 18)) { invPage = (invPage + 1) % 4; click(); return true; }
        if (action == ContractAction.DROP_ITEM)
            for (int i = 0; i < 9; i++)
                if (inside(mx, my, left + 28 + i * 18, top + ITEM_Y, 18, 18)) {
                    int idx = pageStart() + i;
                    if (!minecraft.player.getInventory().getItem(idx).isEmpty()) { selSlot = idx; click(); }
                    return true;
                }

        if (inside(mx, my, left + 170, top + BTN_Y, 74, 16)) { click(); onClose(); return true; }
        if (inside(mx, my, left + 12, top + BTN_Y, 150, 16) && problem() == null) { seal(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    private boolean clickList(double mx, double my, int x, int y, boolean first) {
        if (!inside(mx, my, x, y, LIST_W - 6, ROWS * ROW_H + 2)) return false;
        int i = ((int) my - y - 1) / ROW_H;
        int idx = (first ? scrollA : scrollB) + i;
        int other = first ? selB : selA;
        if (i < 0 || i >= ROWS || idx >= candidates.size() || idx == other) return true;
        if (first) selA = idx; else selB = idx;
        click();
        return true;
    }

    private void setSlider(double mx) {
        int n = action.durations().length;
        double t = (mx - (left + 12 + 4)) / (232 - 8);
        durIdx = Mth.clamp((int) Math.round(t * (n - 1)), 0, n - 1);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { setSlider(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int dir = sy > 0 ? -1 : 1;
        int max = Math.max(0, candidates.size() - ROWS);
        if (inside(mx, my, left + 12, top + LIST_Y, LIST_W, ROWS * ROW_H + 2)) { scrollA = Mth.clamp(scrollA + dir, 0, max); return true; }
        if (inside(mx, my, left + 132, top + LIST_Y, LIST_W, ROWS * ROW_H + 2)) { scrollB = Mth.clamp(scrollB + dir, 0, max); return true; }
        if (inside(mx, my, left + 12, top + ITEM_Y, 232, 18)) { invPage = (invPage + dir + 4) % 4; return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private void seal() {
        Candidate a = candidates.get(selA), b = candidates.get(selB);
        String item = action == ContractAction.DROP_ITEM
                ? BuiltInRegistries.ITEM.getKey(selectedStack().getItem()).toString() : "";
        PacketDistributor.sendToServer(new CreateContractPayload(a.id(), b.id(), action.getId(), seconds(), item));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.5f));
        onClose();
    }
}
