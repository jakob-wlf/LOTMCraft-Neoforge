package de.jakob.lotm.util.helper;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.justiciar.PunishmentAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.rendering.effectRendering.EffectIds;
import de.jakob.lotm.rendering.effectRendering.EffectManager;
import de.jakob.lotm.rendering.effectRendering.EffectParams;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class PunishmentRapierVisuals {
    public static final int STRIKE_TICKS = 12;
    private static final Map<UUID, Formation> FORMATIONS = new HashMap<>();

    private static final class Formation {
        final LivingEntity caster, target;
        final ServerLevel level;
        UUID effect;
        int nextSlot, strikingSlot = -1;
        boolean pendingStrike;
        boolean impactPlayed;
        long strikeTick = -100, refreshed;

        Formation(LivingEntity caster, LivingEntity target, ServerLevel level) {
            this.caster = caster; this.target = target; this.level = level;
        }
    }

    public static void start(LivingEntity caster, LivingEntity target, ServerLevel level) {
        stop(caster.getUUID());
        Formation formation = new Formation(caster, target, level);
        FORMATIONS.put(caster.getUUID(), formation);
        strike(caster.getUUID());
    }

    public static int rapierCount(int sequence) {
        return sequence <= 2 ? 9 : sequence == 3 ? 7 : sequence == 4 ? 5 : 3;
    }

    public static void strike(UUID caster) {
        Formation formation = FORMATIONS.get(caster);
        if (formation == null) return;
        long now = formation.level.getGameTime();
        // Keep one queued animation instead of restarting a blade before it reaches the target.
        if (formation.strikingSlot >= 0 && now - formation.strikeTick < STRIKE_TICKS) {
            formation.pendingStrike = true;
            return;
        }
        formation.pendingStrike = false;
        formation.impactPlayed = false;
        formation.strikingSlot = formation.nextSlot++ % rapierCount(BeyonderData.getSequence(formation.caster));
        formation.strikeTick = now;
        refresh(formation);
    }

    private static void refresh(Formation formation) {
        if (formation.effect != null) EffectManager.cancelEffect(formation.effect, formation.level);
        long now = formation.level.getGameTime();
        formation.effect = EffectManager.playMovableEffect(EffectIds.PUNISHMENT_RAPIERS,
                formation.level, formation.caster, EffectParams.of(60,
                        formation.caster.getId(), formation.target.getId(),
                        (float) (now - formation.strikeTick), formation.strikingSlot,
                        rapierCount(BeyonderData.getSequence(formation.caster))));
        formation.refreshed = now;
    }

    public static void stop(UUID caster) {
        Formation formation = FORMATIONS.remove(caster);
        if (formation != null && formation.effect != null) {
            EffectManager.cancelEffect(formation.effect, formation.level);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (var entry : new HashMap<>(FORMATIONS).entrySet()) {
            Formation formation = entry.getValue();
            UUID id = entry.getKey();
            Long expiry = PunishmentAbility.PUNISHMENT_EXPIRY.get(id);
            if (expiry == null || formation.level.getGameTime() >= expiry
                    || !formation.target.getUUID().equals(PunishmentAbility.CASTER_TO_TARGET.get(id))
                    || !formation.caster.isAlive() || !formation.target.isAlive()
                    || formation.caster.isRemoved() || formation.target.isRemoved()
                    || formation.caster.level() != formation.level || formation.target.level() != formation.level) {
                PunishmentAbility.cancelPunishment(id, formation.level);
            } else if (formation.pendingStrike && formation.level.getGameTime() - formation.strikeTick >= STRIKE_TICKS) {
                strike(id);
            } else if (formation.level.getGameTime() - formation.refreshed >= 40) {
                // Refresh also supplies the formation to clients that started watching the caster later.
                refresh(formation);
            }
            if (FORMATIONS.get(id) == formation && !formation.impactPlayed
                    && formation.level.getGameTime() - formation.strikeTick >= 4) {
                formation.impactPlayed = true;
                formation.level.playSound(null, formation.target.blockPosition(),
                        SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, .65f, 1.6f);
                formation.level.playSound(null, formation.target.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .45f, 1.8f);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        FORMATIONS.clear();
        PunishmentAbility.CASTER_TO_TARGET.clear();
        PunishmentAbility.TARGET_TO_CASTER.clear();
        PunishmentAbility.PUNISHMENT_EXPIRY.clear();
    }
}
