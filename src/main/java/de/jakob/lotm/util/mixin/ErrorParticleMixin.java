package de.jakob.lotm.util.mixin;

import de.jakob.lotm.beyonders.abilities.error.handler.ErrorMixinHooks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ErrorParticleMixin {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void lotmcraft$feebleEffects(Particle particle, CallbackInfo ci) {
        if (ErrorMixinHooks.shouldSwallowParticle()) {
            ci.cancel();
        }
    }
}
