package de.jakob.lotm.util.mixin;

import de.jakob.lotm.rendering.error.ErrorClientState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


// Decryption: low level invisibility flickers and shows a translucent silhouette.
@Mixin(Entity.class)
public abstract class ErrorInvisibilityMixin {

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void lotmcraft$decryptionReveal(Player player, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && ErrorClientState.revealed.containsKey(self.getId())) {
            cir.setReturnValue(false);
        }
    }
}
