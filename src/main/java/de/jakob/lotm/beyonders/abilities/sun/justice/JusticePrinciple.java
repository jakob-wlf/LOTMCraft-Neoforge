package de.jakob.lotm.beyonders.abilities.sun.justice;

import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public enum JusticePrinciple {
    JUSTICE,
    PURIFICATION,
    HOLINESS,
    PEACE,
    CLEARANCE,
    LOYALTY,
    SANCTUARY,
    RADIANCE;

    public String id() {
        return name().toLowerCase();
    }

    public Component title() {
        return Component.translatable("justice.lotmcraft.principle." + id());
    }

    public Component shortName() {
        return Component.translatable("justice.lotmcraft.principle." + id() + ".short");
    }

    public Component vow() {
        return Component.translatable("justice.lotmcraft.principle." + id() + ".vow");
    }

    public Component boon() {
        return Component.translatable("justice.lotmcraft.principle." + id() + ".boon");
    }

    public boolean brokenByHarm(LivingEntity attacker, LivingEntity victim) {
        return switch (this) {
            case JUSTICE -> isPerson(victim) && isWeak(victim) && !JusticeManager.isEvil(victim);
            case PURIFICATION -> BeyonderData.isBeyonder(attacker) && BeyonderData.isEvilPathway(attacker);
            case LOYALTY -> BeyonderData.isBeyonder(victim) && BeyonderData.getSequence(victim) <= 1 && !JusticeManager.isEvil(victim);
            case SANCTUARY -> victim instanceof AbstractVillager || victim instanceof IronGolem
                    || (victim instanceof OwnableEntity pet && pet.getOwner() != null && pet.getOwner() != attacker);
            case RADIANCE -> victim.hasEffect(MobEffects.BLINDNESS) || victim.hasEffect(MobEffects.DARKNESS);
            default -> false;
        };
    }

    public boolean brokenByKill(LivingEntity attacker, LivingEntity victim) {
        return this == PEACE && (victim instanceof Animal || victim instanceof AbstractFish || victim instanceof AbstractVillager);
    }

    public boolean brokenByState(LivingEntity entity, boolean artifactRising) {
        return switch (this) {
            case HOLINESS -> JusticeManager.hasEvilAlly(entity);
            case CLEARANCE -> artifactRising;
            default -> false;
        };
    }

    private static boolean isPerson(LivingEntity entity) {
        return entity instanceof Player || entity instanceof AbstractVillager || entity instanceof BeyonderNPCEntity;
    }

    private static boolean isWeak(LivingEntity entity) {
        return !BeyonderData.isBeyonder(entity) || BeyonderData.getSequence(entity) >= 5;
    }
}
