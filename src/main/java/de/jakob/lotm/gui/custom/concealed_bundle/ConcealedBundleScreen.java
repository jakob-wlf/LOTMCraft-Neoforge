package de.jakob.lotm.gui.custom.concealed_bundle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ConcealedBundleScreen extends AbstractContainerScreen<ConcealedBundleMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL_COLOR = 0xFFC6C6C6;

    private final int rows;

    public ConcealedBundleScreen(ConcealedBundleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = menu.rows();
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.pages() <= 1) return;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(0))
                .bounds(leftPos + imageWidth + 2, topPos + 17, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(leftPos + imageWidth + 2, topPos + 39, 20, 20).build());
    }

    private void turn(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int top = rows * 18 + 17;
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, top);
        g.blit(TEXTURE, leftPos, topPos + top, 0, 126, imageWidth, 96);

        for (int i = 0; i < rows * ConcealedBundleMenu.COLS; i++) {
            if (menu.slots.get(i).isActive()) continue;
            int x = leftPos + 7 + (i % ConcealedBundleMenu.COLS) * 18;
            int y = topPos + 17 + (i / ConcealedBundleMenu.COLS) * 18;
            g.fill(x, y, x + 18, y + 18, PANEL_COLOR);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (menu.pages() <= 1) return;
        String page = (menu.page() + 1) + "/" + menu.pages();
        g.drawString(font, page, imageWidth - 8 - font.width(page), titleLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
