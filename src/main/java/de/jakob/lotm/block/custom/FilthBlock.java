package de.jakob.lotm.block.custom;

import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.ClientBeyonderCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Properties;

public class FilthBlock extends Block {
    public FilthBlock(Properties properties) {super(properties);}
    // Everything sinks seq 0 abyss can stand on it unless sneaking
    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc && isAbyssLord(ecc.getEntity()) && context.isAbove(Shapes.block(), pos, false) && !context.isDescending()) {
            return Shapes.block();
        }
        return Shapes.empty();
    }
    public static boolean isAbyss(@Nullable Entity e) {
        if (!(e instanceof LivingEntity living)) return false;
        if (e.level().isClientSide) return "abyss".equals(ClientBeyonderCache.getPathway(e.getUUID())) && ClientBeyonderCache.getSequence(e.getUUID()) == 0;
        return BeyonderData.isBeyonder(living) && "abyss".equals(BeyonderData.getPathway(living)) && BeyonderData.getSequence(living) == 0;
    }
}