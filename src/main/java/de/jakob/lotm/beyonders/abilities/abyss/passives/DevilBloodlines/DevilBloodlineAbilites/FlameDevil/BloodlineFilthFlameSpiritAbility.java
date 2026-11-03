package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.FlameDevil;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BloodlineFilthFlameSpiritAbility extends PassiveAbility {
    public static final float FIRE_DAMAGE_MULTIPLIER = 1.25F;
    private static final float RETALIATION_DAMAGE = 12.5f;
    private static final int RETALIATION_BURN_TICKS = 20 * 2;
    private static final long RETALIATION_COOLDOWN_TICKS = 20L;
    private static final Map<UUID, Long> lastRetaliation = new ConcurrentHashMap<>();
    private static final DustParticleOptions FILTH_DUST = new DustParticleOptions(new Vector3f(0.28F, 0.14F, 0.05F), 1.6F);

    public BloodlineFilthFlameSpiritAbility(String id) {
        super(id);
    }
    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("abyss", 5));
    }
    @Override
    public boolean meetsBloodlineRequirement(LivingEntity entity) {
        return hasFlameDevilBloodline(entity);
    }
    private static boolean hasFlameDevilBloodline(LivingEntity entity) {
        // Shadow clones never have a bloodline
        if (entity instanceof AvatarEntity) return false;
        return DevilBloodlineAbility.hasBloodline(entity, DevilBloodlineAbility.Bloodline.FLAME_DEVIL);
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }
    public static boolean isFilthFlameSpirit(LivingEntity entity) {
        if (BeyonderData.getSequence(entity) > 5) return false;
        return hasFlameDevilBloodline(entity);
    }
    public static boolean protects(LivingEntity victim, int invaderSeq) {return isFilthFlameSpirit(victim) && invaderSeq >= BeyonderData.getSequence(victim);}
    public static float getFireDamageMultiplier(LivingEntity user) {return isFilthFlameSpirit(user) ? FIRE_DAMAGE_MULTIPLIER : 1.0F;}
    public static boolean shieldSpirit(int invaderSeq, LivingEntity invader, LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return false;
        if (invader == null || invader == victim) return false;
        if (!protects(victim, invaderSeq)) return false;
        // Allies are never stopped or punished
        if (victim.getData(ModAttachments.ALLY_COMPONENT.get()).isAlly(invader.getUUID())) return false;
        long now = level.getGameTime();
        long last = lastRetaliation.getOrDefault(invader.getUUID(), 0L);
        if (now - last >= RETALIATION_COOLDOWN_TICKS) {
            lastRetaliation.put(invader.getUUID(), now);
            if (lastRetaliation.size() > 64) lastRetaliation.values().removeIf(t -> now - t > 200);
            retaliate(level, invader, victim);
        }
        return true;
    }
    private static void retaliate(ServerLevel level, LivingEntity invader, LivingEntity victim) {
        invader.hurt(ModDamageTypes.source(level, ModDamageTypes.FILTH, victim), RETALIATION_DAMAGE);
        invader.setRemainingFireTicks(RETALIATION_BURN_TICKS);
        ParticleUtil.spawnParticles(level, FILTH_DUST, invader.getEyePosition(), 60, 0.5, 0.02);
        ParticleUtil.spawnParticles(level, ParticleTypes.FLAME, invader.getEyePosition(), 25, 0.4, 0.04);
        AbilityUtil.sendActionBar(invader, Component.translatable("ability.lotmcraft.filth_flame_spirit.retaliate").withColor(0xFFa3070c));
    }
}