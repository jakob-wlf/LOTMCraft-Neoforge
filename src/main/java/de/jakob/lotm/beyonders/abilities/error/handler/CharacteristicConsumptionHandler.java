package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.CopiedAbilityComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItem;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.CopiedAbilityHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Set;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class CharacteristicConsumptionHandler {

    private static final Set<String> ADJACENT_PATHWAYS = Set.of("fool", "door");
    private static final int BORROWED_USES = 3;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BeyonderCharacteristicItem characteristic)) return;
        if (!ADJACENT_PATHWAYS.contains(characteristic.getPathway()) || characteristic.getSequence() < 8) return;
        if (!ErrorUtil.isErrorOfSequence(player, 2)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        stack.shrink(1);

        player.heal(player.getMaxHealth() * 0.3f);
        BeyonderData.setSpirituality(player, BeyonderData.getMaxSpirituality(player));
        BeyonderData.digest(player, 0.05f, false);

        var sanity = player.getData(ModAttachments.SANITY_COMPONENT);
        sanity.setSanityAndSync(sanity.getSanity() - 0.01f, player);

        int borrowed = 0;
        for (Ability ability : LOTMCraft.abilityHandler.getByPathwayAndSequenceExact(characteristic.getPathway(), characteristic.getSequence())) {
            if (!ability.canBeCopied || ability.cannotBeStolen) continue;
            CopiedAbilityHelper.addAbility(player, new CopiedAbilityComponent.CopiedAbilityData(
                    ability.getId(), "consumed", BORROWED_USES, player.getUUID().toString()));
            borrowed++;
        }

        player.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 25, 0.3, 0.5, 0.3, 0.03);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1, 0.6f);
        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.loophole.consumed_characteristic", borrowed).withColor(ErrorUtil.FATE_COLOR));
    }
}
