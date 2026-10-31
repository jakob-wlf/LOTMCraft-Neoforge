package de.jakob.lotm.util.mixin;

import de.jakob.lotm.rendering.error.ErrorInventoryDeceit;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Inventory Deceit: the deceived player's inventory screen shows what the Mentor of Deceit prepared.
@Mixin(AbstractContainerScreen.class)
public abstract class ErrorInventoryScreenMixin {

    @Redirect(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack lotmcraft$deceivedSlot(Slot slot) {
        return ErrorInventoryDeceit.getShownItem(this, slot);
    }

    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack lotmcraft$deceivedTooltip(Slot slot) {
        return ErrorInventoryDeceit.getShownItem(this, slot);
    }
}
