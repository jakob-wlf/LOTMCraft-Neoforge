package de.jakob.lotm.rendering;
import de.jakob.lotm.LOTMCraft;
import com.mojang.blaze3d.systems.RenderSystem;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.util.ClientBeyonderCache;
import de.jakob.lotm.util.helper.AbilityBarHelper;
import de.jakob.lotm.util.helper.AbilityId;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import de.jakob.lotm.attachments.AbilityCooldownComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.RequestAbilityBarPacket;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value=Dist.CLIENT)
public class AbilityHotBarRenderer{
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event){
        event.registerAbove(VanillaGuiLayers.HOTBAR,
        ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "ability_hotbar"),
        AbilityHotBarRenderer::render);
    }

    private static ResourceLocation introspectSprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "introspect/" + name);
    }

    private static final ResourceLocation DRAWER_SPRITE = introspectSprite("drawer");
    private static final ResourceLocation SLOT_SPRITE = introspectSprite("slot_idle");
    private static final ResourceLocation SLOT_EMPTY_SPRITE = introspectSprite("slot_empty");
    private static final ResourceLocation SLOT_COPIED_SPRITE = introspectSprite("slot_copied");
    private static final int SLOT_SIZE = 18;

    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SLOTS_PER_ROW = 9;
    private static final int ICON_SIZE = 16;

    // Height of the second row; vanilla status bars move up by this much while the bar is shown
    public static final int EXTRA_HEIGHT = HOTBAR_HEIGHT;

    private static final Set<ResourceLocation> SHIFTED_LAYERS = Set.of(
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.VEHICLE_HEALTH,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.JUMP_METER,
            VanillaGuiLayers.SELECTED_ITEM_NAME
    );

    private static boolean posePushed = false;

    private static final int RAIL_WIDTH = 24;
    private static final int RAIL_HEIGHT = 48;
    private static final Map<String, ResourceLocation> RAILS = new HashMap<>();
    static {
        String[] pathways = {"abyss", "darkness", "death", "demoness", "door", "error", "fool", "justiciar",
                "mother", "red_priest", "sun", "twilight_giant", "tyrant", "visionary", "wheel_of_fortune"};
        for (String pathway : pathways) {
            RAILS.put(pathway, ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/gui/rails/" + pathway + ".png"));
        }
    }

    private static boolean enabled = false;

    public static void toggle(){
        enabled = !enabled;
        if (enabled)
            PacketHandler.sendToServer(new RequestAbilityBarPacket());
    }

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return enabled && mc.player != null && ClientBeyonderCache.isBeyonder(mc.player.getUUID());
    }

    /** How far the rails stick out past each end of the bar; HUD widgets beside the bar move sideways by this much. */
    public static int getSideOffset() {
        if (!isActive()) return 0;
        String pathway = ClientBeyonderCache.getPathway(Minecraft.getInstance().player.getUUID());
        return RAILS.containsKey(pathway) ? RAIL_WIDTH : 0;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) 
    { 
        enabled = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!isActive()) return;

        ResourceLocation name = event.getName();
        if (name.equals(VanillaGuiLayers.HOTBAR)) {
            event.setCanceled(true);
        } else if (SHIFTED_LAYERS.contains(name)) {
            GuiGraphics g = event.getGuiGraphics();
            g.pose().pushPose();
            g.pose().translate(0, -EXTRA_HEIGHT, 0);
            posePushed = true;
        }
    }

    @SubscribeEvent
    public static void onRenderLayerPost(RenderGuiLayerEvent.Post event) {
        if (posePushed && SHIFTED_LAYERS.contains(event.getName())) {
            event.getGuiGraphics().pose().popPose();
            posePushed = false;
        }
    }

    private static void render(GuiGraphics g, DeltaTracker dt){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        if (!isActive()) return;

        if(!ClientBeyonderCache.isBeyonder(mc.player.getUUID())){
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int left = (screenW - HOTBAR_WIDTH) / 2;

        List<String> abilities = AbilityBarHelper.getAbilities(mc.player);
        int rows = AbilityBarHelper.MAX_SLOTS / SLOTS_PER_ROW;
        RenderSystem.enableBlend();
        renderRails(g, mc, left, screenH);
        g.blitSprite(DRAWER_SPRITE, left, screenH - HOTBAR_HEIGHT * rows, HOTBAR_WIDTH, HOTBAR_HEIGHT * rows);

        for (int row = 0; row < rows; row++) {
            int top = screenH - HOTBAR_HEIGHT * (rows - row);

            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int slot = row * SLOTS_PER_ROW + col;
                int x = left + 3 + col * 20;
                int y = top + 3;

                String abilityId = slot < abilities.size() ? abilities.get(slot) : null;
                ResourceLocation slotSprite = abilityId == null ? SLOT_EMPTY_SPRITE
                        : AbilityId.parse(abilityId).copied() ? SLOT_COPIED_SPRITE : SLOT_SPRITE;
                g.blitSprite(slotSprite, x - 1, y - 1, SLOT_SIZE, SLOT_SIZE);

                if (abilityId != null) {
                    renderAbility(g, mc, abilityId, x, y);
                }
            }
        }
        RenderSystem.disableBlend();
    }

    private static void renderRails(GuiGraphics g, Minecraft mc, int left, int screenH) {
        String pathway = ClientBeyonderCache.getPathway(mc.player.getUUID());
        ResourceLocation rail = RAILS.get(pathway);
        if (rail == null) return;

        int y = screenH - RAIL_HEIGHT;
        g.blit(rail, left - RAIL_WIDTH, y, 0, 0, RAIL_WIDTH, RAIL_HEIGHT, RAIL_WIDTH, RAIL_HEIGHT);
        g.blit(rail, left + HOTBAR_WIDTH, y, RAIL_WIDTH, RAIL_HEIGHT, RAIL_WIDTH, 0, -RAIL_WIDTH, RAIL_HEIGHT, RAIL_WIDTH, RAIL_HEIGHT);
    }

     private static void renderAbility(GuiGraphics g, Minecraft mc, String abilityId, int x, int y) {
        Ability ability = LOTMCraft.abilityHandler.getById(abilityId.split(":")[0]);
        if (ability == null) return;

        g.blit(ability.getTextureLocation(), x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        renderCooldown(g, mc, ability, x, y);
    }

    private static void renderCooldown(GuiGraphics g, Minecraft mc, Ability ability, int x, int y){
        AbilityCooldownComponent cooldowns = mc.player.getData(ModAttachments.COOLDOWN_COMPONENT);
        int remainingTicks = cooldowns.getRemainingCooldown(ability.getId());
        if (remainingTicks <= 0) return;

        int total = ability.getCooldown(ClientBeyonderCache.getSequence(mc.player.getUUID()));
        float fraction = Math.min(1f, remainingTicks / (float) total);
        int top = y + Math.round(ICON_SIZE * (1f - fraction));
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        g.fill(x, top, x + ICON_SIZE, y + ICON_SIZE, 0x99000000);

        int seconds = (remainingTicks + 19) / 20;
        String text = seconds >= 60 ? (seconds / 60) + "m" : String.valueOf(seconds);
        int tx = x + (ICON_SIZE - mc.font.width(text)) / 2;
        int ty = y + (ICON_SIZE - mc.font.lineHeight) / 2 + 1;
        g.drawString(mc.font, text, tx, ty, 0xFFFFFFFF, true);
        g.pose().popPose();
    }
}