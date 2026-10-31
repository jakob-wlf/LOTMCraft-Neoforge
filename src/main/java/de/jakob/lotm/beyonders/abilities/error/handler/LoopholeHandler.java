package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class LoopholeHandler {

    private static final Map<UUID, Map<UUID, Long>> foundLoopholes = new ConcurrentHashMap<>();
    private static final Map<UUID, Exploit> exploits = new ConcurrentHashMap<>();
    private record DoorKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<DoorKey, DoorKey> doorLinks = new ConcurrentHashMap<>();
    private static final Map<UUID, DoorKey> pendingDoors = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> doorCooldown = new ConcurrentHashMap<>();

    private record Exploit(UUID exploiter, long until, float damage) {}

    public static void markLoopholeFound(LivingEntity target, LivingEntity finder, int ticks) {
        foundLoopholes.computeIfAbsent(target.getUUID(), k -> new ConcurrentHashMap<>())
                .put(finder.getUUID(), target.level().getGameTime() + ticks);
    }

    public static boolean isLoopholeFound(LivingEntity target, LivingEntity finder) {
        if (target == null || finder == null) return false;
        Map<UUID, Long> map = foundLoopholes.get(target.getUUID());
        if (map == null) return false;
        Long until = map.get(finder.getUUID());
        return until != null && until > target.level().getGameTime();
    }

    public static void exploitNextAbility(LivingEntity target, LivingEntity exploiter, float damage, int ticks) {
        exploits.put(target.getUUID(), new Exploit(exploiter.getUUID(), target.level().getGameTime() + ticks, damage));
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        LivingEntity user = event.getEntity();
        if (user == null || !(user.level() instanceof ServerLevel level)) return;
        Exploit exploit = exploits.remove(user.getUUID());
        if (exploit == null || exploit.until < level.getGameTime()) return;

        Entity exploiter = level.getEntity(exploit.exploiter);
        user.hurt(ModDamageTypes.source(level, ModDamageTypes.BEYONDER_GENERIC, exploiter instanceof LivingEntity living ? living : null), exploit.damage);
        level.sendParticles(ParticleTypes.CRIT, user.getX(), user.getY() + user.getBbHeight() / 2, user.getZ(), 25, 0.4, 0.6, 0.4, 0.2);
        if (user instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("ability.lotmcraft.loophole.exploited_backfire").withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    private static BlockPos normalizeDoor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    public static boolean isDoor(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DoorBlock;
    }

    public static String selectDoor(ServerPlayer player, BlockPos pos) {
        DoorKey location = new DoorKey(player.level().dimension(), normalizeDoor(player.level(), pos).immutable());
        DoorKey first = pendingDoors.remove(player.getUUID());

        if (first == null || first.equals(location)) {
            pendingDoors.put(player.getUUID(), location);
            return "ability.lotmcraft.loophole.door_selected";
        }

        doorLinks.put(first, location);
        doorLinks.put(location, first);
        return "ability.lotmcraft.loophole.door_linked";
    }

    @Nullable
    private static DoorKey getLink(Level level, BlockPos pos) {
        return doorLinks.get(new DoorKey(level.dimension(), normalizeDoor(level, pos).immutable()));
    }

    @SubscribeEvent
    public static void onUseDoor(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Level level = event.getLevel();
        if (!isDoor(level, event.getPos())) return;

        DoorKey link = getLink(level, event.getPos());
        if (link == null || player.getServer() == null) return;
        ServerLevel targetLevel = player.getServer().getLevel(link.dimension());
        if (targetLevel == null) return;

        long now = level.getGameTime();
        if (doorCooldown.getOrDefault(player.getUUID(), 0L) > now) return;

        BlockPos targetDoor = link.pos();
        if (!isDoor(targetLevel, targetDoor)) {
            doorLinks.remove(link);
            return;
        }

        BlockState state = targetLevel.getBlockState(targetDoor);
        Direction facing = state.getValue(DoorBlock.FACING);
        Vec3 exit = Vec3.atBottomCenterOf(targetDoor.relative(facing));

        doorCooldown.put(player.getUUID(), now + 30);
        if (targetLevel == level) doorCooldown.put(player.getUUID(), targetLevel.getGameTime() + 30);

        ((ServerLevel) level).sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.3, 0.6, 0.3, 0.04);
        player.teleportTo(targetLevel, exit.x, exit.y, exit.z, Set.of(), facing.toYRot(), player.getXRot());
        targetLevel.playSound(null, targetDoor, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.7f);
        event.setCanceled(true);
    }

    public static void clear(UUID entity) {
        foundLoopholes.remove(entity);
        exploits.remove(entity);
    }
}
