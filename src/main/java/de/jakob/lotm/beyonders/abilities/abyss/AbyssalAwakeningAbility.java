package de.jakob.lotm.beyonders.abilities.abyss;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.dimension.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class AbyssalAwakeningAbility extends ToggleAbility {

    // Normal abyss
    private static final float PURIFICATION_BASE = 0.25f;
    private static final float NORMAL_BASE = 0.85f;
    private static final float EVIL_BASE = 1.5f;
    // Awakened
    private static final float PURIFICATION_AWAKENED = 0f;
    private static final float NORMAL_AWAKENED = 0.4f;
    private static final float EVIL_AWAKENED = 3f;

    private static final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public AbyssalAwakeningAbility(String id) {
        super(id);
        canBeCopied = false;
        canBeReplicated = false;
        canBeUsedInArtifact = false;
        cannotBeStolen = true;
        canBeShared = false;
    }
    @Override
    public Map<String, Integer> getRequirements() {return new HashMap<>(Map.of("abyss", 0));}
    @Override
    protected float getSpiritualityCost() {return 500;}

    @Override
    public void start(Level level, LivingEntity entity) {if (!level.isClientSide) active.add(entity.getUUID());}
    @Override
    public void tick(Level level, LivingEntity entity) {if (!level.isClientSide) active.add(entity.getUUID());}
    @Override
    public void stop(Level level, LivingEntity entity) {if (!level.isClientSide) active.remove(entity.getUUID());}
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {active.remove(event.getEntity().getUUID());}

    public static boolean isAwakened(ServerLevel level) {
        if (!level.dimension().equals(ModDimensions.ABYSS_DIMENSION_KEY) || active.isEmpty()) return false;
        for (ServerPlayer p : level.players()) {if (active.contains(p.getUUID())) return true;}
        return false;
    }

    // Damage rules inside the abyss
    @SubscribeEvent(priority = EventPriority.LOW) // after DamageResistanceHandler
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ModDimensions.ABYSS_DIMENSION_KEY)) return;
        float mult = getMultiplier(event.getSource(), isAwakened(level));
        if (mult == 1f) return;
        float amount = event.getAmount() * mult;
        event.setAmount(amount);
        if (amount <= 0f) event.setCanceled(true);
    }
    private static float getMultiplier(DamageSource source, boolean awakened) {
        if (source.is(ModDamageTypes.PURIFICATION)) return awakened ? PURIFICATION_AWAKENED : PURIFICATION_BASE;
        if (isEvil(source)) return awakened ? EVIL_AWAKENED : EVIL_BASE;
        if (!isModType(source)) return 1f; // vanilla damage is untouched
        // The awakened caster's own attacks aren't nerfed
        if (source.getEntity() instanceof LivingEntity attacker && active.contains(attacker.getUUID())) return 1f;
        return awakened ? NORMAL_AWAKENED : NORMAL_BASE;
    }
    private static boolean isEvil(DamageSource s) {
        return s.is(ModDamageTypes.EVIL);
    }
    private static boolean isModType(DamageSource s) {
        return s.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals(LOTMCraft.MOD_ID)).orElse(false);
    }
}