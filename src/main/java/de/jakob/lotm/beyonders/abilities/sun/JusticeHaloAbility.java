package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeEnforcement;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeManager;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.PlayPhotonBlockEffectPacket;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JusticeHaloAbility extends Ability {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double BASE_RADIUS = 28;
    private static final int BASE_DURATION = 20 * 45;
    private static final double HALO_HEIGHT = 36;

    private final DustParticleOptions haloDust = new DustParticleOptions(new Vector3f(1f, 0.78f, 0.18f), 3f);
    private final DustParticleOptions groundDust = new DustParticleOptions(new Vector3f(1f, 0.85f, 0.35f), 1.6f);

    public JusticeHaloAbility(String id) {
        super(id, 90, "light_source", "purification", "light_strong");

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(60, 75, 90, 120));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(3000f, 1800f, 1200f, 900f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 900;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        ServerLevel serverLevel = (ServerLevel) level;

        double scale = JusticeManager.scale(player);
        double radius = BASE_RADIUS * scale;
        int duration = (int) (BASE_DURATION * scale);
        Vec3 center = player.position();

        JusticeEnforcement.startHalo(player, serverLevel, center, radius, duration);
        level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, player.getSoundSource(), 2f, .7f);

        PacketHandler.sendToNearbyPlayers(new PlayPhotonBlockEffectPacket(
                "justice_halo", BlockPos.containing(center), 0, 11, 0, 4, null, -1, false, true, null
        ), serverLevel, center, 256);

        ServerScheduler.scheduleDelayed(duration, () -> {
            JusticeEnforcement.endHalo(player.getUUID());
        }, serverLevel, () -> AbilityUtil.getTimeInArea(player, new Location(center, level)));
    }
}
