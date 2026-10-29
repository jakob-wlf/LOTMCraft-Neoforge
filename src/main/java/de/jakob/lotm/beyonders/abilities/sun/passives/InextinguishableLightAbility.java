package de.jakob.lotm.beyonders.abilities.sun.passives;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbilityHandler;
import de.jakob.lotm.beyonders.abilities.justiciar.LawAbility;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class InextinguishableLightAbility extends PassiveAbility {
    private static final int COOLDOWN_TICKS = 20 * 60 * 30;
    private static final int MIN_DISTANCE = 200;
    private static final int MAX_DISTANCE = 500;

    private static final HashMap<UUID, Integer> lastTriggered = new HashMap<>();

    private static final DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 222 / 255f, 120 / 255f), 2.5f);

    public InextinguishableLightAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 0));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if(entity.getHealth() - event.getNewDamage() > 0) return;

        if(tryEscape(entity)) {
            event.setNewDamage(0);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if(tryEscape(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean tryEscape(LivingEntity entity) {
        if(!(entity.level() instanceof ServerLevel serverLevel)) return false;

        if(!PassiveAbilityHandler.getById("inextinguishable_light_ability").shouldApplyTo(entity)) return false;

        if(LawAbility.SOLACE_KILLED.contains(entity.getUUID())) return false;

        if(isOnCooldown(entity)) return false;

        lastTriggered.put(entity.getUUID(), entity.tickCount);

        Vec3 from = entity.position();
        Vec3 to = findEscapePosition(serverLevel, entity);

        playLightBurst(serverLevel, from, entity);

        entity.stopRiding();
        entity.teleportTo(to.x, to.y, to.z);
        entity.resetFallDistance();
        entity.clearFire();
        entity.setHealth(Math.max(entity.getHealth(), entity.getMaxHealth() * .25f));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 3, 4, false, false, false));

        playLightBurst(serverLevel, to, entity);
        return true;
    }

    private static boolean isOnCooldown(LivingEntity entity) {
        Integer last = lastTriggered.get(entity.getUUID());
        if(last == null) return false;

        int elapsed = entity.tickCount - last;
        if(elapsed < 0) {
            lastTriggered.remove(entity.getUUID());
            return false;
        }

        return elapsed < COOLDOWN_TICKS;
    }

    private static Vec3 findEscapePosition(ServerLevel level, LivingEntity entity) {
        Vec3 origin = entity.position();
        boolean hasCeiling = level.dimensionType().hasCeiling();

        for(int attempt = 0; attempt < 12; attempt++) {
            double angle = entity.getRandom().nextDouble() * Math.PI * 2;
            double distance = MIN_DISTANCE + entity.getRandom().nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = (int) Math.floor(origin.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.z + Math.sin(angle) * distance);

            if(!level.getWorldBorder().isWithinBounds(x, z)) continue;

            BlockPos pos = hasCeiling ? findSafeYBelowCeiling(level, x, (int) origin.y, z) : findSafeYSurface(level, x, z);
            if(pos != null) {
                return Vec3.atBottomCenterOf(pos);
            }
        }

        return origin.add(0, 1, 0);
    }

    private static BlockPos findSafeYSurface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        return isSafe(level, pos) ? pos : null;
    }

    private static BlockPos findSafeYBelowCeiling(ServerLevel level, int x, int startY, int z) {
        int top = Math.min(startY + 32, level.getMaxBuildHeight() - 2);
        int bottom = Math.max(startY - 64, level.getMinBuildHeight() + 1);
        for(int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if(isSafe(level, pos)) return pos;
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(below).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    private static void playLightBurst(ServerLevel level, Vec3 pos, LivingEntity entity) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 1.6f);
        ParticleUtil.spawnParticles(level, dust, pos.add(0, entity.getBbHeight() / 2, 0), 80, .6, entity.getBbHeight() / 2, .6, 0.15);
    }
}
