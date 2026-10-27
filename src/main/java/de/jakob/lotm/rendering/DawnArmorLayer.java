package de.jakob.lotm.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.jakob.lotm.beyonders.abilities.twilight_giant.ArsenalOfDawnAbility;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public class DawnArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public DawnArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        draw(pose, buffer, light, overlay, model, player, DawnArmorModels.TEXTURE, DawnArmorModels.MODEL,
                ArsenalOfDawnAbility.HELMET, ArsenalOfDawnAbility.CHESTPLATE, ArsenalOfDawnAbility.LEGGINGS, ArsenalOfDawnAbility.BOOTS);
        draw(pose, buffer, light, overlay, model, player, DawnArmorModels.SILVER_TEXTURE, DawnArmorModels.SILVER_MODEL,
                ArsenalOfDawnAbility.SILVER_HELMET, ArsenalOfDawnAbility.SILVER_CHESTPLATE, ArsenalOfDawnAbility.SILVER_LEGGINGS, ArsenalOfDawnAbility.SILVER_BOOTS);
    }

    private static void draw(PoseStack pose, MultiBufferSource buffer, int light, int overlay, PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player, ResourceLocation texture, ResourceLocation mesh, String helmetKind, String chestKind, String legsKind, String bootsKind) {
        boolean helmet = ArsenalOfDawnAbility.is(player.getItemBySlot(EquipmentSlot.HEAD), helmetKind);
        boolean chest = ArsenalOfDawnAbility.is(player.getItemBySlot(EquipmentSlot.CHEST), chestKind);
        boolean legs = ArsenalOfDawnAbility.is(player.getItemBySlot(EquipmentSlot.LEGS), legsKind);
        boolean boots = ArsenalOfDawnAbility.is(player.getItemBySlot(EquipmentSlot.FEET), bootsKind);
        if (!helmet && !chest && !legs && !boots) return;
        VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));
        if (helmet) DawnArmorModels.render(pose, consumer, light, overlay, model.head, mesh, "head");
        if (chest) {
            DawnArmorModels.render(pose, consumer, light, overlay, model.body, mesh, "body");
            DawnArmorModels.render(pose, consumer, light, overlay, model.body, mesh, "waist");
            DawnArmorModels.render(pose, consumer, light, overlay, model.rightArm, mesh, "right_arm");
            DawnArmorModels.render(pose, consumer, light, overlay, model.leftArm, mesh, "left_arm");
        }
        if (legs) {
            DawnArmorModels.render(pose, consumer, light, overlay, model.rightLeg, mesh, "right_leg");
            DawnArmorModels.render(pose, consumer, light, overlay, model.leftLeg, mesh, "left_leg");
        }
        if (boots) {
            DawnArmorModels.render(pose, consumer, light, overlay, model.rightLeg, mesh, "right_shoe");
            DawnArmorModels.render(pose, consumer, light, overlay, model.leftLeg, mesh, "left_shoe");
        }
    }
}
