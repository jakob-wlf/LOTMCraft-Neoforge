package de.jakob.lotm.rendering.error;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.ClientBeyonderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ArmorCrackLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation[] CRACKS = {
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/armor_cracks_1.png"),
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/armor_cracks_2.png"),
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/armor_cracks_3.png"),
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "textures/entity/armor_cracks_4.png")
    };

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public ArmorCrackLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    private static boolean canSeeCracks(LivingEntity entity) {
        Player player = Minecraft.getInstance().player;
        if (player == null || entity == player) return false;
        if (!"error".equals(ClientBeyonderCache.getPathway(player.getUUID()))) return false;
        if (ClientBeyonderCache.getSequence(player.getUUID()) > 7) return false;
        return entity.distanceToSqr(player) <= 20 * 20;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(getParentModel() instanceof HumanoidModel<?> model) || !canSeeCracks(entity) || entity.isInvisible()) return;

        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) continue;

            float worn = stack.getDamageValue() / (float) stack.getMaxDamage();
            if (worn < 0.12f) continue;
            int stage = Math.min(3, (int) (worn * 4));

            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(CRACKS[stage]));
            List<ModelPart> parts = switch (slot) {
                case HEAD -> List.of(model.head);
                case CHEST -> List.of(model.body, model.rightArm, model.leftArm);
                case LEGS -> List.of(model.body, model.rightLeg, model.leftLeg);
                default -> List.of(model.rightLeg, model.leftLeg);
            };
            float inflate = slot == EquipmentSlot.LEGS ? 1.07f : 1.14f;

            for (ModelPart part : parts) {
                if (!part.visible) continue;
                float sx = part.xScale, sy = part.yScale, sz = part.zScale;
                part.xScale = sx * inflate;
                part.yScale = sy * inflate;
                part.zScale = sz * inflate;
                part.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                part.xScale = sx;
                part.yScale = sy;
                part.zScale = sz;
            }
        }
    }
}
