package de.jakob.lotm.entity.custom.ability_entities;

import de.jakob.lotm.entity.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A purely visual vine or thread that grows from its position towards a target, optionally coiling around it.
 * Its shape is built and drawn every frame by {@code TendrilRenderer}; the server only keeps it alive.
 */
public class TendrilEntity extends Entity {

    public enum Kind { VINE, THREAD }

    private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> GROW = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> COIL = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SHELL = SynchedEntityData.defineId(TendrilEntity.class, EntityDataSerializers.FLOAT);

    // client only: lets the end glide between updates when it is moved every tick (a shot thread)
    private Vec3 previousEnd;
    private int endMovedAt = -100;

    public TendrilEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /**
     * @param target  entity to reach and coil around, or null to end at {@code end}
     * @param grow    ticks until fully grown
     * @param life    total ticks, including the {@code fade} ticks at the end in which it withers away
     * @param coil    turns wrapped around the target (0 for none)
     */
    public static TendrilEntity spawn(ServerLevel level, Kind kind, Vec3 start, @Nullable Entity target, Vec3 end,
                                      int grow, int life, int fade, float coil, float width) {
        TendrilEntity tendril = new TendrilEntity(ModEntities.TENDRIL.get(), level);
        tendril.setPos(start);
        tendril.entityData.set(END, end.toVector3f());
        tendril.entityData.set(TARGET, target == null ? -1 : target.getId());
        tendril.entityData.set(KIND, (byte) kind.ordinal());
        tendril.entityData.set(GROW, Math.max(1, grow));
        tendril.entityData.set(LIFE, life);
        tendril.entityData.set(FADE, Math.max(1, fade));
        tendril.entityData.set(SEED, level.random.nextInt());
        tendril.entityData.set(COIL, coil);
        tendril.entityData.set(WIDTH, width);
        level.addFreshEntity(tendril);
        return tendril;
    }

    /**
     * Wraps the coil around the target as an egg shaped shell of this radius instead of hugging its body,
     * running from {@code from} to {@code to} (0 = below the feet, 1 = above the head).
     */
    public TendrilEntity shell(float radius, float from, float to) {
        entityData.set(SHELL, radius);
        entityData.set(END, new Vector3f(from, to, 0));
        return this;
    }

    /** Moves the free end, e.g. the tip of a thread that is still flying. */
    public void moveEnd(Vec3 end) {
        entityData.set(END, end.toVector3f());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (END.equals(key) && level().isClientSide) {
            previousEnd = endMovedAt < -50 ? getEnd() : getRenderEnd(0);
            endMovedAt = tickCount;
        }
    }

    /** The free end, smoothed over the tick after it last moved. */
    public Vec3 getRenderEnd(float partialTick) {
        Vec3 end = getEnd();
        if (previousEnd == null) return end;
        float t = (tickCount + partialTick - endMovedAt);
        return t >= 1 ? end : previousEnd.lerp(end, Math.max(0, t));
    }

    /** True while the free end is being moved, so the renderer keeps showing its glowing tip. */
    public boolean isEndMoving(float partialTick) {
        return tickCount + partialTick - endMovedAt < 2;
    }

    /** Lets the tendril wither away now instead of at the end of its life. */
    public void release() {
        if (isRemoved()) return;
        int witherEnd = tickCount + getFade();
        if (witherEnd < getLife()) entityData.set(LIFE, witherEnd);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount >= getLife() || (getTargetId() >= 0 && getTarget() == null))) {
            discard();
        }
    }

    @Nullable
    public Entity getTarget() {
        int id = getTargetId();
        return id < 0 ? null : level().getEntity(id);
    }

    public int getTargetId() { return entityData.get(TARGET); }
    public Vec3 getEnd() { return new Vec3(entityData.get(END)); }
    public Kind getKind() { return Kind.values()[entityData.get(KIND)]; }
    public int getGrow() { return entityData.get(GROW); }
    public int getLife() { return entityData.get(LIFE); }
    public int getFade() { return entityData.get(FADE); }
    public int getSeed() { return entityData.get(SEED); }
    public float getCoil() { return entityData.get(COIL); }
    public float getWidth() { return entityData.get(WIDTH); }
    public float getShell() { return entityData.get(SHELL); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(END, new Vector3f());
        builder.define(TARGET, -1);
        builder.define(KIND, (byte) 0);
        builder.define(GROW, 10);
        builder.define(LIFE, 40);
        builder.define(FADE, 10);
        builder.define(SEED, 0);
        builder.define(COIL, 0f);
        builder.define(WIDTH, .08f);
        builder.define(SHELL, 0f);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }
}
