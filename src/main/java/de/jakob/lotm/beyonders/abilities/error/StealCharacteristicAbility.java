package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.FateHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.LoopholeHandler;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItem;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItemHandler;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.playerMap.StoredData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

public class StealCharacteristicAbility extends SelectableAbility {

    private static final Map<UUID, Long> openMenus = new java.util.concurrent.ConcurrentHashMap<>();

    public StealCharacteristicAbility(String id) {
        super(id, 20);
        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(10, 15, 20));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(15000f, 9000f, 6000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 2));
    }

    @Override
    protected float getSpiritualityCost() {
        return 6000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.steal_characteristic.other",
                "ability.lotmcraft.steal_characteristic.self"
        };
    }

    public static double getBaseChance(int targetSeq) {
        return switch (targetSeq) {
            case 9, 8, 7 -> 0.5;
            case 6, 5 -> 0.3;
            case 4 -> 0.1;
            case 3 -> 0.06;
            case 2 -> 0.03;
            case 1 -> 0.01;
            default -> 0;
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            if (entity instanceof Player player && level.isClientSide) player.playSound(SoundEvents.BELL_RESONATE, 1, 0.5f);
            return;
        }

        if (selectedAbility == 1) {
            openSelfMenu(player);
            return;
        }

        if (ProhibitionHandler.IsInTheftZone(player.position(), serverLevel, AbilityUtil.getSeqWithArt(player, this))) return;
        stealFromOther(serverLevel, player);
    }

    private void message(ServerPlayer player, String key, Object... args) {
        AbilityUtil.sendActionBar(player, Component.translatable(key, args).withColor(ErrorUtil.FATE_COLOR));
    }

    private void stealFromOther(ServerLevel level, ServerPlayer thief) {
        LivingEntity target = AbilityUtil.getTargetEntity(thief, 40, 2, false, true);
        if (target == null || !BeyonderData.isBeyonder(target)) {
            message(thief, "ability.lotmcraft.steal_characteristic.no_target");
            return;
        }

        int thiefSeq = AbilityUtil.getSeqWithArt(thief, this);
        int targetSeq = BeyonderData.getSequence(target);
        boolean ally = AllyUtil.areAllies(thief, target);

        if (targetSeq <= 0) {
            message(thief, "ability.lotmcraft.steal_characteristic.true_god");
            return;
        }

        double chance;
        if (ally) {
            chance = 1;
        } else {
            if (AbilityUtil.isTargetSignificantlyStronger(thiefSeq, targetSeq)) {
                message(thief, "ability.lotmcraft.steal_characteristic.failed");
                return;
            }
            chance = getBaseChance(targetSeq);
            if (FateHandler.hasRecentlyStolenFate(thief, target)) chance *= 3;
            if (LoopholeHandler.isLoopholeFound(target, thief)) chance *= 1.5;
        }

        FxHandler.playOnEntity(level, "characteristic_theft", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));

        if (random.nextDouble() >= Math.min(1, chance)) {
            message(thief, "ability.lotmcraft.steal_characteristic.failed");
            return;
        }

        ItemStack characteristic = extractCharacteristic(target, targetSeq);
        if (characteristic.isEmpty()) {
            message(thief, "ability.lotmcraft.steal_characteristic.failed");
            return;
        }

        ErrorUtil.giveOrDrop(thief, characteristic);
        level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.4f);
        message(thief, "ability.lotmcraft.steal_characteristic.success", target.getName());
        if (target instanceof ServerPlayer victim) {
            victim.displayClientMessage(Component.translatable("ability.lotmcraft.steal_characteristic.victim").withColor(ErrorUtil.FATE_COLOR), false);
        }
    }

    public static ItemStack extractCharacteristic(LivingEntity entity, int sequence) {
        String pathway = BeyonderData.getPathway(entity);
        BeyonderCharacteristicItem item = BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence(pathway, sequence);
        if (item == null) return ItemStack.EMPTY;

        int currentSeq = BeyonderData.getSequence(entity);
        int stacks = BeyonderData.getCharStack(entity, sequence);

        if (stacks > 0) {
            BeyonderData.setCharStack(entity, stacks - 1, sequence, true);
        } else if (sequence == currentSeq) {
            if (entity instanceof ServerPlayer player && BeyonderData.playerMap.get(player).isPresent()) {
                StoredData regressed = BeyonderData.playerMap.get(player).get().regressSeq();
                BeyonderData.playerMap.put(player, regressed);
                // A regression must never be refused by the sequence limits, or the map and the player drift apart
                BeyonderData.setBeyonder(player, regressed.pathway(), regressed.sequence(), true, false, true, false);
            } else if (currentSeq >= 9) {
                BeyonderData.clearBeyonderData(entity);
            } else {
                BeyonderData.setBeyonder(entity, pathway, currentSeq + 1);
            }
        } else {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private void openSelfMenu(ServerPlayer player) {
        int seq = BeyonderData.getSequence(player);
        ListTag options = new ListTag();

        for (int s = Math.max(1, seq); s <= 9; s++) {
            int stacks = BeyonderData.getCharStack(player, s);
            if (stacks > 0) {
                CompoundTag option = new CompoundTag();
                option.putInt("seq", s);
                option.putBoolean("regress", false);
                option.putInt("stacks", stacks);
                options.add(option);
            }
        }

        if (seq >= 1 && seq <= 9 && BeyonderData.getCharStack(player, seq) == 0) {
            CompoundTag option = new CompoundTag();
            option.putInt("seq", seq);
            option.putBoolean("regress", true);
            options.add(option);
        }

        CompoundTag data = new CompoundTag();
        data.putString("pathway", BeyonderData.getPathway(player));
        data.put("options", options);
        openMenus.put(player.getUUID(), player.serverLevel().getGameTime() + 20 * 60);
        ErrorUtil.send(player, "self_characteristic", data);
    }

    public void extractFromSelf(ServerPlayer player, int sequence) {
        Long until = openMenus.remove(player.getUUID());
        if (until == null || until < player.serverLevel().getGameTime()) return;

        int seq = BeyonderData.getSequence(player);
        if (sequence < Math.max(1, seq) || sequence > 9) return;

        ItemStack characteristic = extractCharacteristic(player, sequence);
        if (characteristic.isEmpty()) return;

        var sanity = player.getData(ModAttachments.SANITY_COMPONENT);
        sanity.setSanityAndSync(sanity.getSanity() - 0.05f, player);
        ErrorUtil.giveOrDrop(player, characteristic);
        FxHandler.playOnEntity(player.serverLevel(), "characteristic_theft", player, player.getBbHeight() * 0.5, 1);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    public static StealCharacteristicAbility get() {
        return (StealCharacteristicAbility) LOTMCraft.abilityHandler.getById("steal_characteristic_ability");
    }
}
