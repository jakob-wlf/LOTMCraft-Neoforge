package de.jakob.lotm.beyonders.abilities.abyss.contract.handler;

import de.jakob.lotm.beyonders.abilities.core.PassiveAbilityHandler;
import de.jakob.lotm.beyonders.abilities.core.PhysicalEnhancementsAbility;
import de.jakob.lotm.beyonders.abilities.core.PhysicalEnhancementsAbility.PhysicalEnhancement;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class SoulBuffs {
    private SoulBuffs() {}
    public static void grant(ServerPlayer holder, PhysicalEnhancementsAbility ability, int seq) {
        if (holder == null || ability == null || seq < 0 || seq > 9) {return;}
        List<PhysicalEnhancement> enhancements = ability.getPhysicalEnhancementsForSequence(seq);
        long duration = Math.max(1, 10 - seq) * 60L;
        String soulId = "soul_buff_" + UUID.randomUUID();
        int index = 0;
        for (PhysicalEnhancement enhancement : enhancements) {
            int halfLevel = enhancement.getLevel() / 2;
            // Skip enhancements that would have no effect.
            if (halfLevel <= 0) continue;
            PhysicalEnhancement halfEnhancement = new PhysicalEnhancement(enhancement.getType(), halfLevel);
            PhysicalEnhancementsAbility.addTemporaryEnhancement(holder, halfEnhancement, soulId + "_" + index++, duration);
        }
    }

    public static PhysicalEnhancementsAbility getAbility(String pathway) {
        if (pathway == null) return null;
        String abilityId = switch (pathway.toLowerCase()) {
            case "red_priest" -> "physical_enhancements_red_priest_ability";
            case "sun" -> "physical_enhancements_sun_ability";
            case "tyrant" -> "physical_enhancements_tyrant_ability";
            case "demoness" -> "physical_enhancements_demoness_ability";
            case "abyss" -> "physical_enhancements_abyss_ability";
            case "fool" -> "physical_enhancements_fool_ability";
            case "error" -> "physical_enhancements_error_ability";
            case "darkness" -> "physical_enhancements_darkness_ability";
            case "door" -> "physical_enhancements_door_ability";
            case "mother" -> "physical_enhancements_mother_ability";
            case "visionary" -> "physical_enhancements_visionary_ability";
            case "wheel_of_fortune" -> "physical_enhancements_wheel_of_fortune_ability";
            case "justiciar" -> "physical_enhancements_justiciar_ability";
            case "death" -> "physical_enhancements_death_ability";
            default -> null;
        };
        if (abilityId == null) return null;
        var ability = PassiveAbilityHandler.getById(abilityId);
        return ability instanceof PhysicalEnhancementsAbility physicalAbility ? physicalAbility : null;
    }

}
