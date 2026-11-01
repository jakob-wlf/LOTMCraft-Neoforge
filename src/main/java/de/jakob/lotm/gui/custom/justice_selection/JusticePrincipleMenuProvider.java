package de.jakob.lotm.gui.custom.justice_selection;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public class JusticePrincipleMenuProvider implements MenuProvider {
    @Override
    public Component getDisplayName() {
        return Component.translatable("justice.lotmcraft.menu.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new JusticePrincipleMenu(containerId, inventory, (ServerPlayer) player);
    }
}
