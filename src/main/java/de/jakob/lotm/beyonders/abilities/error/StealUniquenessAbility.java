package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.UniquenessComponent;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.FateHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ObservationHandler;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.*;

public class StealUniquenessAbility extends Ability {

    public static final int KING_OF_ANGELS_STACKS = 2;

    public StealUniquenessAbility(String id) {
        super(id, 300);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeShared = false;
        canBeUsedInArtifact = false;
        cannotBeStolen = true;

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(60000f, 40000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 1));
    }

    @Override
    protected float getSpiritualityCost() {
        return 40000;
    }

    public static boolean isKingOfAngels(LivingEntity entity) {
        int seq = BeyonderData.getSequence(entity);
        return seq == 0 || (seq == 1 && BeyonderData.getCharStack(entity, 1) >= KING_OF_ANGELS_STACKS);
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer thief)) return;

        if (!isKingOfAngels(thief)) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.steal_uniqueness.not_king", KING_OF_ANGELS_STACKS).withColor(ErrorUtil.TIME_COLOR));
            return;
        }

        LivingEntity looked = AbilityUtil.getTargetEntity(thief, 60, 2);
        if (!(looked instanceof ServerPlayer target)) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.steal_uniqueness.no_target").withColor(ErrorUtil.TIME_COLOR));
            return;
        }

        UniquenessComponent targetUniqueness = target.getData(ModAttachments.UNIQUENESS_COMPONENT);
        if (!targetUniqueness.hasUniqueness()) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.steal_uniqueness.none").withColor(ErrorUtil.TIME_COLOR));
            return;
        }

        FxHandler.playOnEntity(serverLevel, "authority_theft", target, target.getBbHeight() * 0.5, 1.6);

        double chance = FateHandler.hasRecentlyStolenFate(thief, target) ? 0.3 : 0.01;
        if (random.nextDouble() >= chance) {
            AbilityUtil.sendActionBar(thief, Component.translatable("ability.lotmcraft.steal_uniqueness.failed").withColor(ErrorUtil.TIME_COLOR));
            return;
        }

        String pathway = targetUniqueness.getUniquenessPathway();
        targetUniqueness.setHasUniqueness(false);
        targetUniqueness.setUniquenessPathway("");
        BeyonderData.playerMap.setUniqueness(target, "none");
        PacketHandler.syncUniquenessToPlayer(target);

        UniquenessComponent thiefUniqueness = thief.getData(ModAttachments.UNIQUENESS_COMPONENT);
        thiefUniqueness.setHasUniqueness(true);
        thiefUniqueness.setUniquenessPathway(pathway);
        BeyonderData.playerMap.setUniqueness(thief, pathway);
        PacketHandler.syncUniquenessToPlayer(thief);

        serverLevel.playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1f, 1.4f);
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("ability.lotmcraft.steal_uniqueness.broadcast",
                ObservationHandler.prettify(pathway)).withStyle(ChatFormatting.DARK_PURPLE), false);
    }
}
