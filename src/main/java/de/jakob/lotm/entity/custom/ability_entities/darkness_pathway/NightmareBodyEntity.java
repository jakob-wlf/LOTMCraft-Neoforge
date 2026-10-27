package de.jakob.lotm.entity.custom.ability_entities.darkness_pathway;

import de.jakob.lotm.beyonders.abilities.darkness.NightmareAbility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NightmareBodyEntity extends Mob {
    private static final EntityDataAccessor<Optional<UUID>> SESSION =
            SynchedEntityData.defineId(NightmareBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> SKIN =
            SynchedEntityData.defineId(NightmareBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final Map<String, Integer> HELD = new HashMap<>();

    private ChunkPos ticket;

    public NightmareBodyEntity(EntityType<? extends NightmareBodyEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SESSION, Optional.empty());
        builder.define(SKIN, Optional.empty());
    }

    public void setSession(UUID uuid) {
        entityData.set(SESSION, Optional.of(uuid));
    }

    public void setSkin(@Nullable UUID uuid) {
        entityData.set(SKIN, Optional.ofNullable(uuid));
    }

    @Nullable
    public UUID getSkin() {
        return entityData.get(SKIN).orElse(null);
    }

    public void holdChunk() {
        if (!(level() instanceof ServerLevel server) || ticket != null) return;
        ticket = new ChunkPos(blockPosition());
        String key = key(server, ticket);
        int count = HELD.getOrDefault(key, 0);
        if (count == 0) server.getChunkSource().addRegionTicket(TicketType.FORCED, ticket, 2, ticket);
        HELD.put(key, count + 1);
    }

    @Override
    public void remove(RemovalReason reason) {
        release();
        super.remove(reason);
    }

    private void release() {
        if (!(level() instanceof ServerLevel server) || ticket == null) return;
        String key = key(server, ticket);
        int count = HELD.getOrDefault(key, 0) - 1;
        if (count <= 0) {
            HELD.remove(key);
            server.getChunkSource().removeRegionTicket(TicketType.FORCED, ticket, 2, ticket);
        } else {
            HELD.put(key, count);
        }
        ticket = null;
    }

    private static String key(ServerLevel level, ChunkPos pos) {
        return level.dimension().location() + " " + pos.x + " " + pos.z;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, 0, 0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && amount > 0) {
            UUID session = entityData.get(SESSION).orElse(null);
            if (session != null) NightmareAbility.stopNightmare(session);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }
}
