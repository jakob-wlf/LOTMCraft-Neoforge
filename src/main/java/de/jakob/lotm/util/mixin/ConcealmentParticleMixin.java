package de.jakob.lotm.util.mixin;

import de.jakob.lotm.network.packets.handlers.ClientHandler;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ConcealmentParticleMixin {
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void lotm$hideConcealedParticle(Particle particle, CallbackInfo ci) {
        AABB box = particle.getBoundingBox();
        if (box == null) return;
        Vec3 center = box.getCenter();
        if (ClientHandler.concealmentMutes(center.x, center.y, center.z)) ci.cancel();
    }
}
