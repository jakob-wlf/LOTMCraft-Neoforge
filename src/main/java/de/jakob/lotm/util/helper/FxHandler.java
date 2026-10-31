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
import org.joml.Quaternionf;

import java.util.UUID;

public final class FxHandler {

    private static final double RANGE = 160;

    private FxHandler() {
    }

    public static void play(ServerLevel level, String fx, Vec3 pos, double scale) {
        play(level, fx, pos, scale, null);
    }

    public static void play(ServerLevel level, String fx, Vec3 pos, double scale, Quaternionf rotation) {
        BlockPos blockPos = BlockPos.containing(pos);
        PacketHandler.sendToNearbyPlayers(new PlayPhotonBlockEffectPacket(fx, blockPos,
                pos.x - blockPos.getX() - 0.5, pos.y - blockPos.getY() - 0.5, pos.z - blockPos.getZ() - 0.5,
                scale, rotation, -1, false, true, null), level, pos, RANGE);
    }

    /** Rotation that turns an effect authored along +Y towards {@code direction}. */
    public static Quaternionf pointing(Vec3 direction) {
        Vec3 dir = direction.normalize();
        return new Quaternionf().rotationTo(0, 1, 0, (float) dir.x, (float) dir.y, (float) dir.z);
    }

    /** Rotation that turns an effect authored facing +Z (local Y up) along an entity's view. */
    public static Quaternionf facing(Entity entity, float extraYawDegrees) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-entity.getYRot()))
                .rotateX((float) Math.toRadians(entity.getXRot()))
                .rotateY((float) Math.toRadians(extraYawDegrees));
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

    /** Plays an effect centred on the entity, sized to its height (authored for a 1.8 block tall body). */
    public static UUID playOnBody(ServerLevel level, String fx, Entity entity) {
        return playOnEntity(level, fx, entity, entity.getBbHeight() * 0.5, Math.max(0.6, entity.getBbHeight() / 1.8));
    }

    public static void cancelOnEntity(ServerLevel level, UUID id) {
        if (id != null) PacketHandler.sendToAllPlayersInSameLevel(new CancelPhotonEntityEffectPacket(id), level);
    }
}
