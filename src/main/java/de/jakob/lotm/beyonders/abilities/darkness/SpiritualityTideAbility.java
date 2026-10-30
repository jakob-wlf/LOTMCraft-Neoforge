package de.jakob.lotm.beyonders.abilities.darkness;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class SpiritualityTideAbility extends SelectableAbility {
    private static int darkness = 0;
    private static UUID darknessOwner;

    public SpiritualityTideAbility(String id) {
        super(id, 2);
        canBeUsedByNPC = false;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 0));
    }

    @Override
    protected float getSpiritualityCost() {
        return 4000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.spirituality_tide.suppress",
                "ability.lotmcraft.spirituality_tide.quicken"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (!(entity instanceof ServerPlayer player) || !"darkness".equals(BeyonderData.getPathway(player))) return;
        int want = abilityIndex == 0 ? -1 : 1;
        if (darkness == want && player.getUUID().equals(darknessOwner)) {
            darkness = 0;
            darknessOwner = null;
            AbilityUtil.sendActionBar(player, Component.literal("The tide stills.").withColor(0xFF3300b5));
            return;
        }
        darkness = want;
        darknessOwner = player.getUUID();
        AbilityUtil.sendActionBar(player, Component.literal(want < 0 ? "The tide is held back." : "The tide runs faster.").withColor(0xFF3300b5));
    }

    public static int tide() {
        return darkness;
    }

    public static boolean dry(LivingEntity entity) {
        return entity != null
                && !entity.level().dimension().equals(ModDimensions.SPIRIT_WORLD_DIMENSION_KEY)
                && darkness <= -2;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(AbilityUseEvent event) {
        LivingEntity entity = event.getEntity();
        Ability ability = event.getAbility();
        if (!dry(entity) || ability == null) return;
        if (ability.getRequirements().values().stream().anyMatch(seq -> seq != null && seq == 0)) return;
        if (ability.spiritualityCost(BeyonderData.getSequence(entity)) <= 0) return;
        event.setCanceled(true);
        if (entity instanceof ServerPlayer player) {
            AbilityUtil.sendActionBar(player, Component.literal("Spirituality no longer drips into the world.").withColor(0xFF3300b5));
        }
    }

}
