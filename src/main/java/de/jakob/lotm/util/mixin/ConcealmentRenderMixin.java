package de.jakob.lotm.util.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class ConcealmentRenderMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void lotm$hideConcealed(Entity entity, double x, double y, double z, float yaw, float partial, PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (Minecraft.getInstance().player == entity) return;
        if (ClientHandler.concealmentHides(entity.getX(), entity.getY(), entity.getZ())) ci.cancel();
    }
}
