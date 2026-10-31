package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.visionary.handlers.VisionaryHandler;
import de.jakob.lotm.network.packets.toClient.LuckPerceptionPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LuckSenseAbility extends PassiveAbility {

    private static final double SCAN_RADIUS = 24;
    private static final int MAX_ENTRIES = 48;

    public LuckSenseAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel) || !(entity instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;

        int seq = BeyonderData.getSequence(player);
        List<LuckPerceptionPacket.Entry> entries = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SCAN_RADIUS),
                        e -> e != player && e.isAlive() && !(e instanceof ArmorStand)
                                && e.distanceToSqr(player) <= SCAN_RADIUS * SCAN_RADIUS
                                && !VisionaryHandler.shouldStayInvisible(seq, e)
                                && !AbilityUtil.isTargetSignificantlyStronger(seq, BeyonderData.getSequence(e)))
                .stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .limit(MAX_ENTRIES)
                .map(e -> new LuckPerceptionPacket.Entry(e.getId(), e.getData(ModAttachments.LUCK_COMPONENT.get()).getLuck()))
                .toList();

        PacketDistributor.sendToPlayer(player, new LuckPerceptionPacket(true, player.getData(ModAttachments.LUCK_COMPONENT.get()).getLuck(), entries));
    }

    @Override
    public void onPassiveAbilityRemoved(LivingEntity entity, ServerLevel serverLevel) {
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, LuckPerceptionPacket.inactive());
        }
    }
}
