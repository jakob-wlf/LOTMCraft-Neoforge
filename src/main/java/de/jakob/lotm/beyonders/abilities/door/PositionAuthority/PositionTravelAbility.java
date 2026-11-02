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

public class PositionTravelAbility extends PositionAuthorityAbility {

    public PositionTravelAbility(String id) {
        super(id, 20);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(5, 10, 20));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(2000f, 5000f, 10000f));
    }

    @Override
    protected float getSpiritualityCost() {
        return 10000;
    }

    @Override
    protected String command() {
        return "to";
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq) {
        ServerLevel level = target.serverLevel();
        caster.teleportTo(level, target.getX(), target.getY(), target.getZ(), Set.of(), caster.getYRot(), caster.getXRot());
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        EffectManager.playEffect(EffectIds.WAYPOINT, target.getX(), target.getY() + 1, target.getZ(), level);
    }
}
