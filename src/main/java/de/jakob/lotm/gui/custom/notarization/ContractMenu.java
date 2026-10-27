package de.jakob.lotm.gui.custom.notarization;

import de.jakob.lotm.beyonders.abilities.sun.notarization.net.Candidate;
import de.jakob.lotm.gui.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ContractMenu extends AbstractContainerMenu {
    private final List<Candidate> candidates;

    public ContractMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, Candidate.LIST_CODEC.decode(buf));
    }

    public ContractMenu(int id, List<Candidate> candidates) {
        super(ModMenuTypes.CONTRACT.get(), id);
        this.candidates = candidates;
    }

    public List<Candidate> candidates() { return candidates; }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player.isAlive() && !player.isSpectator(); }
}
