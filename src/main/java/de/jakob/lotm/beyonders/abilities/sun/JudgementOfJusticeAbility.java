package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeData;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeEnforcement;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticeManager;
import de.jakob.lotm.beyonders.abilities.sun.justice.JusticePrinciple;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JudgementOfJusticeAbility extends SelectableAbility {
    private static final int BASE_DURATION = 20 * 120;

    private final DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 0.8f, 0.2f), 1.6f);

    public JudgementOfJusticeAbility(String id) {
        super(id, 25, "light_source", "purification", "light_weak");

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(20, 25, 30, 40));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(1200f, 700f, 450f, 300f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 300;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return Arrays.stream(JusticePrinciple.values())
                .map(p -> "ability.lotmcraft.judgement_of_justice." + p.id())
                .toArray(String[]::new);
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (selectedAbility < 0 || selectedAbility >= JusticePrinciple.values().length) return;

        JusticePrinciple principle = JusticePrinciple.values()[selectedAbility];
        if (!JusticeData.has(player, principle)) {
            AbilityUtil.sendActionBar(player, Component.translatable("justice.lotmcraft.judgement.not_sworn", principle.shortName()).withStyle(ChatFormatting.GOLD));
            AbilityUtil.refund(player, this);
            return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(player, 40, 2f);
        if (target == null || !AbilityUtil.mayTarget(player, target)) {
            AbilityUtil.sendActionBar(player, Component.translatable("justice.lotmcraft.judgement.no_target").withStyle(ChatFormatting.GOLD));
            AbilityUtil.refund(player, this);
            return;
        }

        int duration = (int) (BASE_DURATION * JusticeManager.scale(player));
        JusticeEnforcement.addEdict(player, target, principle, duration);

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 pos = target.position();
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_POWER_SELECT, player.getSoundSource(), 1.5f, 1.2f);
        ParticleUtil.spawnCircleParticles(serverLevel, dust, pos.add(0, .1, 0), new Vec3(0, 1, 0), 1.6, 50);
        ParticleUtil.spawnCircleParticles(serverLevel, dust, pos.add(0, target.getBbHeight() + .6, 0), new Vec3(0, 1, 0), .9, 30);
        ParticleUtil.spawnParticles(serverLevel, ParticleTypes.END_ROD, target.getEyePosition(), 25, .3, .6, .3, .08);

        if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.sendSystemMessage(Component.translatable("justice.lotmcraft.judgement.bound", principle.title()).withStyle(ChatFormatting.GOLD));
        }
    }
}
