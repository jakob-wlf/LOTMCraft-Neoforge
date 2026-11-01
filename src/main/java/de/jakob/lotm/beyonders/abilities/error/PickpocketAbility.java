package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.TheftHandler;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.SendPassiveTheftEffectPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class PickpocketAbility extends ToggleAbility {

    private static final Random RANDOM = new Random();

    public PickpocketAbility(String id) {
        super(id);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeShared = false;
        canBeReplicated = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 9));
    }

    @Override
    protected float getSpiritualityCost() {
        return 0.2f;
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.pickpocketActive = true;
            return;
        }
        if (!(entity instanceof ServerPlayer player)) return;
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.pickpocket.on").withColor(ErrorUtil.ERROR_COLOR));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide) {
            ErrorClientFlags.pickpocketActive = false;
            return;
        }
        if (!(entity instanceof ServerPlayer player)) return;
        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.pickpocket.off").withColor(ErrorUtil.ERROR_COLOR));
    }

    private static boolean isPickpocketing(Player player) {
        if (player.level().isClientSide) {
            return ErrorClientFlags.pickpocketActive && ErrorClientFlags.isLocalPlayer(player.getUUID());
        }
        PickpocketAbility ability = (PickpocketAbility) LOTMCraft.abilityHandler.getById("pickpocket_ability");
        return ability != null && ability.isActiveForEntity(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (!isPickpocketing(player)) return;

        boolean emptyHanded = player.getMainHandItem().isEmpty();
        boolean charged = player.getAttackStrengthScale(0.5f) >= 0.9f;

        if (emptyHanded) {
            event.setCanceled(true);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!charged) return;

        ServerLevel level = serverPlayer.serverLevel();
        if (ProhibitionHandler.IsInTheftZone(serverPlayer.position(), level, BeyonderData.getSequence(serverPlayer))) return;

        if (RANDOM.nextFloat() > (emptyHanded ? 0.6f : 0.3f)) {
            if (emptyHanded) serverPlayer.resetAttackStrengthTicker();
            return;
        }

        if (TheftHandler.stealItemsFromEntityPassive(target, serverPlayer)) {
            PacketHandler.sendToPlayer(serverPlayer, new SendPassiveTheftEffectPacket(target.getEyePosition().x, target.getEyePosition().y, target.getEyePosition().z));
        }

        if (emptyHanded) serverPlayer.resetAttackStrengthTicker();
    }
}
