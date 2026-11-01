package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DeceitBlockHandler {

    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> deceived = new ConcurrentHashMap<>();

    private static final Map<UUID, BlockPos> lastInteracted = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> lentExperience = new ConcurrentHashMap<>();

    private static final int LENT_LEVELS = 40;

    public static boolean canDeceive(BlockState state, @Nullable BlockEntity blockEntity) {
        return blockEntity instanceof AbstractFurnaceBlockEntity
                || blockEntity instanceof BrewingStandBlockEntity
                || state.getBlock() instanceof EnchantingTableBlock
                || state.getBlock() instanceof AnvilBlock
                || state.getBlock() instanceof BonemealableBlock
                || state.hasProperty(BlockStateProperties.OPEN);
    }

    public static void deceive(ServerLevel level, BlockPos pos, int ticks) {
        deceived.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>()).put(pos.immutable(), level.getGameTime() + ticks);

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AnvilBlock && !state.is(Blocks.ANVIL)) {
            level.setBlock(pos, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING)), 3);
        } else if (state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)) {
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(null, level, state, pos, true);
            } else {
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 3);
            }
        }

        level.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.4, 0.3, 0.4, 0.02);
    }

    public static boolean isDeceived(Level level, BlockPos pos) {
        Map<BlockPos, Long> map = deceived.get(level.dimension());
        if (map == null) return false;
        Long until = map.get(pos);
        return until != null && until > level.getGameTime();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (deceived.isEmpty() || event.getServer().getTickCount() % 10 != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<BlockPos, Long> map = deceived.get(level.dimension());
            if (map == null || map.isEmpty()) continue;
            long now = level.getGameTime();
            map.values().removeIf(until -> until < now);

            for (BlockPos pos : map.keySet()) {
                if (!level.isLoaded(pos)) continue;
                tickBlock(level, pos);
            }
        }
    }

    private static void tickBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            // the furnace believes it has just been fed a lump of coal
            furnace.litTime = 200;
            furnace.litDuration = 200;
            if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
                level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
            }
            furnace.setChanged();
        } else if (blockEntity instanceof BrewingStandBlockEntity brewingStand) {
            brewingStand.fuel = 20;
            brewingStand.setChanged();
        } else if (state.getBlock() instanceof AnvilBlock && !state.is(Blocks.ANVIL)) {
            level.setBlock(pos, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, state.getValue(AnvilBlock.FACING)), 3);
        } else if (state.getBlock() instanceof BonemealableBlock && state.isRandomlyTicking()) {
            state.randomTick(level, pos, level.random);
            if (level.random.nextInt(4) == 0) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0);
            }
        }
    }


    @SubscribeEvent
    public static void onEnchantLevel(EnchantmentLevelSetEvent event) {
        if (!isDeceived(event.getLevel(), event.getPos())) return;
        int max = switch (event.getEnchantRow()) {
            case 0 -> 8;
            case 1 -> 20;
            default -> 30;
        };
        event.setEnchantLevel(Math.max(event.getEnchantLevel(), max));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (isDeceived(event.getLevel(), event.getPos())) {
            lastInteracted.put(event.getEntity().getUUID(), event.getPos().immutable());
        } else {
            lastInteracted.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof EnchantmentMenu) && !(event.getContainer() instanceof AnvilMenu)) return;
        BlockPos pos = lastInteracted.get(player.getUUID());
        if (pos == null || !isDeceived(player.level(), pos)) return;

        lentExperience.put(player.getUUID(), player.experienceLevel);
        player.setExperienceLevels(Math.max(player.experienceLevel, LENT_LEVELS));
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        restoreExperience(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        restoreExperience(event.getEntity());
        lastInteracted.remove(event.getEntity().getUUID());
    }

    private static void restoreExperience(Player player) {
        Integer original = lentExperience.remove(player.getUUID());
        if (original != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.setExperienceLevels(original);
        }
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        BlockPos pos = lastInteracted.get(event.getEntity().getUUID());
        if (pos != null && isDeceived(event.getEntity().level(), pos)) {
            event.setBreakChance(0);
        }
    }
}
