package de.jakob.lotm.gui.custom.notarization;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractAction;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractRules;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.ContractText;
import de.jakob.lotm.beyonders.abilities.sun.notarization.net.ConfirmData;
import de.jakob.lotm.network.packets.toServer.ConfirmDecisionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class ConfirmScreen extends AbstractContainerScreen<ConfirmMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/notarization/confirm_bg.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/notarization/confirm_widgets.png");
    private static final int W = 192, H = 128;
    private static final int BTN_Y = 102, ACCEPT_X = 12, DECLINE_X = 100, BTN_W = 80, BTN_H = 20;

    private int remaining;

    public ConfirmScreen(ConfirmMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.remaining = menu.data().remainingTicks();
    }

    @Override public boolean isPauseScreen() { return false; }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Component sentence() {
        ConfirmData d = menu.data();
        ContractAction action = ContractAction.byId(d.actionId());
        if (action == null) return Component.empty();
        Component item = null;
        if (!d.item().isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(d.item());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                Item it = BuiltInRegistries.ITEM.get(id);
                item = it.getDescription();
            }
        }
        return ContractText.sentence(action, Component.literal(d.nameA()), Component.literal(d.nameB()), item, d.seconds(),
                ChatFormatting.DARK_RED, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_GREEN);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        if (remaining > 0) remaining--;
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, W, H, 256, 128);

        Component t = Component.literal("Contract Offer");
        g.drawString(font, t, x + (W - font.width(t)) / 2, y + 11, 0xFFE9A0, true);

        int bx = x + 12, by = y + 28, bw = W - 24, bh = 40;
        g.fill(bx, by, bx + bw, by + bh, 0x33200C02);
        g.renderOutline(bx, by, bw, bh, 0x88784A12);
        float s = 0.9f;
        g.pose().pushPose();
        g.pose().translate(bx + 5, by + 4, 0);
        g.pose().scale(s, s, 1f);
        List<FormattedCharSequence> lines = font.split(sentence(), (int) ((bw - 10) / s));
        for (int i = 0; i < Math.min(4, lines.size()); i++) g.drawString(font, lines.get(i), 0, i * 9, 0x3B2A14, false);
        g.pose().popPose();

        Component w = Component.literal("Witnessed by " + menu.data().notary());
        g.drawString(font, w, x + (W - font.width(w)) / 2, y + 71, 0x5A3510, false);

        int total = ContractRules.CONFIRM_TICKS;
        int fill = Math.max(0, Math.min(160, 160 * remaining / total));
        g.blit(WIDGETS, x + 16, y + 83, 0, 44, 160, 6, 256, 64);
        if (fill > 0) g.blit(WIDGETS, x + 16, y + 83, 0, 50, fill, 6, 256, 64);
        int secs = (remaining + 19) / 20;
        Component tl = Component.literal("Time left: " + secs + "s");
        g.drawString(font, tl, x + (W - font.width(tl)) / 2, y + 91, secs <= 5 ? 0xA01810 : 0x3B2A14, false);

        drawButton(g, x + ACCEPT_X, y + BTN_Y, 0, "Accept", inside(mx, my, x + ACCEPT_X, y + BTN_Y, BTN_W, BTN_H));
        drawButton(g, x + DECLINE_X, y + BTN_Y, 80, "Decline", inside(mx, my, x + DECLINE_X, y + BTN_Y, BTN_W, BTN_H));
    }

    private void drawButton(GuiGraphics g, int x, int y, int u, String label, boolean hover) {
        g.blit(WIDGETS, x, y, u, hover ? 20 : 0, BTN_W, BTN_H, 256, 64);
        int tx = x + 20 + (BTN_W - 24 - font.width(label)) / 2;
        g.drawString(font, label, tx, y + 6, 0xFFFFFF, true);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (inside(mx, my, leftPos + ACCEPT_X, topPos + BTN_Y, BTN_W, BTN_H)) { send(true); return true; }
            if (inside(mx, my, leftPos + DECLINE_X, topPos + BTN_Y, BTN_W, BTN_H)) { send(false); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void send(boolean accept) {
        minecraft.getSoundManager().play(accept
                ? SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f)
                : SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f));
        PacketDistributor.sendToServer(new ConfirmDecisionPayload(menu.containerId, accept));
    }
}