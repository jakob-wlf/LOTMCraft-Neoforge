package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.gui.custom.justice_selection.JusticePrincipleMenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class SetOfPrinciplesAbility extends Ability {
    public SetOfPrinciplesAbility(String id) {
        super(id, 1);

        canBeCopied = false;
        canBeUsedByNPC = false;
        canBeShared = false;
        canBeReplicated = false;
        canBeUsedInArtifact = false;
        cannotBeStolen = true;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 0;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        player.openMenu(new JusticePrincipleMenuProvider());
    }
}
