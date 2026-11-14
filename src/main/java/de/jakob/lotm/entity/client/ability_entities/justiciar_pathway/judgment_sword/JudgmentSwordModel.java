package de.jakob.lotm.entity.client.ability_entities.justiciar_pathway.judgment_sword;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class JudgmentSwordModel {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            LOTMCraft.MOD_ID, "models/entity/judgment_sword.json");
    private static float[] vertices;

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> vertices = null);
    }

    private static float[] geometry() {
        if (vertices != null) return vertices;
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(MODEL)) {
            var array = JsonParser.parseReader(reader).getAsJsonArray();
            float[] loaded = new float[array.size() * 8];
            for (int i = 0; i < array.size(); i++) {
                var row = array.get(i).getAsJsonArray();
                for (int j = 0; j < 8; j++) loaded[i * 8 + j] = row.get(j).getAsFloat();
            }
            return vertices = loaded;
        } catch (Exception error) {
            LOTMCraft.LOGGER.error("Unable to load Sword of Judgment model", error);
            return vertices = new float[0];
        }
    }

    public static void render(PoseStack stack, VertexConsumer buffer, int light) {
        float[] mesh = geometry();
        for (int i = 0; i < mesh.length; i += 8) {
            buffer.addVertex(stack.last().pose(), mesh[i], mesh[i + 1], mesh[i + 2])
                    .setColor(255, 255, 255, 255).setUv(mesh[i + 3], mesh[i + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(stack.last(), mesh[i + 5], mesh[i + 6], mesh[i + 7]);
        }
    }
}
