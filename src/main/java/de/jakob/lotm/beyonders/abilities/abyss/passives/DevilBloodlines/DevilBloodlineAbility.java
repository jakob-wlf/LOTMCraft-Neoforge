package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DevilBloodlineAbility extends PassiveAbility {

    public enum Bloodline { SHADOW_SHIFTER, PHANTOM, SHAPESHIFTER, CURSELING, CORRODER, FLAME_DEVIL }
    private static final int ALL_BLOODLINES_SEQ = 0; // at this sequence the user has every bloodline
    private static final int ASSIGN_CHECK_INTERVAL_TICKS = 100;

    public DevilBloodlineAbility(String id) {super(id);}
    @Override
    public Map<String, Integer> getRequirements() {return new HashMap<>(Map.of("abyss", 6));}
    //Queries
    public static boolean hasAllBloodlines(LivingEntity entity) {
        if (entity instanceof AvatarEntity) return false;
        return BeyonderData.isBeyonder(entity) && "abyss".equals(BeyonderData.getPathway(entity)) && BeyonderData.getSequence(entity) <= ALL_BLOODLINES_SEQ;
    }
    public static boolean hasBloodline(LivingEntity entity, Bloodline required) {
        if (entity instanceof AvatarEntity) return false;
        if (!"abyss".equals(BeyonderData.getPathway(entity))) return false;
        if (hasAllBloodlines(entity)) return true;
        if (entity.level() instanceof ServerLevel level) assignBloodlineIfMissing(entity, level);
        var component = entity.getData(ModAttachments.BLOODLINE_COMPONENT.get());
        return component.hasBloodline() && required.name().equals(component.getBloodline());
    }
    private static Bloodline parse(String name) {
        if (name == null) return null;
        try {return Bloodline.valueOf(name);} catch (IllegalArgumentException e) {return null;}
    }
    //Assignment
    public static void assignBloodlineIfMissing(LivingEntity entity, ServerLevel level) {
        if (!"abyss".equals(BeyonderData.getPathway(entity))) return;
        var component = entity.getData(ModAttachments.BLOODLINE_COMPONENT.get());
        // A stored value that is no longer a valid bloodline (renamed or removed) is re-rolled instead of kept forever
        if (component.hasBloodline() && parse(component.getBloodline()) != null) return;
        long seed = level.getSeed() ^ entity.getUUID().getMostSignificantBits() ^ entity.getUUID().getLeastSignificantBits();
        Random random = new Random(seed);
        Bloodline bloodline = Bloodline.values()[random.nextInt(Bloodline.values().length)];
        component.setBloodline(bloodline.name());
    }
    //Hooks
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            assignBloodlineIfMissing(player, level);
        }
    }
    @Override
    public void onPassiveAbilityGained(LivingEntity entity, ServerLevel serverLevel) {
        assignBloodlineIfMissing(entity, serverLevel); // lock in the bloodline the moment it's gained
    }
    @Override
    public void tick(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return;
        if (player.tickCount % ASSIGN_CHECK_INTERVAL_TICKS != 0) return;
        assignBloodlineIfMissing(player, serverLevel);
    }
}