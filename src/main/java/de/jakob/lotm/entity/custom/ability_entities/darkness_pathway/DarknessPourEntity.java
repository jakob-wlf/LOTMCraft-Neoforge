package de.jakob.lotm.entity.custom.ability_entities.darkness_pathway;

import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DarknessPourEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DarknessPourEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DESTINATION = SynchedEntityData.defineId(DarknessPourEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.02f, 0.01f, 0.04f), 1.8f);
    private static final int LIFE = 20 * 8;

    private final Map<UUID, Integer> stage = new HashMap<>();
    private int age;

    public DarknessPourEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public DarknessPourEntity(Level level, double x, double y, double z, LivingEntity owner, boolean destination) {
        this(ModEntities.DARKNESS_POUR.get(), level);
        setPos(x, y, z);
        entityData.set(OWNER, Optional.ofNullable(owner == null ? null : owner.getUUID()));
        entityData.set(DESTINATION, destination);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        age++;
        if (age >= LIFE) {
            discard();
            return;
        }
        float radius = Math.min(8f, 2f + age / 10f);
        serverLevel.sendParticles(DUST, getX(), getY() + 0.2, getZ(), 30, radius * 0.45, 0.35, radius * 0.45, 0.01);
        LivingEntity owner = owner(serverLevel);
        AABB box = new AABB(getX() - radius, getY() - 1, getZ() - radius, getX() + radius, getY() + 3, getZ() + radius);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (owner != null && target.getUUID().equals(owner.getUUID())) continue;
            if (!entityData.get(DESTINATION) && owner != null && AllyUtil.areAllies(owner, target)) continue;
            if (entityData.get(DESTINATION)) {
                String pathway = BeyonderData.getPathway(target);
                if ("death".equals(pathway) || "mother".equals(pathway)) continue;
            }
            int step = stage.getOrDefault(target.getUUID(), 0);
            if (step == 0) {
                if (target instanceof Mob mob) mob.setNoAi(true);
                AbilityUtil.sealAllTargetAbilities(owner == null ? target : owner, target, "dissolve", 20 * 6);
                stage.put(target.getUUID(), age);
            } else if (age - step >= 40 && age - step < 80) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 40, 2, false, false, false));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false, false));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 40, 2, false, false, false));
            } else if (age - step >= 80) {
                target.kill();
                stage.put(target.getUUID(), age + LIFE);
            }
        }
    }

    private LivingEntity owner(ServerLevel level) {
        UUID id = entityData.get(OWNER).orElse(null);
        if (id == null) return null;
        Entity entity = level.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(DESTINATION, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(DESTINATION, tag.getBoolean("Destination"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER).ifPresent(id -> tag.putUUID("Owner", id));
        tag.putBoolean("Destination", entityData.get(DESTINATION));
    }
}
