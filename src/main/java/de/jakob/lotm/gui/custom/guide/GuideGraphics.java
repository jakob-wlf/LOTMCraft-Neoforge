package de.jakob.lotm.gui.custom.guide;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuideGraphics {
    public static final int INK = 0xFF2E1F0E;
    public static final int INK_TEXT = 0xFF45351F;
    public static final int INK_SOFT = 0xFF7A6544;
    public static final int INK_FAINT = 0x55805030;
    public static final int GOLD = 0xFFB5841A;
    public static final int GOLD_DARK = 0xFF8C6D12;

    public static final float TEXT_SCALE = 0.75f;
    public static final int LINE_H = 8;
    public static final int SLOT = 18;

    public static final ResourceLocation COVER = tex("cover");
    public static final ResourceLocation PAGE_LEFT = tex("page_left");
    public static final ResourceLocation PAGE_RIGHT = tex("page_right");
    public static final ResourceLocation FRAME = tex("frame");
    public static final ResourceLocation DIVIDER = tex("divider");
    public static final ResourceLocation EMBLEM = tex("emblem");
    public static final ResourceLocation RIBBON = tex("ribbon");
    public static final ResourceLocation TAB = tex("tab");
    public static final ResourceLocation SLOT_TEX = tex("slot");
    public static final ResourceLocation CIRCLE = tex("circle");
    public static final ResourceLocation NOTE = tex("note");
    public static final ResourceLocation SEAL = tex("seal");
    public static final ResourceLocation KEYCAP = tex("keycap");
    public static final ResourceLocation ARROWS = tex("arrows");
    public static final ResourceLocation STAMP = tex("stamp");
    public static final ResourceLocation CRAFT_ARROW = tex("craft_arrow");

    public static final int DIVIDER_W = 120, DIVIDER_H = 9;

    private static final Map<EntityType<?>, Entity> ENTITY_CACHE = new HashMap<>();

    public final GuiGraphics g;
    public final Font font;
    public final GuideBookScreen screen;
    public final int mouseX, mouseY;
    /** Seconds since the book was opened, for animations. */
    public final float time;

    GuideGraphics(GuiGraphics g, Font font, GuideBookScreen screen, int mouseX, int mouseY, float time) {
        this.g = g;
        this.font = font;
        this.screen = screen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.time = time;
    }

    public static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/guide/" + name + ".png");
    }

    public boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void tooltip(int x, int y, int w, int h, List<Component> lines) {
        if (hovered(x, y, w, h)) screen.setTooltip(lines);
    }

    public void tooltip(int x, int y, int w, int h, ItemStack stack) {
        if (!stack.isEmpty() && hovered(x, y, w, h)) screen.setTooltip(stack);
    }

    public void click(int x, int y, int w, int h, Runnable action) {
        screen.addClick(x, y, w, h, action);
    }

    public void text(FormattedCharSequence t, float x, float y, float scale, int color) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0f);
        ps.scale(scale, scale, 1f);
        g.drawString(font, t, 0, 0, color, false);
        ps.popPose();
    }

    public void text(Component t, float x, float y, float scale, int color) {
        text(t.getVisualOrderText(), x, y, scale, color);
    }

    public void centered(FormattedCharSequence t, float cx, float y, float scale, int color) {
        text(t, cx - font.width(t) * scale / 2f, y, scale, color);
    }

    public void centered(Component t, float cx, float y, float scale, int color) {
        centered(t.getVisualOrderText(), cx, y, scale, color);
    }

    public List<FormattedCharSequence> wrap(Component t, int width, float scale) {
        return font.split(t, (int) (width / scale));
    }

    public void blit(ResourceLocation tex, int x, int y, int w, int h) {
        blit(tex, x, y, w, h, 0, 0, w, h, w, h);
    }

    public void blit(ResourceLocation tex, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH);
    }

    public void tinted(int argb, Runnable draw) {
        RenderSystem.setShaderColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        draw.run();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public void nineSlice(ResourceLocation tex, int x, int y, int w, int h, int corner, int texSize) {
        int c = corner, mid = texSize - 2 * c, iw = w - 2 * c, ih = h - 2 * c;
        blit(tex, x, y, c, c, 0, 0, c, c, texSize, texSize);
        blit(tex, x + w - c, y, c, c, texSize - c, 0, c, c, texSize, texSize);
        blit(tex, x, y + h - c, c, c, 0, texSize - c, c, c, texSize, texSize);
        blit(tex, x + w - c, y + h - c, c, c, texSize - c, texSize - c, c, c, texSize, texSize);
        if (iw > 0) {
            blit(tex, x + c, y, iw, c, c, 0, mid, c, texSize, texSize);
            blit(tex, x + c, y + h - c, iw, c, c, texSize - c, mid, c, texSize, texSize);
        }
        if (ih > 0) {
            blit(tex, x, y + c, c, ih, 0, c, c, mid, texSize, texSize);
            blit(tex, x + w - c, y + c, c, ih, texSize - c, c, c, mid, texSize, texSize);
        }
        if (iw > 0 && ih > 0) blit(tex, x + c, y + c, iw, ih, c, c, mid, mid, texSize, texSize);
    }

    public void divider(int cx, int cy) {
        blit(DIVIDER, cx - DIVIDER_W / 2, cy - DIVIDER_H / 2, DIVIDER_W, DIVIDER_H);
    }

    public void circle(int cx, int cy, int size, boolean flat) {
        int h = flat ? size / 3 : size;
        blit(CIRCLE, cx - size / 2, cy - h / 2, size, h, 0, 0, 96, 96, 96, 96);
    }

    public void slot(int x, int y) {
        blit(SLOT_TEX, x, y, SLOT, SLOT);
    }

    public void item(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
    }

    public void slotItem(ItemStack stack, int x, int y) {
        slot(x, y);
        item(stack, x + 1, y + 1);
        tooltip(x, y, SLOT, SLOT, stack);
    }

    public <T> T cycle(List<T> options, float seconds) {
        return options.get((int) (time / seconds) % options.size());
    }

    public void model(ItemStack stack, float cx, float cy, float size, float yaw, float pitch) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        boolean flat = !model.usesBlockLight();

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 150f + size);
        ps.scale(size, -size, size);
        if (!flat) ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));

        g.flush();
        if (flat) Lighting.setupForFlatItems();
        else Lighting.setupFor3DItems();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, 0xF000F0, OverlayTexture.NO_OVERLAY,
                ps, g.bufferSource(), mc.level, 0);
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    @Nullable
    public static LivingEntity entity(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (!ENTITY_CACHE.containsKey(type)) {
            Entity created = null;
            try {
                created = type.create(mc.level);
            } catch (Exception ex) {
                LOTMCraft.LOGGER.warn("Guide book could not create entity {}", type, ex);
            }
            ENTITY_CACHE.put(type, created);
        }
        return ENTITY_CACHE.get(type) instanceof LivingEntity living ? living : null;
    }

    static void clearEntityCache() {
        ENTITY_CACHE.clear();
    }

    public void entity(LivingEntity e, int x, int y, int w, int h, float yaw) {
        float bbH = Math.max(0.3f, e.getBbHeight());
        float bbW = Math.max(0.3f, e.getBbWidth());
        float scale = Math.min(h / bbH, w / (bbW * 1.4f)) * 0.9f;

        e.tickCount = (int) (time * 20);
        e.setYBodyRot(180f + yaw);
        e.yBodyRotO = 180f + yaw;
        e.setYRot(180f + yaw);
        e.yRotO = 180f + yaw;
        e.setYHeadRot(180f + yaw);
        e.yHeadRotO = 180f + yaw;
        e.setXRot(0f);
        e.xRotO = 0f;

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-8));
        pose.mul(camera);

        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        try {
            InventoryScreen.renderEntityInInventory(g, x + w / 2f, y + h / 2f, scale,
                    new Vector3f(0f, bbH / 2f, 0f), pose, camera, e);
        } catch (Exception ex) {
            LOTMCraft.LOGGER.debug("Guide book failed to render {}", e.getType(), ex);
        }
        g.pose().popPose();
    }
}
