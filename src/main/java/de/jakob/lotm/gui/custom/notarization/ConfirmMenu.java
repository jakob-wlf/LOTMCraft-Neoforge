package de.jakob.lotm.gui.custom.notarization;

import de.jakob.lotm.beyonders.abilities.sun.notarization.PendingAccess;
import de.jakob.lotm.gui.ModMenuTypes;
import de.jakob.lotm.beyonders.abilities.sun.notarization.net.ConfirmData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ConfirmMenu extends AbstractContainerMenu {
    private final ConfirmData data;
    private final UUID contractId; // null on the client

    public ConfirmMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, ConfirmData.read(buf), null);
    }

    public ConfirmMenu(int id, ConfirmData data, UUID contractId) {
        super(ModMenuTypes.CONFIRM_CONTRACT.get(), id);
        this.data = data;
        this.contractId = contractId;
    }

    public ConfirmData data() { return data; }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        if (contractId == null) return true;
        return player.isAlive() && PendingAccess.stillWaiting(contractId, player.getUUID());
    }
}