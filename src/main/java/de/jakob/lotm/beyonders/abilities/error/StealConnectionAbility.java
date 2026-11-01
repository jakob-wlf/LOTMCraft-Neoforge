package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.attachments.AllyComponent;
import de.jakob.lotm.attachments.MarionetteComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.MarionetteUtils;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StealConnectionAbility extends SelectableAbility {

    private static final Map<UUID, Long> threadImmunity = new ConcurrentHashMap<>();

    public StealConnectionAbility(String id) {
        super(id, 12);
        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(6, 9, 12));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(9000f, 6000f, 4000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    protected float getSpiritualityCost() {
        return 4000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.steal_connection.threads",
                "ability.lotmcraft.steal_connection.strings",
                "ability.lotmcraft.steal_connection.bonds"
        };
    }

    public static boolean isThreadImmune(LivingEntity entity) {
        Long until = threadImmunity.get(entity.getUUID());
        return until != null && until > entity.level().getGameTime();
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            if (entity instanceof Player player && level.isClientSide) player.playSound(SoundEvents.BELL_RESONATE, 1, 0.5f);
            return;
        }
        if (ProhibitionHandler.IsInTheftZone(player.position(), serverLevel, AbilityUtil.getSeqWithArt(player, this))) return;

        switch (selectedAbility) {
            case 0 -> stealThreads(serverLevel, player);
            case 1 -> severStrings(serverLevel, player);
            case 2 -> stealBonds(serverLevel, player);
        }
    }

    private void message(ServerPlayer player, String key, Object... args) {
        AbilityUtil.sendActionBar(player, Component.translatable(key, args).withColor(ErrorUtil.FATE_COLOR));
    }

    private boolean resists(ServerPlayer player, LivingEntity target) {
        return AbilityUtil.isTargetSignificantlyStronger(player, target)
                || random.nextDouble() < AbilityUtil.getSequenceFailureChance(player, target);
    }

    private void stealThreads(ServerLevel level, ServerPlayer player) {
        LivingEntity target = AbilityUtil.getTargetEntity(player, 40, 2);
        if (target == null) {
            message(player, "ability.lotmcraft.steal_connection.no_target");
            return;
        }
        if (resists(player, target)) {
            message(player, "ability.lotmcraft.steal_connection.resisted");
            return;
        }

        int ticks = (int) (20 * 120 * multiplier(player));
        threadImmunity.put(player.getUUID(), level.getGameTime() + ticks);
        threadImmunity.put(target.getUUID(), level.getGameTime() + ticks / 3);

        // a marionettist robbed of threads loses one of their marionettes
        Set<UUID> marionettes = target.getData(ModAttachments.MARIONETTE_OWNER_COMPONENT).getMarionettes();
        if (!marionettes.isEmpty()) {
            UUID freed = marionettes.iterator().next();
            Entity raw = level.getEntity(freed);
            if (raw instanceof LivingEntity marionette) {
                MarionetteComponent component = marionette.getData(ModAttachments.MARIONETTE_COMPONENT);
                component.setMarionette(false);
                component.setControllerUUID("");
            }
            target.getData(ModAttachments.MARIONETTE_OWNER_COMPONENT).removeMarionette(freed);
        }
        AbilityUtil.sealSpecificTargetAbility(player, target, "marionette_controlling_ability", "stolen_threads", 20 * 30);
        AbilityUtil.sealSpecificTargetAbility(player, target, "puppeteering_ability", "stolen_threads", 20 * 30);

        FxHandler.playOnEntity(level, "fate_threads", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));
        level.playSound(null, target.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1, 0.5f);
        message(player, "ability.lotmcraft.steal_connection.threads_stolen", ticks / 20);
    }

    private void severStrings(ServerLevel level, ServerPlayer player) {
        LivingEntity target = AbilityUtil.getTargetEntity(player, 40, 2, false, false, true, false);
        if (target == null) {
            message(player, "ability.lotmcraft.steal_connection.no_target");
            return;
        }

        LivingEntity master = ErrorUtil.getMainBody(level, target);
        if (master == null || master == player || target instanceof Player) {
            message(player, "ability.lotmcraft.steal_connection.no_strings");
            return;
        }
        if (BeyonderData.isBeyonder(master) && resists(player, master)) {
            message(player, "ability.lotmcraft.steal_connection.resisted");
            return;
        }

        if (target instanceof AvatarEntity avatar) {
            avatar.setOriginalOwner(player.getUUID());
            avatar.setTarget(null);
        } else {
            MarionetteComponent component = target.getData(ModAttachments.MARIONETTE_COMPONENT);
            master.getData(ModAttachments.MARIONETTE_OWNER_COMPONENT).removeMarionette(target.getUUID());
            component.setMarionette(false);
            if (target.getPersistentData().getBoolean("VoidSummoned")) {
                target.getPersistentData().putUUID("VoidSummonOwner", player.getUUID());
            }
            MarionetteUtils.turnEntityIntoMarionette(target, player);
        }

        FxHandler.playOnEntity(level, "fate_threads", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));
        level.playSound(null, target.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1, 0.8f);
        message(player, "ability.lotmcraft.steal_connection.strings_stolen", target.getName());
    }

    private void stealBonds(ServerLevel level, ServerPlayer player) {
        LivingEntity target = AbilityUtil.getTargetEntity(player, 40, 2);
        if (target == null) {
            message(player, "ability.lotmcraft.steal_connection.no_target");
            return;
        }
        if (resists(player, target)) {
            message(player, "ability.lotmcraft.steal_connection.resisted");
            return;
        }

        AllyComponent original = target.getData(ModAttachments.ALLY_COMPONENT);
        if (!original.hasAllies()) {
            message(player, "ability.lotmcraft.steal_connection.no_bonds");
            return;
        }

        target.setData(ModAttachments.ALLY_COMPONENT, new AllyComponent(new HashSet<>(), new HashSet<>(original.requests())));
        if (target instanceof ServerPlayer targetPlayer) AllyUtil.syncAllyData(targetPlayer);

        int ticks = (int) (20 * 60 * multiplier(player));
        UUID targetId = target.getUUID();
        ServerScheduler.scheduleDelayed(ticks, () -> {
            LivingEntity again = ErrorUtil.findLiving(level, targetId);
            if (again == null) return;
            AllyComponent current = again.getData(ModAttachments.ALLY_COMPONENT);
            Set<AllyComponent.AllyInfo> merged = new HashSet<>(original.allies());
            merged.addAll(current.allies());
            again.setData(ModAttachments.ALLY_COMPONENT, new AllyComponent(merged, new HashSet<>(current.requests())));
            if (again instanceof ServerPlayer againPlayer) AllyUtil.syncAllyData(againPlayer);
        });

        FxHandler.playOnEntity(level, "fate_threads", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));
        message(player, "ability.lotmcraft.steal_connection.bonds_stolen", target.getName());
    }
}
