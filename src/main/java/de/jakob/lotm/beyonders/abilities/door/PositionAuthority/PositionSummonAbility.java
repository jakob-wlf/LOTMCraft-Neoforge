package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.rendering.effectRendering.EffectIds;
import de.jakob.lotm.rendering.effectRendering.EffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class PositionSummonAbility extends PositionAuthorityAbility {

    public PositionSummonAbility(String id) {
        super(id, 30);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(5, 15, 30));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3000f, 8000f, 15000f));
    }

    @Override
    protected float getSpiritualityCost() {
        return 15000;
    }

    @Override
    protected String command() {
        return "to me";
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq) {
        ServerLevel level = caster.serverLevel();
        target.teleportTo(level, caster.getX(), caster.getY(), caster.getZ(), Set.of(), target.getYRot(), target.getXRot());
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        EffectManager.playEffect(EffectIds.WAYPOINT, caster.getX(), caster.getY() + 1, caster.getZ(), level);
    }
}
