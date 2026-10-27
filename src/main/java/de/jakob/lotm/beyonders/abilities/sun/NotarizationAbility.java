package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.sun.notarization.ability.NotaryAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Map;

public class NotarizationAbility extends SelectableAbility {
    public NotarizationAbility(String id) {
        super(id, 1);

        canBeCopied = false;
        canBeReplicated = false;
        cannotBeStolen = true;
        canBeUsedByNPC = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return Map.of("sun", 6);
    }

    @Override
    protected float getSpiritualityCost() {
        return 100;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{"ability.lotmcraft.notarization.contract", "ability.lotmcraft.notarization.investigate"};
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if(!(entity instanceof ServerPlayer serverPlayer)) return;
        switch (selectedAbility) {
            case 0 -> NotaryAbilities.openContractMenu(serverPlayer);
            case 1 -> NotaryAbilities.authenticate(serverPlayer);
        }
    }
}
