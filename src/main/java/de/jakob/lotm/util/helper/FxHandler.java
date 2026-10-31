package de.jakob.lotm.util.helper;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.CancelPhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.CancelPhotonEntityEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayCancellablePhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayPhotonBlockEffectPacket;
import de.jakob.lotm.network.packets.toClient.PlayPhotonEntityEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class FxHandler {

    private static final double RANGE = 160;

    private FxHandler() {
    }

    public static void play(ServerLevel level, String fx, Vec3 pos, double scale) {
        BlockPos blockPos = BlockPos.containing(pos);
        PacketHandler.sendToNearbyPlayers(new PlayPhotonBlockEffectPacket(fx, blockPos,
                pos.x - blockPos.getX() - 0.5, pos.y - blockPos.getY() - 0.5, pos.z - blockPos.getZ() - 0.5,
                scale, null, -1, false, true, null), level, pos, RANGE);
    }

    public static UUID playCancellable(ServerLevel level, String fx, Vec3 pos, double scale) {
        UUID id = UUID.randomUUID();
        BlockPos blockPos = BlockPos.containing(pos);
        PacketHandler.sendToNearbyPlayers(new PlayCancellablePhotonBlockEffectPacket(fx, blockPos,
                pos.x - blockPos.getX() - 0.5, pos.y - blockPos.getY() - 0.5, pos.z - blockPos.getZ() - 0.5,
                scale, null, -1, false, true, null, id), level, pos, RANGE);
        return id;
    }

    public static void cancel(ServerLevel level, UUID id) {
        if (id != null) PacketHandler.sendToAllPlayersInSameLevel(new CancelPhotonBlockEffectPacket(id), level);
    }

    public static UUID playOnEntity(ServerLevel level, String fx, Entity entity, double yOffset, double scale) {
        UUID id = UUID.randomUUID();
        PacketHandler.sendToNearbyPlayers(new PlayPhotonEntityEffectPacket(fx, entity.getId(), 0, yOffset, 0, scale,
                null, false, true, id, false), level, entity.position(), RANGE);
        return id;
    }

    public static void cancelOnEntity(ServerLevel level, UUID id) {
        if (id != null) PacketHandler.sendToAllPlayersInSameLevel(new CancelPhotonEntityEffectPacket(id), level);
    }
}
