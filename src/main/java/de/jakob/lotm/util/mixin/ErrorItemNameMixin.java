package de.jakob.lotm.util.mixin;

import de.jakob.lotm.beyonders.abilities.error.handler.ErrorMixinHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ErrorItemNameMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void lotmcraft$deceivedName(CallbackInfoReturnable<Component> cir) {
        Component name = ErrorMixinHooks.getDisguisedName((ItemStack) (Object) this);
        if (name != null) {
            cir.setReturnValue(name);
        }
    }
}
