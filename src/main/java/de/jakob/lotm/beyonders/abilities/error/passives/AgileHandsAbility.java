package de.jakob.lotm.beyonders.abilities.error.passives;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class AgileHandsAbility extends PassiveAbility {

    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "agile_hands");

    public AgileHandsAbility(String id) {
        super(id);
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 9));
    }

    private static double getEfficiency(int sequence) {
        return switch (sequence) {
            case 9, 8 -> 1;
            case 7, 6, 5 -> 2;
            case 4, 3 -> 4;
            case 2, 1 -> 5;
            default -> 6;
        };
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if (level.isClientSide) return;

        AttributeInstance attribute = entity.getAttribute(Attributes.MINING_EFFICIENCY);
        if (attribute == null) return;

        double value = getEfficiency(BeyonderData.getSequence(entity));
        AttributeModifier existing = attribute.getModifier(MODIFIER_ID);
        if (existing != null && existing.amount() == value) return;

        attribute.removeModifier(MODIFIER_ID);
        attribute.addTransientModifier(new AttributeModifier(MODIFIER_ID, value, AttributeModifier.Operation.ADD_VALUE));
    }

    @Override
    public void onPassiveAbilityRemoved(LivingEntity entity, ServerLevel serverLevel) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MINING_EFFICIENCY);
        if (attribute != null) attribute.removeModifier(MODIFIER_ID);
    }
}
