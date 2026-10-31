package de.jakob.lotm.rendering.error;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ErrorInventoryDeceit {

    private ErrorInventoryDeceit() {
    }

    public static ItemStack getShownItem(Object screen, Slot slot) {
        ItemStack real = slot.getItem();
        if (!(screen instanceof InventoryScreen) || !(slot.container instanceof Inventory)) return real;

        ItemStack[] fake = ErrorClientState.fakeInventory;
        Minecraft mc = Minecraft.getInstance();
        if (fake == null || mc.level == null || ErrorClientState.fakeInventoryUntil < mc.level.getGameTime()) return real;

        int index = slot.getContainerSlot();
        if (index < 0 || index >= 36) return real;
        return fake[index];
    }
}
