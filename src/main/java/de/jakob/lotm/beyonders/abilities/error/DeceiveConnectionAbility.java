package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.DeceptionTrapHandler;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;

public class DeceiveConnectionAbility extends SelectableAbility {

    public DeceiveConnectionAbility(String id) {
        super(id, 3);
        canBeUsedInArtifact = false;

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3000f, 2000f, 1500f, 1200f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 1200;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.deceive_connection.setup",
                "ability.lotmcraft.deceive_connection.trap"
        };
    }

    @Override
    public float getInflatedSpiritualityCost(LivingEntity entity, ServerLevel level, int seq) {
        return getSelectedAbilityIndex(entity.getUUID()) == 0 ? 0 : super.getInflatedSpiritualityCost(entity, level, seq);
    }

    public static double getRadius(int seq) {
        return switch (seq) {
            case 0 -> 80;
            case 1 -> 60;
            case 2 -> 45;
            default -> 32;
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(entity instanceof ServerPlayer player)) {
            if (entity instanceof Player player && level.isClientSide) player.playSound(SoundEvents.BELL_RESONATE, 1, 0.7f);
            return;
        }

        if (selectedAbility == 0) {
            CompoundTag data = DeceptionTrapHandler.getConfigTag(player).copy();
            ErrorUtil.send(player, "trap_setup", data);
            return;
        }

        double radius = getRadius(AbilityUtil.getSeqWithArt(player, this));
        boolean active = DeceptionTrapHandler.toggle(player, radius);
        if (active) {
            FxHandler.play(player.serverLevel(), "deceit_wave", player.position().add(0, 0.1, 0), radius / 20.0);
        }
        player.serverLevel().playSound(null, player.blockPosition(), active ? SoundEvents.END_PORTAL_FRAME_FILL : SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8f, 0.6f);
        AbilityUtil.sendActionBar(player, Component.translatable(active ? "ability.lotmcraft.deceive_connection.on" : "ability.lotmcraft.deceive_connection.off")
                .withColor(ErrorUtil.DECEIT_COLOR));
    }
}
