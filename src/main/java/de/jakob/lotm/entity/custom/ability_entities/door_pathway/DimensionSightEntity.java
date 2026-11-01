package de.jakob.lotm.entity.custom.ability_entities.door_pathway;

import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.network.packets.toClient.SyncDimensionSightPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DimensionSightEntity extends Entity {
    public static final float HALF = 1.75f;

    private BlockPos center = BlockPos.ZERO;
    private int[] offsets = new int[0];
    private BlockState[] states = new BlockState[0];
    private List<SyncDimensionSightPacket.Sighted> sighted = List.of();
    private int version;

    public DimensionSightEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public DimensionSightEntity(Level level, Vec3 position, float yaw) {
        this(ModEntities.DIMENSION_SIGHT.get(), level);
        this.setPos(position);
        this.setYRot(yaw);
        this.yRotO = yaw;
    }

    public void receive(SyncDimensionSightPacket packet) {
        if (packet.terrain()) {
            int[] blocks = packet.blocks();
            int count = blocks.length / 2;
            offsets = new int[count];
            states = new BlockState[count];
            for (int i = 0; i < count; i++) {
                offsets[i] = blocks[i * 2];
                states[i] = Block.stateById(blocks[i * 2 + 1]);
            }
            center = packet.center();
        }
        sighted = packet.sighted();
        version++;
    }

    public BlockPos center() {
        return center;
    }

    public int[] offsets() {
        return offsets;
    }

    public BlockState[] states() {
        return states;
    }

    public List<SyncDimensionSightPacket.Sighted> sighted() {
        return sighted;
    }

    public int version() {
        return version;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
