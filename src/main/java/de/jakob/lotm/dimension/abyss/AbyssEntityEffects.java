package de.jakob.lotm.dimension.abyss;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.abyss.AbyssalAwakeningAbility;
import de.jakob.lotm.dimension.ModDimensions;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class AbyssEntityEffects {

    private static final int ABYSS_SAFE_SEQUENCE = 5;
    private static final int OTHER_SAFE_SEQUENCE = 2;
    private static final float SANITY_DAMAGE = 0.005f;
    private static  final int AWAKENED_MULT = 5;
    
    //The Abyss itself counts as a sequence 0 source.
    private static final int ABYSS_SOURCE_SEQUENCE = 0;
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(ModDimensions.ABYSS_DIMENSION_KEY) || (!isAbyssBiome(player))) return;
        if (player.isCreative() || player.isSpectator()) return;
        if (!player.isAlive()) return;
        boolean awakened = AbyssalAwakeningAbility.isAwakened(player.serverLevel());
        // Awakened: everyone drains except a seq 0 abyss, and the drain is doubled
        if (awakened ? isAbyss(player) : isProtected(player)) return;
        float damage = awakened ? SANITY_DAMAGE * AWAKENED_MULT : SANITY_DAMAGE;
        player.getData(ModAttachments.SANITY_COMPONENT).decreaseSanityWithSequenceDifference(damage, player, ABYSS_SOURCE_SEQUENCE, BeyonderData.getSequence(player));
    }
    private static boolean isAbyss(ServerPlayer player) {
        return BeyonderData.isBeyonder(player) && "abyss".equals(BeyonderData.getPathway(player)) && BeyonderData.getSequence(player) == 0;
    }
    private static boolean isAbyssBiome(ServerPlayer player) {
        return player.level().getBiome(player.blockPosition()).is(ModDimensions.ABYSS_BIOME_KEY);
    }

    private static boolean isProtected(ServerPlayer player) {
        String pathway = BeyonderData.getPathway(player);
        if (pathway == null || pathway.isEmpty() || pathway.equalsIgnoreCase("none")) {return false;}
        int sequence = BeyonderData.getSequence(player);
        int needed = "abyss".equals(pathway) ? ABYSS_SAFE_SEQUENCE : OTHER_SAFE_SEQUENCE;
        return sequence <= needed;
    }
}