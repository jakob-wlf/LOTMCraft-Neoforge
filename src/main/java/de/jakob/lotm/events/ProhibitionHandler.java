package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.justiciar.ExileOfBalanceAbility;
import de.jakob.lotm.beyonders.abilities.justiciar.ProhibitionAbility;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ProhibitionHandler {

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        if (isInZone(entity.position(), serverLevel, ProhibitionAbility.ProhibitionType.BEYONDER_ABILITIES, BeyonderData.getSequence(entity), entity)) {
            event.setCanceled(true);
            WorldJudgmentHandler.escalate(entity);
            if (!ProhibitionAbility.ProhibitionZone.appliesTo(entity, ProhibitionAbility.ProhibitionType.BEYONDER_ABILITIES)){
                event.setCanceled(false);
                return;
            }
            if (entity instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("lotmcraft.prohibition.beyonder_abilities")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }

        long now = serverLevel.getGameTime();
        for (ProhibitionAbility.ProhibitionZone zone : ProhibitionAbility.ACTIVE_ZONES) {
            if (de.jakob.lotm.beyonders.abilities.error.DeceitAbility.isReversed(zone)) continue;
            if (zone.type != ProhibitionAbility.ProhibitionType.OUTSIDE_WORLD) continue;
            if (!de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields.canApplyFrom(zone.ownerId, entity)) continue;
            if (!zone.level.equals(serverLevel)) continue;
            if (zone.expiryTick < now) continue;
            double dist = entity.position().distanceTo(zone.center);
            if (dist > zone.radius && dist <= zone.radius + 10.0) {
                event.setCanceled(true);
                WorldJudgmentHandler.escalate(entity);
                if (entity instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable("lotmcraft.prohibition.outside_world_abilities")
                            .withStyle(ChatFormatting.RED));
                }
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!(attacker.level() instanceof ServerLevel serverLevel)) return;

        if (isInZone(attacker.position(), serverLevel, ProhibitionAbility.ProhibitionType.COMBAT, BeyonderData.getSequence(attacker), attacker)) {
            event.setNewDamage(0);
            WorldJudgmentHandler.escalate(attacker);
            if (attacker instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("lotmcraft.prohibition.combat")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        long now = serverLevel.getGameTime();
        ProhibitionAbility.ACTIVE_ZONES.removeIf(z -> z.expiryTick < now);

        Vec3 pos = player.position();

        if (isInZone(pos, serverLevel, ProhibitionAbility.ProhibitionType.FLYING, BeyonderData.getSequence(player), player)) {
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
                player.sendSystemMessage(Component.translatable("lotmcraft.prohibition.flying")
                        .withStyle(ChatFormatting.RED));
            }
        }

        for (ProhibitionAbility.ProhibitionZone zone : ProhibitionAbility.ACTIVE_ZONES) {
            if (de.jakob.lotm.beyonders.abilities.error.DeceitAbility.isReversed(zone)) continue;
            if (!zone.type.equals(ProhibitionAbility.ProhibitionType.PLAYERS)) continue;
            if (!de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields.canApplyFrom(zone.ownerId, player)) continue;
            if (!zone.level.equals(serverLevel)) continue;
            if (!zone.isActive()) continue;
            if (zone.ownerId.equals(player.getUUID())) continue;
            if (!ProhibitionAbility.ProhibitionZone.appliesTo(player, zone.type)) continue;

            if (zone.isInZone(pos, serverLevel)) {
                Vec3 direction = pos.subtract(zone.center).normalize();
                if (direction.lengthSqr() < 0.001) {
                    direction = new Vec3(1, 0, 0);
                }
                player.setDeltaMovement(direction.scale(1.5));
                player.hurtMarked = true;
                player.sendSystemMessage(Component.translatable("lotmcraft.prohibition.players")
                        .withStyle(ChatFormatting.RED));
            }
        }
        for (ProhibitionAbility.ProhibitionZone zone : ProhibitionAbility.ACTIVE_ZONES) {
            if (de.jakob.lotm.beyonders.abilities.error.DeceitAbility.isReversed(zone)) continue;
            if (!zone.type.equals(ProhibitionAbility.ProhibitionType.OUTSIDE_WORLD)) continue;
            if (!de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields.canApplyFrom(zone.ownerId, player)) continue;
            if (!zone.level.equals(serverLevel)) continue;
            if (!zone.isActive()) continue;
            if (zone.ownerId.equals(player.getUUID())) continue;
            if (!ProhibitionAbility.ProhibitionZone.appliesTo(player, zone.type)) continue;

            double dist = pos.distanceTo(zone.center);
            if (dist > zone.radius && dist <= zone.radius + 3.0) {
                Vec3 direction = pos.subtract(zone.center).normalize();
                if (direction.lengthSqr() < 0.001) direction = new Vec3(1, 0, 0);
                player.setDeltaMovement(direction.scale(1.5));
                player.hurtMarked = true;
                player.sendSystemMessage(Component.translatable("lotmcraft.prohibition.outside_world")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        if (isInZone(player.position(), serverLevel, ProhibitionAbility.ProhibitionType.ITEM_USE, BeyonderData.getSequence(player), player)) {
            event.setCanceled(true);
            WorldJudgmentHandler.escalate(player);
            player.sendSystemMessage(Component.translatable("lotmcraft.prohibition.item_use")
                    .withStyle(ChatFormatting.RED));
        }
    }

    public static boolean isInStandInsZone(Vec3 pos, ServerLevel level, int targetSequence) {
        return isInZone(pos, level, ProhibitionAbility.ProhibitionType.STAND_INS, targetSequence);
    }

    public static boolean isInMarionetteZone(Vec3 pos, ServerLevel level, int targetSequence) {
        return isInZone(pos, level, ProhibitionAbility.ProhibitionType.MARIONETTE_INTERCHANGE, targetSequence);
    }

    public static boolean IsInTheftZone(Vec3 pos, ServerLevel level, int targetSequence) {
        return isInZone(pos, level, ProhibitionAbility.ProhibitionType.THEFT, targetSequence);
    }

    @SubscribeEvent
    public static void onAbilityUseExile(AbilityUseEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel sl)) return;
        Long expiry = ExileOfBalanceAbility.EXILED_ENTITIES.get(entity.getUUID());
        if (expiry != null && sl.getGameTime() < expiry) {
            event.setCanceled(true);
            if (entity instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("lotmcraft.prohibition.exile_participating")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void onDamageExile(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!(attacker.level() instanceof ServerLevel sl)) return;
        Long expiry = ExileOfBalanceAbility.EXILED_ENTITIES.get(attacker.getUUID());
        if (expiry != null && sl.getGameTime() < expiry) {
            event.setNewDamage(0);
        }
    }

    private static boolean isInZone(Vec3 pos, ServerLevel level, ProhibitionAbility.ProhibitionType type, int targetSequence) {
        return isInZone(pos, level, type, targetSequence,
                de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceContext.source());
    }

    private static boolean isInZone(Vec3 pos, ServerLevel level, ProhibitionAbility.ProhibitionType type, int targetSequence, LivingEntity target) {
        long now = level.getGameTime();
        for (ProhibitionAbility.ProhibitionZone zone : ProhibitionAbility.ACTIVE_ZONES) {
            if (de.jakob.lotm.beyonders.abilities.error.DeceitAbility.isReversed(zone)) continue;
            if (zone.type != type) continue;
            if (target != null && !de.jakob.lotm.beyonders.abilities.justiciar.balancing.BalanceBattlefields.canApplyFrom(zone.ownerId, target)) continue;
            if (!zone.level.equals(level)) continue;
            if (zone.expiryTick < now) continue;
            if (isTargetTooStrong(zone.casterSequence, targetSequence)) continue;
            if (zone.isInZone(pos, level)) return true;
        }
        return false;
    }

    private static boolean isTargetTooStrong(int casterSequence, int targetSequence) {
        if(targetSequence <= 4 && casterSequence >= 5) return true;
        if(targetSequence <= 2 && casterSequence >= 3) return true;
        if(targetSequence == 0 && casterSequence >= 1) return true;

        return casterSequence - 1 > targetSequence;
    }
}
