package de.jakob.lotm.beyonders.abilities.sun.justice;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.SanityComponent;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.sun.SetOfPrinciplesAbility;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.util.AuthorityResistanceManager;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JusticeManager {
    public static final int MIN_PRINCIPLES = 2;
    public static final long LOCK_TICKS = 24000L;
    public static final long PURIFICATION_INTERVAL_TICKS = 20L * 60 * 60 * 24;
    public static final long FAIL_COOLDOWN_TICKS = 600L;
    public static final long ONGOING_REFAIL_TICKS = 12000L;
    public static final float SANITY_LOSS = 0.1f;
    public static final int FAIL_RECOVERY_TICKS = 20 * 60 * 30;
    public static final int EVIL_KILL_COUNT = 100;

    private static final String CLEARANCE_MOD = "justice_clearance";
    private static final String JUSTICE_MOD = "justice_justice";
    private static final String HOLINESS_MOD = "justice_holiness";
    private static final String LOYALTY_MOD = "justice_loyalty";
    private static final Set<UUID> modifierHolders = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> lastOngoingFail = new ConcurrentHashMap<>();

    private JusticeManager() {}

    public static boolean isMentor(LivingEntity entity) {
        return entity instanceof Player && BeyonderData.getPathway(entity).equals("sun") && BeyonderData.getSequence(entity) <= 3;
    }

    public static boolean isEvil(LivingEntity entity) {
        if (BeyonderData.isEvilPathway(entity)) return true;
        if (entity instanceof Player player) {
            return player.getData(ModAttachments.UNIQUENESS_COMPONENT.get()).getKillCount() > EVIL_KILL_COUNT;
        }
        return false;
    }

    public static boolean hasEvilAlly(LivingEntity entity) {
        if (entity.getServer() == null) return false;
        for (ServerPlayer other : entity.getServer().getPlayerList().getPlayers()) {
            if (other == entity || !BeyonderData.isBeyonder(other) || !BeyonderData.isEvilPathway(other)) continue;
            if (AllyUtil.areAllies(entity, other)) return true;
        }
        return false;
    }

    public static ServerPlayer findHighAlly(ServerPlayer player) {
        if (player.getServer() == null) return null;
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (other == player || !BeyonderData.isBeyonder(other) || BeyonderData.getSequence(other) > 1) continue;
            if (AllyUtil.areAllies(player, other)) return other;
        }
        return null;
    }

    public static boolean isAvailable(ServerPlayer player, JusticePrinciple principle) {
        return principle != JusticePrinciple.LOYALTY || findHighAlly(player) != null;
    }

    public static boolean isEnhanced(Player player) {
        return JusticeEnforcement.isEnhanced(player);
    }

    public static double scale(Player player) {
        if (!JusticeData.has(player, JusticePrinciple.CLEARANCE)) return 1.0;
        return isEnhanced(player) ? 1.6 : 1.3;
    }

    public static boolean swear(ServerPlayer player, int mask) {
        if (Integer.bitCount(mask) < MIN_PRINCIPLES) return false;
        var data = JusticeData.of(player);
        long now = player.level().getGameTime();
        if (data.getLong(JusticeData.LOCK) > now) return false;
        int old = data.getInt(JusticeData.MASK);
        if (old == mask) return true;
        data.putInt(JusticeData.MASK, mask);
        data.putLong(JusticeData.LOCK, now + LOCK_TICKS);
        data.putInt(JusticeData.DARK, 0);
        data.putString(JusticeData.ALLY, "");
        if ((mask & (1 << JusticePrinciple.PURIFICATION.ordinal())) != 0 && (old & (1 << JusticePrinciple.PURIFICATION.ordinal())) == 0) {
            data.putLong(JusticeData.DEADLINE, now + PURIFICATION_INTERVAL_TICKS);
        }
        return true;
    }

    public static void tick(ServerPlayer player) {
        JusticeEnforcement.tickSubject(player);

        boolean active = isMentor(player) && JusticeData.isSworn(player);
        if (!active) {
            if (modifierHolders.remove(player.getUUID())) clearModifiers(player);
            return;
        }

        var data = JusticeData.of(player);

        if (player.tickCount % 20 != 0) return;
        recoverFails(data);
        ServerLevel level = (ServerLevel) player.level();
        boolean enhanced = isEnhanced(player);
        Set<JusticePrinciple> chosen = JusticeData.chosen(player);

        applyBonuses(player, level, chosen, enhanced);
        checkVows(player, level, chosen);
        applyLossOfControl(player, level, data);
    }

    private static void applyBonuses(ServerPlayer player, ServerLevel level, Set<JusticePrinciple> chosen, boolean enhanced) {
        modifierHolders.add(player.getUUID());
        int amp = enhanced ? 1 : 0;

        if (chosen.contains(JusticePrinciple.CLEARANCE)) {
            BeyonderData.addModifier(player, CLEARANCE_MOD, enhanced ? 1.6 : 1.3);
        } else {
            BeyonderData.removeModifier(player, CLEARANCE_MOD);
        }

        if (chosen.contains(JusticePrinciple.JUSTICE)) {
            int mySeq = BeyonderData.getSequence(player);
            boolean threatened = AbilityUtil.getNearbyEntities(player, level, player.position(), 40).stream()
                    .anyMatch(e -> BeyonderData.isBeyonder(e) && BeyonderData.getSequence(e) < mySeq);
            if (threatened) {
                AuthorityResistanceManager.addModifier(player, JUSTICE_MOD, 1.4f);
                effect(player, MobEffects.DAMAGE_BOOST, amp, 60);
                if (enhanced) effect(player, MobEffects.REGENERATION, 0, 60);
            } else {
                AuthorityResistanceManager.removeModifier(player, JUSTICE_MOD);
            }
        }

        if (chosen.contains(JusticePrinciple.HOLINESS)) {
            for (LivingEntity ally : AbilityUtil.getNearbyEntities(player, level, player.position(), 32, false, true)) {
                if (AllyUtil.areAllies(player, ally)) {
                    AuthorityResistanceManager.addModifierWithTimeLimit(ally, HOLINESS_MOD, 1.2f, 60);
                };
            }
        }

        if (chosen.contains(JusticePrinciple.PEACE)) {
            player.removeEffect(MobEffects.HUNGER);
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20);
        }

        if (chosen.contains(JusticePrinciple.LOYALTY)) {
            boolean near = false;
            ServerPlayer ally = loyalAlly(player);
            if (ally != null && ally.level() == level && ally.distanceTo(player) <= 64) near = true;
            if (near) {
                BeyonderData.addModifier(player, LOYALTY_MOD, enhanced ? 1.5 : 1.25);
                effect(player, MobEffects.DAMAGE_BOOST, amp, 60);
                AuthorityResistanceManager.addModifier(player, LOYALTY_MOD, 1.4f);
            } else {
                BeyonderData.removeModifier(player, LOYALTY_MOD);
                AuthorityResistanceManager.removeModifier(player, LOYALTY_MOD);
            }
        } else {
            BeyonderData.removeModifier(player, LOYALTY_MOD);
            AuthorityResistanceManager.removeModifier(player, LOYALTY_MOD);
        }

        if (chosen.contains(JusticePrinciple.SANCTUARY)) {
            boolean village = !level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(24)).isEmpty();
            if (village) {
                effect(player, MobEffects.HERO_OF_THE_VILLAGE, amp, 100);
                effect(player, MobEffects.ABSORPTION, amp, 100);
            }
        }

        if (chosen.contains(JusticePrinciple.RADIANCE)) {
            effect(player, MobEffects.NIGHT_VISION, 0, 400);
            BlockPos pos = player.blockPosition();
            if (level.canSeeSky(pos) && level.isDay()) effect(player, MobEffects.REGENERATION, amp, 60);
        }
    }

    private static void checkVows(ServerPlayer player, ServerLevel level, Set<JusticePrinciple> chosen) {
        var data = JusticeData.of(player);
        long now = level.getGameTime();

        for (JusticePrinciple principle : chosen) {
            if (principle == JusticePrinciple.HOLINESS && principle.brokenByState(player)) fail(player, principle, true);
        }

        if (chosen.contains(JusticePrinciple.PURIFICATION) && now > data.getLong(JusticeData.DEADLINE)) {
            data.putLong(JusticeData.DEADLINE, now + PURIFICATION_INTERVAL_TICKS);
            fail(player, JusticePrinciple.PURIFICATION, false);
        }

        if (chosen.contains(JusticePrinciple.LOYALTY)) {
            String stored = data.getString(JusticeData.ALLY);
            if (stored.isEmpty()) {
                ServerPlayer ally = findHighAlly(player);
                if (ally != null) data.putString(JusticeData.ALLY, ally.getStringUUID());
            } else {
                ServerPlayer ally = player.getServer().getPlayerList().getPlayer(UUID.fromString(stored));
                if (ally != null && !AllyUtil.areAllies(player, ally)) {
                    data.putString(JusticeData.ALLY, "");
                    fail(player, JusticePrinciple.LOYALTY, false);
                }
            }
        }

        if (chosen.contains(JusticePrinciple.RADIANCE)) {
            int dark = data.getInt(JusticeData.DARK);
            if (level.getMaxLocalRawBrightness(player.blockPosition()) <= 3) dark++;
            else dark = Math.max(0, dark - 2);
            if (dark >= 180) {
                dark = 0;
                fail(player, JusticePrinciple.RADIANCE, true);
            }
            data.putInt(JusticeData.DARK, dark);
        }
    }

    private static void applyLossOfControl(ServerPlayer player, ServerLevel level, net.minecraft.nbt.CompoundTag data) {
        if (data.getInt(JusticeData.FAILS) < 3) return;
        addSanity(player, -0.003f);
        if (player.getRandom().nextFloat() < 0.12f) {
            List<Holder<MobEffect>> debuffs = List.of(MobEffects.CONFUSION, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.BLINDNESS, MobEffects.WEAKNESS);
            effect(player, debuffs.get(player.getRandom().nextInt(debuffs.size())), 0, 100);
        }
        if (player.tickCount % 200 == 0 && player.getRandom().nextFloat() < 0.1f) {
            player.sendSystemMessage(Component.translatable("justice.lotmcraft.regress").withStyle(ChatFormatting.DARK_RED));
            AbilityUtil.sealAllTargetAbilities(player, player, "vows_broken", 20 * 60);
            player.addEffect(new MobEffectInstance(ModEffects.LOOSING_CONTROL, 20 * 6, 1, false, false, false));
        }
    }

    /** Called from ChangeBeyonderDataEvent (fires before the change is applied). A new sequence or pathway wipes all fails. */
    public static void onBeyonderDataChange(ServerPlayer player, String oldPathway, String newPathway, int oldSequence, int newSequence) {
        if (!player.getPersistentData().contains(JusticeData.ROOT)) return;
        if (oldSequence != newSequence || !Objects.equals(oldPathway, newPathway)) {
            resetFails(JusticeData.of(player));
        }
    }

    private static void resetFails(net.minecraft.nbt.CompoundTag data) {
        data.putInt(JusticeData.FAILS, 0);
        data.putIntArray(JusticeData.FAIL_TIMERS, new int[0]);
    }

    private static int[] failTimers(net.minecraft.nbt.CompoundTag data, int fails) {
        int[] stored = data.getIntArray(JusticeData.FAIL_TIMERS);
        int[] timers = new int[fails];
        for (int i = 0; i < fails; i++) timers[i] = i < stored.length ? stored[i] : FAIL_RECOVERY_TICKS;
        return timers;
    }


    private static void recoverFails(net.minecraft.nbt.CompoundTag data) {
        int fails = data.getInt(JusticeData.FAILS);
        if (fails <= 0) return;
        int[] timers = failTimers(data, fails);
        int[] remaining = new int[timers.length];
        int kept = 0;
        for (int timer : timers) {
            timer -= 20;
            if (timer > 0) remaining[kept++] = timer;
        }
        data.putIntArray(JusticeData.FAIL_TIMERS, Arrays.copyOf(remaining, kept));
        if (kept < fails) data.putInt(JusticeData.FAILS, kept);
    }

    public static void fail(ServerPlayer player, JusticePrinciple principle, boolean ongoing) {
        if (!isMentor(player) || !JusticeData.has(player, principle)) return;
        var data = JusticeData.of(player);
        long now = player.level().getGameTime();

        if (data.contains(JusticeData.LAST_FAIL) && now - data.getLong(JusticeData.LAST_FAIL) < FAIL_COOLDOWN_TICKS) return;
        if (ongoing) {
            String key = player.getStringUUID() + principle.id();
            Long last = lastOngoingFail.get(key);
            if (last != null && now - last < ONGOING_REFAIL_TICKS) return;
            lastOngoingFail.put(key, now);
        }

        int fails = data.getInt(JusticeData.FAILS) + 1;
        int[] timers = failTimers(data, fails);
        timers[fails - 1] = FAIL_RECOVERY_TICKS;
        data.putInt(JusticeData.FAILS, fails);
        data.putIntArray(JusticeData.FAIL_TIMERS, timers);
        data.putLong(JusticeData.LAST_FAIL, now);

        ServerLevel level = (ServerLevel) player.level();
        ParticleUtil.spawnParticles(level, ParticleTypes.SMOKE, player.getEyePosition(), 30, .4, .6, .4, .02);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, player.getSoundSource(), 1f, .6f);

        Component message = Component.translatable("justice.lotmcraft.fail." + Math.min(fails, 4), principle.shortName()).withStyle(ChatFormatting.RED);
        player.sendSystemMessage(message);
        AbilityUtil.sendActionBar(player, message);

        if (fails == 1) addSanity(player, -SANITY_LOSS);
        if (fails == 3) addSanity(player, -SANITY_LOSS);
        if (fails >= 4) {
            resetFails(data);
            player.kill();
        }
    }

    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        if (!isMentor(killer) || !JusticeData.isSworn(killer)) return;
        var data = JusticeData.of(killer);
        for (JusticePrinciple principle : JusticeData.chosen(killer)) {
            if (principle.brokenByKill(killer, victim)) fail(killer, principle, false);
        }
        if (JusticeData.has(killer, JusticePrinciple.PURIFICATION) && BeyonderData.isBeyonder(victim)
                && BeyonderData.isEvilPathway(victim) && BeyonderData.getSequence(victim) == BeyonderData.getSequence(killer)) {
            data.putLong(JusticeData.DEADLINE, killer.level().getGameTime() + PURIFICATION_INTERVAL_TICKS);
            AbilityUtil.sendActionBar(killer, Component.translatable("justice.lotmcraft.purified").withStyle(ChatFormatting.GOLD));
        }
    }

    public static void onHarm(ServerPlayer attacker, LivingEntity victim) {
        if (!isMentor(attacker) || !JusticeData.isSworn(attacker)) return;
        for (JusticePrinciple principle : JusticeData.chosen(attacker)) {
            if (principle.brokenByHarm(attacker, victim)) fail(attacker, principle, false);
        }
    }

    public static void onArtifactUsed(ServerPlayer player) {
        if (!isMentor(player) || !JusticeData.isSworn(player)) return;
        fail(player, JusticePrinciple.CLEARANCE, true);
    }

    public static float purificationMultiplier(ServerPlayer attacker, LivingEntity victim) {
        if (!isMentor(attacker) || !JusticeData.has(attacker, JusticePrinciple.PURIFICATION)) return 1f;
        if (!BeyonderData.isEvilPathway(victim)) return 1f;
        return isEnhanced(attacker) ? 2f : 1.5f;
    }

    public static void onFoodEaten(ServerPlayer player, ItemStack stack) {
        if (!isMentor(player) || !JusticeData.has(player, JusticePrinciple.PEACE)) return;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;
        boolean enhanced = isEnhanced(player);

        FoodData foodData = player.getFoodData();
        float bonus = food.saturation() * (enhanced ? 1.2f : 0.75f);
        foodData.setSaturation(Math.min(foodData.getFoodLevel(), foodData.getSaturationLevel() + bonus));

        List<Holder<MobEffect>> boons = List.of(MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_RESISTANCE,
                MobEffects.ABSORPTION, MobEffects.DIG_SPEED, MobEffects.JUMP, MobEffects.SATURATION);
        effect(player, boons.get(player.getRandom().nextInt(boons.size())), enhanced ? 1 : 0, 20 * (enhanced ? 30 : 15));

        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            for (Holder<MobEffect> holder : List.of(MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE)) {
                MobEffectInstance current = player.getEffect(holder);
                if (current == null) continue;
                int duration = current.getDuration() * (enhanced ? 2 : 3) / 2;
                player.addEffect(new MobEffectInstance(holder, duration, current.getAmplifier() + 1, false, false, true));
            }
        }
        ParticleUtil.spawnParticles((ServerLevel) player.level(), ParticleTypes.END_ROD, player.getEyePosition(), 12, .3, .3, .3, .05);
    }

    private static ServerPlayer loyalAlly(ServerPlayer player) {
        String stored = JusticeData.of(player).getString(JusticeData.ALLY);
        if (stored.isEmpty() || player.getServer() == null) return null;
        return player.getServer().getPlayerList().getPlayer(UUID.fromString(stored));
    }

    private static void clearModifiers(ServerPlayer player) {
        BeyonderData.removeModifier(player, CLEARANCE_MOD);
        BeyonderData.removeModifier(player, LOYALTY_MOD);
    }

    private static void addSanity(ServerPlayer player, float delta) {
        SanityComponent sanity = player.getData(ModAttachments.SANITY_COMPONENT);
        sanity.setSanityAndSync(Math.max(0f, sanity.getSanity() + delta), player);
    }

    private static void effect(LivingEntity entity, Holder<MobEffect> effect, int amplifier, int duration) {
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, false));
    }
}
