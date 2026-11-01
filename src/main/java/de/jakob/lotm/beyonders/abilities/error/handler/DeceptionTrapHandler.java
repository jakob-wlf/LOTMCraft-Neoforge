package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.helper.TeleportationUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DeceptionTrapHandler {

    private static final String CONFIG_TAG = "lotm_deceit_trap";
    private static final double TELEPORT_JUMP = 7.0;

    private static class Trap {
        final UUID owner;
        final double radius;
        final Map<UUID, Vec3> lastPositions = new HashMap<>();
        final Set<UUID> insiders = new HashSet<>();
        final Set<UUID> redirectedThisTick = new HashSet<>();

        Trap(UUID owner, double radius) {
            this.owner = owner;
            this.radius = radius;
        }
    }

    private static final Map<UUID, Trap> traps = new ConcurrentHashMap<>();

    public static void setConfig(ServerPlayer player, @Nullable Vec3 in, @Nullable Vec3 out) {
        CompoundTag tag = new CompoundTag();
        if (in != null) {
            Vec3 clamped = TeleportationUtil.clampToBorder(player.serverLevel(), in);
            tag.putDouble("inX", clamped.x);
            tag.putDouble("inY", clamped.y);
            tag.putDouble("inZ", clamped.z);
        }
        if (out != null) {
            Vec3 clamped = TeleportationUtil.clampToBorder(player.serverLevel(), out);
            tag.putDouble("outX", clamped.x);
            tag.putDouble("outY", clamped.y);
            tag.putDouble("outZ", clamped.z);
        }
        player.getPersistentData().put(CONFIG_TAG, tag);
    }

    public static CompoundTag getConfigTag(ServerPlayer player) {
        return player.getPersistentData().getCompound(CONFIG_TAG);
    }

    @Nullable
    private static Vec3 read(CompoundTag tag, String prefix) {
        if (!tag.contains(prefix + "X")) return null;
        return new Vec3(tag.getDouble(prefix + "X"), tag.getDouble(prefix + "Y"), tag.getDouble(prefix + "Z"));
    }

    public static boolean toggle(ServerPlayer owner, double radius) {
        if (traps.remove(owner.getUUID()) != null) {
            return false;
        }
        traps.put(owner.getUUID(), new Trap(owner.getUUID(), radius));
        return true;
    }

    public static boolean isActive(UUID owner) {
        return traps.containsKey(owner);
    }

    public static void remove(UUID owner) {
        traps.remove(owner);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (traps.isEmpty()) return;

        for (Trap trap : new ArrayList<>(traps.values())) {
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(trap.owner);
            if (owner == null || !owner.isAlive()) {
                traps.remove(trap.owner);
                continue;
            }
            tickTrap(owner, trap);
        }
    }

    private static void tickTrap(ServerPlayer owner, Trap trap) {
        ServerLevel level = owner.serverLevel();
        Vec3 center = owner.position();
        CompoundTag config = getConfigTag(owner);
        Vec3 in = read(config, "in");
        Vec3 out = read(config, "out");
        trap.redirectedThisTick.clear();

        if (level.getGameTime() % 10 == 0) {
            drawBorder(level, center, trap.radius);
        }

        AABB box = AABB.ofSize(center, trap.radius * 2 + 96, trap.radius * 2 + 96, trap.radius * 2 + 96);
        Set<UUID> seen = new HashSet<>();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive())) {
            UUID id = entity.getUUID();
            seen.add(id);

            Vec3 current = entity.position();
            Vec3 previous = trap.lastPositions.put(id, current);
            boolean inside = current.distanceTo(center) <= trap.radius;

            if (previous != null && !entity.isPassenger() && previous.distanceTo(current) > TELEPORT_JUMP && isAffected(owner, entity)) {
                boolean wasInside = previous.distanceTo(center) <= trap.radius;
                if (!wasInside && inside && in != null) {
                    redirect(owner, trap, entity, in, "in");
                    inside = in.distanceTo(center) <= trap.radius;
                } else if (wasInside && !inside && out != null) {
                    redirect(owner, trap, entity, out, "out");
                }
            }

            if (inside) trap.insiders.add(id);
            else trap.insiders.remove(id);
        }

        for (UUID id : new ArrayList<>(trap.insiders)) {
            if (seen.contains(id)) continue;
            trap.insiders.remove(id);
            trap.lastPositions.remove(id);
            Entity raw = level.getEntity(id);
            if (raw instanceof LivingEntity entity && entity.isAlive() && out != null && isAffected(owner, entity)) {
                redirect(owner, trap, entity, out, "out");
            }
        }

        trap.lastPositions.keySet().retainAll(seen);
    }

    private static boolean isAffected(ServerPlayer owner, LivingEntity entity) {
        if (AllyUtil.areAllies(owner, entity)) return false;
        if (!BeyonderData.isBeyonder(entity)) return true;
        return !AbilityUtil.isTargetSignificantlyStronger(BeyonderData.getSequence(owner), BeyonderData.getSequence(entity));
    }

    private static void redirect(ServerPlayer owner, Trap trap, LivingEntity entity, Vec3 destination, String direction) {
        if (!trap.redirectedThisTick.add(entity.getUUID())) return;
        ServerLevel level = owner.serverLevel();

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
        entity.teleportTo(destination.x, destination.y, destination.z);
        trap.lastPositions.put(entity.getUUID(), destination);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, destination.x, destination.y + 1, destination.z, 30, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, entity.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 0.5f);

        owner.displayClientMessage(Component.translatable("ability.lotmcraft.deceive_connection.redirected_" + direction, entity.getName())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private static void drawBorder(ServerLevel level, Vec3 center, double radius) {
        for (int i = 0; i < 24; i++) {
            double angle = (level.getGameTime() * 0.02) + i * (Math.PI * 2 / 24);
            level.sendParticles(ParticleTypes.PORTAL, center.x + Math.cos(angle) * radius, center.y + 0.2, center.z + Math.sin(angle) * radius, 1, 0, 0.2, 0, 0);
        }
    }
}
