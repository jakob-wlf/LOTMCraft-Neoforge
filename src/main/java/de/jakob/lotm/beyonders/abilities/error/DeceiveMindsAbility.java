package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.DeceitHandler;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeceiveMindsAbility extends SelectableAbility {

    private static final Map<UUID, Map.Entry<Long, Boolean>> pendingWheels = new ConcurrentHashMap<>();

    public DeceiveMindsAbility(String id) {
        super(id, 2);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeShared = false;
        canBeUsedInArtifact = false;
        autoClear = false;

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(2400f, 1500f, 1100f, 900f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 900;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.deceive_minds.prepare",
                "ability.lotmcraft.deceive_minds.apply"
        };
    }

    @Override
    public float getInflatedSpiritualityCost(LivingEntity entity, ServerLevel level, int seq) {
        return getSelectedAbilityIndex(entity.getUUID()) == 0 ? 0 : super.getInflatedSpiritualityCost(entity, level, seq);
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(entity instanceof ServerPlayer player)) {
            if (entity instanceof Player player && level.isClientSide) player.playSound(SoundEvents.BELL_RESONATE, 1, 1.4f);
            return;
        }

        if (selectedAbility == 0) {
            CompoundTag data = new CompoundTag();
            data.put("config", DeceitHandler.getConfig(player).save(player.serverLevel()));
            ErrorUtil.send(player, "deceit_prepare", data);
            return;
        }

        boolean area = player.isShiftKeyDown();
        pendingWheels.put(player.getUUID(), new AbstractMap.SimpleEntry<>(player.serverLevel().getGameTime() + 20 * 30, area));

        CompoundTag data = new CompoundTag();
        data.put("config", DeceitHandler.getConfig(player).save(player.serverLevel()));
        data.putBoolean("area", area);
        ErrorUtil.send(player, "deceit_wheel", data);
    }

    public void applyFromWheel(ServerPlayer player, int choice) {
        Map.Entry<Long, Boolean> pending = pendingWheels.remove(player.getUUID());
        ServerLevel level = player.serverLevel();
        if (pending == null || pending.getKey() < level.getGameTime()) return;

        Set<DeceitHandler.DeceitType> types = EnumSet.noneOf(DeceitHandler.DeceitType.class);
        if (choice < 0) {
            types.addAll(DeceitHandler.getConfig(player).alloy);
        } else {
            DeceitHandler.DeceitType type = DeceitHandler.DeceitType.byOrdinal(choice);
            if (type != null) types.add(type);
        }
        if (types.isEmpty()) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.deceive_minds.empty_alloy").withColor(ErrorUtil.DECEIT_COLOR));
            return;
        }

        int seq = AbilityUtil.getSeqWithArt(player, this);
        List<LivingEntity> targets = new ArrayList<>();
        if (pending.getValue()) {
            for (LivingEntity entity : AbilityUtil.getNearbyEntities(player, level, player.position(), 20)) {
                if (!AllyUtil.areAllies(player, entity)) targets.add(entity);
            }
        } else {
            LivingEntity target = AbilityUtil.getTargetEntity(player, 40, 2);
            if (target != null) targets.add(target);
        }

        if (targets.isEmpty()) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.deceive_minds.no_target").withColor(ErrorUtil.DECEIT_COLOR));
            return;
        }

        int deceived = 0;
        for (LivingEntity target : targets) {
            if (DeceitHandler.apply(player, target, types, seq)) {
                deceived++;
                FxHandler.playOnEntity(level, "deceit_threads", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.8));
            }
        }

        if (pending.getValue()) {
            FxHandler.play(level, "deceit_wave", player.position().add(0, 0.1, 0), 1.0);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.8f);
        AbilityUtil.sendActionBar(player, Component.translatable(deceived > 0 ? "ability.lotmcraft.deceive_minds.applied" : "ability.lotmcraft.deceive_minds.resisted", deceived)
                .withColor(ErrorUtil.DECEIT_COLOR));
    }

    public static DeceiveMindsAbility get() {
        return (DeceiveMindsAbility) LOTMCraft.abilityHandler.getById("deceive_minds_ability");
    }
}
