package de.jakob.lotm.beyonders.abilities.demoness;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class BloodLossAbility extends ToggleAbility {

    private static final double DROP_CHANCE = .4;
    private static final double DROP_RANGE = 2;

    private static final Random RANDOM = new Random();

    public BloodLossAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("demoness", 7));
    }

    @Override
    protected float getSpiritualityCost() {
        return 0;
    }

    @Override
    public void start(Level level, LivingEntity entity) {
        if (level.isClientSide()) return;
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("ability.lotmcraft.blood_drop.on"), true);
        }
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if (level.isClientSide()) return;
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("ability.lotmcraft.blood_drop.off"), true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide() || event.getNewDamage() <= 0) return;

        if (!(LOTMCraft.abilityHandler.getById("blood_loss_ability") instanceof BloodLossAbility ability)
                || !ability.isActiveForEntity(attacker)) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (RANDOM.nextDouble() < DROP_CHANCE && attacker.distanceTo(victim) < DROP_RANGE) {
            ItemStack blood = new ItemStack(ModItems.BLOOD.get());
            blood.set(ModDataComponents.BLOOD_OWNER, victim.getUUID().toString());
            victim.spawnAtLocation(blood);
        }
    }
}
