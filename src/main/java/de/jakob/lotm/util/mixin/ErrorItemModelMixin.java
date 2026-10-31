package de.jakob.lotm.util.mixin;

import de.jakob.lotm.beyonders.abilities.error.handler.ErrorMixinHooks;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Item Deceit: the deceived player sees a different texture on the item they hold.
@Mixin(ItemRenderer.class)
public abstract class ErrorItemModelMixin {

    @ModifyVariable(method = "getModel", at = @At("HEAD"), argsOnly = true)
    private ItemStack lotmcraft$deceivedModel(ItemStack stack) {
        return ErrorMixinHooks.getDisguisedStack(stack);
    }
}
