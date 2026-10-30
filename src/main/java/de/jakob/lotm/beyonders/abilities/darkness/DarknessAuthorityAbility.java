package de.jakob.lotm.beyonders.abilities.darkness;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.entity.custom.ability_entities.darkness_pathway.DarknessPourEntity;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DarknessAuthorityAbility extends SelectableAbility {
    private static final List<Slumber> slumbers = new ArrayList<>();
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.02f, 0.01f, 0.04f), 1.6f);

    public DarknessAuthorityAbility(String id) {
        super(id, 8);
        canBeUsedByNPC = false;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 0));
    }

    @Override
    protected float getSpiritualityCost() {
        return 12000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.darkness_authority.dissolve",
                "ability.lotmcraft.darkness_authority.destination",
                "ability.lotmcraft.darkness_authority.eternal_slumber"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) return;
        switch (abilityIndex) {
            case 0 -> pour(serverLevel, player, false);
            case 1 -> pour(serverLevel, player, true);
            case 2 -> slumber(serverLevel, player);
        }
    }

    private void pour(ServerLevel level, ServerPlayer player, boolean destination) {
        LivingEntity looked = AbilityUtil.getTargetEntity(player, 24, 2);
        Vec3 pos = looked != null ? looked.position() : player.getEyePosition().add(player.getLookAngle().scale(12));
        DarknessPourEntity pour = new DarknessPourEntity(level, pos.x, pos.y, pos.z, player, destination);
        level.addFreshEntity(pour);
        bar(player, destination ? "All things meet their destination." : "Darkness pours.");
    }

    private void slumber(ServerLevel level, ServerPlayer player) {
        LivingEntity target = AbilityUtil.getTargetEntity(player, 24, 2);
        if (target == null) return;
        Iterator<Slumber> existing = slumbers.iterator();
        boolean lifted = false;
        while (existing.hasNext()) {
            Slumber slumber = existing.next();
            if (!slumber.target.getUUID().equals(target.getUUID())) continue;
            slumber.release();
            existing.remove();
            lifted = true;
        }
        if (lifted) {
            bar(player, "Eternal slumber lifts.");
            return;
        }
        BlockPos center = target.blockPosition();
        Map<BlockPos, BlockState> replaced = new HashMap<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean inside = Math.abs(x) <= 1 && y >= 0 && y <= 2 && Math.abs(z) <= 1;
                    if (inside) continue;
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getDestroySpeed(level, pos) < 0) continue;
                    replaced.put(pos, state);
                    level.setBlockAndUpdate(pos, Blocks.BLACK_CONCRETE.defaultBlockState());
                }
            }
        }
        slumbers.add(new Slumber(level, target, center, replaced, level.getGameTime() + 20L * 15));
        clearMind(target);
        level.sendParticles(DUST, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 40, 1.2, 0.2, 1.2, 0.01);
        bar(player, "Eternal slumber takes them.");
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Iterator<Slumber> iterator = slumbers.iterator();
        while (iterator.hasNext()) {
            Slumber slumber = iterator.next();
            if (slumber.level != level) continue;
            if (slumber.tick()) iterator.remove();
        }
    }

    private static void clearMind(LivingEntity target) {
        target.removeEffect(ModEffects.MENTAL_PLAGUE);
        target.removeEffect(ModEffects.LOOSING_CONTROL);
        target.removeEffect(ModEffects.FOOLING);
        target.removeEffect(MobEffects.CONFUSION);
        target.getActiveEffects().stream().map(MobEffectInstance::getEffect).toList().forEach(effect -> {
            var key = effect.unwrapKey();
            if (key.isPresent()) {
                String path = key.get().location().getPath();
                if (path.contains("mental") || path.contains("corrupt") || path.contains("frenzy") || path.contains("fool")) {
                    target.removeEffect(effect);
                }
            }
        });
    }

    private static void bar(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withColor(0xFF3300b5)));
    }

    private static final class Slumber {
        private final ServerLevel level;
        private final LivingEntity target;
        private final BlockPos center;
        private final Map<BlockPos, BlockState> replaced;
        private final long until;

        private Slumber(ServerLevel level, LivingEntity target, BlockPos center, Map<BlockPos, BlockState> replaced, long until) {
            this.level = level;
            this.target = target;
            this.center = center;
            this.replaced = replaced;
            this.until = until;
        }

        private boolean tick() {
            if (level.getGameTime() >= until || !target.isAlive()) {
                release();
                return true;
            }
            double dx = target.getX() - (center.getX() + 0.5);
            double dz = target.getZ() - (center.getZ() + 0.5);
            if (dx * dx + dz * dz > 2.25 || target.getY() < center.getY() || target.getY() > center.getY() + 2.2) {
                target.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
                target.setDeltaMovement(Vec3.ZERO);
            }
            target.addEffect(new MobEffectInstance(ModEffects.ASLEEP, 40, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 2, false, false, false));
            clearMind(target);
            return false;
        }

        private void release() {
            replaced.forEach(level::setBlockAndUpdate);
            target.removeEffect(ModEffects.ASLEEP);
            target.removeEffect(MobEffects.WEAKNESS);
            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            target.removeEffect(MobEffects.BLINDNESS);
            target.removeEffect(MobEffects.DARKNESS);
            target.removeEffect(MobEffects.DIG_SLOWDOWN);
        }
    }
}
