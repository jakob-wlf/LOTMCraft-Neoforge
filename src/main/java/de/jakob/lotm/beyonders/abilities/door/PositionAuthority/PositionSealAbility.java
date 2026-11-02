package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.door.SealingAbility;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedList;
import java.util.List;

public class PositionSealAbility extends PositionAuthorityAbility {

    public PositionSealAbility(String id) {
        super(id, 30);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(10, 20, 30));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(4000f, 9000f, 16000f));
    }

    @Override
    protected float getSpiritualityCost() {
        return 16000;
    }

    @Override
    protected String command() {
        return "seal";
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq) {
        if (LOTMCraft.abilityHandler.getById("sealing_ability") instanceof SealingAbility sealing) {
            sealing.seal(caster, target.serverLevel(), target.position().add(0, target.getBbHeight() / 2, 0), seq);
        }
    }
}
