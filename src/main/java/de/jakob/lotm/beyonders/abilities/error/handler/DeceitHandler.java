package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class DeceitHandler {

    public enum DeceitType {
        PARASITE, INTUITION, SANITY, HEALTH, SPIRITUALITY, ITEM, HUNGER, INVENTORY, DIVINATION, ALLY, ABILITY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static DeceitType byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
        }
    }

    public static final String ITEM_DECEIT_TAG = "lotm_deceit_look";

    public static class DeceitConfig {
        public final EnumSet<DeceitType> alloy = EnumSet.noneOf(DeceitType.class);
        public float sanity = 1.0f;
        public float health = 1.0f;
        public int spiritualityMode = 0; // 0 fluctuate, 1 fixed, 2 overuse, 3 underuse
        public float spiritualityValue = 0.5f;
        public int hungerMode = 0; // 0 faster, 1 slower, 2 drained, 3 meager meals
        public String itemDisguise = "minecraft:stick";
        public String itemName = "";
        public final List<ItemStack> fakeInventory = new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
        public int divinationMode = 0; // 0 fake location, 1 redirect, 2 higher being
        public int fakeX = 0, fakeY = 64, fakeZ = 0;
        public String redirectName = "";
        public int fakeSequence = 9;
        public int abilityMode = 0; // 0 no effects, 1 looks weak

        public CompoundTag save(ServerLevel level) {
            CompoundTag tag = new CompoundTag();
            int alloyBits = 0;
            for (DeceitType type : alloy) alloyBits |= 1 << type.ordinal();
            tag.putInt("alloy", alloyBits);
            tag.putFloat("sanity", sanity);
            tag.putFloat("health", health);
            tag.putInt("spiritMode", spiritualityMode);
            tag.putFloat("spiritValue", spiritualityValue);
            tag.putInt("hungerMode", hungerMode);
            tag.putString("itemDisguise", itemDisguise);
            tag.putString("itemName", itemName);
            ListTag inventory = new ListTag();
            for (ItemStack stack : fakeInventory) inventory.add(stack.saveOptional(level.registryAccess()));
            tag.put("inventory", inventory);
            tag.putInt("divMode", divinationMode);
            tag.putInt("fakeX", fakeX);
            tag.putInt("fakeY", fakeY);
            tag.putInt("fakeZ", fakeZ);
            tag.putString("redirect", redirectName);
            tag.putInt("fakeSeq", fakeSequence);
            tag.putInt("abilityMode", abilityMode);
            return tag;
        }

        public static DeceitConfig load(ServerLevel level, CompoundTag tag) {
            DeceitConfig config = new DeceitConfig();
            int alloyBits = tag.getInt("alloy");
            for (DeceitType type : DeceitType.values()) {
                if ((alloyBits & (1 << type.ordinal())) != 0) config.alloy.add(type);
            }
            if (tag.contains("sanity")) config.sanity = clamp01(tag.getFloat("sanity"));
            if (tag.contains("health")) config.health = clamp01(tag.getFloat("health"));
            config.spiritualityMode = Math.floorMod(tag.getInt("spiritMode"), 4);
            if (tag.contains("spiritValue")) config.spiritualityValue = clamp01(tag.getFloat("spiritValue"));
            config.hungerMode = Math.floorMod(tag.getInt("hungerMode"), 4);
            if (tag.contains("itemDisguise")) config.itemDisguise = tag.getString("itemDisguise");
            config.itemName = tag.getString("itemName");
            if (config.itemName.length() > 48) config.itemName = config.itemName.substring(0, 48);
            ListTag inventory = tag.getList("inventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(36, inventory.size()); i++) {
                config.fakeInventory.set(i, ItemStack.parseOptional(level.registryAccess(), inventory.getCompound(i)));
            }
            config.divinationMode = Math.floorMod(tag.getInt("divMode"), 3);
            config.fakeX = tag.getInt("fakeX");
            config.fakeY = tag.contains("fakeY") ? tag.getInt("fakeY") : 64;
            config.fakeZ = tag.getInt("fakeZ");
            config.redirectName = tag.getString("redirect");
            config.fakeSequence = tag.contains("fakeSeq") ? Math.max(0, Math.min(9, tag.getInt("fakeSeq"))) : 9;
            config.abilityMode = Math.floorMod(tag.getInt("abilityMode"), 2);
            return config;
        }
    }

    public record ActiveDeceit(UUID caster, DeceitType type, long expires) {}

    private static final String CONFIG_TAG = "lotm_deceit_config";

    private static final Map<UUID, List<ActiveDeceit>> active = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> deceiveCounts = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> sanityMasks = new ConcurrentHashMap<>();
    private static final Map<UUID, Map.Entry<Vec3, Long>> frozen = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> foodAtStart = new ConcurrentHashMap<>();

    private static final Random RANDOM = new Random();


    public static DeceitConfig getConfig(ServerPlayer caster) {
        CompoundTag data = caster.getPersistentData();
        if (!data.contains(CONFIG_TAG, Tag.TAG_COMPOUND)) {
            DeceitConfig config = new DeceitConfig();
            config.alloy.add(DeceitType.SANITY);
            config.alloy.add(DeceitType.ALLY);
            return config;
        }
        return DeceitConfig.load(caster.serverLevel(), data.getCompound(CONFIG_TAG));
    }

    public static void setConfig(ServerPlayer caster, CompoundTag tag) {
        DeceitConfig config = DeceitConfig.load(caster.serverLevel(), tag);
        caster.getPersistentData().put(CONFIG_TAG, config.save(caster.serverLevel()));
    }

    public static int getDuration(int casterSeq) {
        return switch (casterSeq) {
            case 0 -> 20 * 180;
            case 1 -> 20 * 120;
            case 2 -> 20 * 90;
            default -> 20 * 60;
        };
    }

    public static boolean apply(ServerPlayer caster, LivingEntity target, Collection<DeceitType> types, int casterSeq) {
        if (types.isEmpty() || target == caster) return false;
        ServerLevel level = caster.serverLevel();
        int targetSeq = ErrorUtil.seqOf(target);

        if (BeyonderData.isBeyonder(target) && AbilityUtil.isTargetSignificantlyStronger(casterSeq, targetSeq)) {
            return false;
        }

        double failure = AbilityUtil.getSequenceFailureChance(casterSeq, targetSeq);
        if (LoopholeHandler.isLoopholeFound(target, caster)) failure *= 0.3;
        if ("error".equals(BeyonderData.getPathway(target)) && targetSeq < casterSeq) failure = 1;
        if (RANDOM.nextDouble() < failure) return false;

        DeceitConfig config = getConfig(caster);
        int duration = getDuration(casterSeq);
        if (targetSeq < casterSeq) duration /= 2;
        long expires = level.getGameTime() + duration;

        List<ActiveDeceit> list = active.computeIfAbsent(target.getUUID(), k -> Collections.synchronizedList(new ArrayList<>()));
        for (DeceitType type : types) {
            list.removeIf(d -> d.type == type && d.caster.equals(caster.getUUID()));
            list.add(new ActiveDeceit(caster.getUUID(), type, expires));
            applyClientSide(level, caster, target, type, config, duration);
        }

        deceiveCounts.computeIfAbsent(caster.getUUID(), k -> new ConcurrentHashMap<>()).merge(target.getUUID(), 1, Integer::sum);

        level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getEyeY(), target.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        return true;
    }

    private static void applyClientSide(ServerLevel level, ServerPlayer caster, LivingEntity target, DeceitType type, DeceitConfig config, int duration) {
        CompoundTag data = new CompoundTag();
        data.putString("type", type.key());
        data.putInt("ticks", duration);

        switch (type) {
            case SANITY -> {
                data.putFloat("value", config.sanity);
                sanityMasks.put(caster.getUUID(), level.getGameTime() + duration);
            }
            case HEALTH -> data.putFloat("value", config.health);
            case SPIRITUALITY -> {
                data.putInt("mode", config.spiritualityMode);
                data.putFloat("value", config.spiritualityValue);
            }
            case HUNGER -> data.putInt("mode", config.hungerMode);
            case INVENTORY -> {
                ListTag inventory = new ListTag();
                for (ItemStack stack : config.fakeInventory) inventory.add(stack.saveOptional(level.registryAccess()));
                data.put("items", inventory);
            }
            case ALLY -> {
                data.putUUID("caster", caster.getUUID());
                data.putString("name", caster.getName().getString());
            }
            case ABILITY -> data.putInt("mode", config.abilityMode);
            case ITEM -> applyItemDeceit(level, target, config, duration);
            default -> {
            }
        }

        ErrorUtil.send(target, "deceit", data);
    }

    private static void applyItemDeceit(ServerLevel level, LivingEntity target, DeceitConfig config, int duration) {
        ItemStack held = target.getMainHandItem();
        if (held.isEmpty()) return;

        ResourceLocation disguise = ResourceLocation.tryParse(config.itemDisguise);
        if (disguise == null || !BuiltInRegistries.ITEM.containsKey(disguise)) return;

        long until = level.getGameTime() + duration;
        String name = config.itemName;
        CustomData.update(DataComponents.CUSTOM_DATA, held, tag -> {
            CompoundTag look = new CompoundTag();
            look.putString("item", disguise.toString());
            look.putString("name", name);
            look.putUUID("victim", target.getUUID());
            look.putLong("until", until);
            tag.put(ITEM_DECEIT_TAG, look);
        });
    }

    public static int getDeceiveCount(LivingEntity caster, LivingEntity target) {
        Map<UUID, Integer> map = deceiveCounts.get(caster.getUUID());
        return map == null ? 0 : map.getOrDefault(target.getUUID(), 0);
    }

    public static void addDeceiveCount(LivingEntity caster, LivingEntity target) {
        deceiveCounts.computeIfAbsent(caster.getUUID(), k -> new ConcurrentHashMap<>()).merge(target.getUUID(), 1, Integer::sum);
    }


    @Nullable
    public static ActiveDeceit getDeceit(LivingEntity target, DeceitType type) {
        List<ActiveDeceit> list = active.get(target.getUUID());
        if (list == null) return null;
        long now = target.level().getGameTime();
        synchronized (list) {
            for (ActiveDeceit deceit : list) {
                if (deceit.type == type && deceit.expires > now) return deceit;
            }
        }
        return null;
    }

    public static boolean hasDeceit(LivingEntity target, DeceitType type) {
        return getDeceit(target, type) != null;
    }

    public static float getObservedSanity(LivingEntity observer, LivingEntity target) {
        Long until = sanityMasks.get(target.getUUID());
        if (until != null && until > target.level().getGameTime() && target instanceof ServerPlayer caster) {
            return getConfig(caster).sanity;
        }
        return target.getData(ModAttachments.SANITY_COMPONENT).getSanity();
    }

    public static boolean isHiddenFromIntuition(LivingEntity sensor, LivingEntity threat) {
        if (sensor == null || threat == null) return false;
        List<ActiveDeceit> list = active.get(sensor.getUUID());
        if (list == null) return false;
        long now = sensor.level().getGameTime();
        synchronized (list) {
            for (ActiveDeceit deceit : list) {
                if (deceit.type == DeceitType.INTUITION && deceit.expires > now && deceit.caster.equals(threat.getUUID())) return true;
            }
        }
        return false;
    }

    public static void onParasiteReleased(LivingEntity host) {
        if (!hasDeceit(host, DeceitType.PARASITE)) return;
        freeze(host, 20 * 8);
        if (host instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("ability.lotmcraft.deceit.parasite_linger").withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    public static void freeze(LivingEntity entity, int ticks) {
        frozen.put(entity.getUUID(), new AbstractMap.SimpleEntry<>(entity.position(), entity.level().getGameTime() + ticks));
        AbilityUtil.sealAllTargetAbilities(entity, entity, "deceit_freeze", ticks);
    }

    public static boolean handleDivination(ServerPlayer diviner, @Nullable ServerPlayer target) {
        ActiveDeceit deceit = getDeceit(diviner, DeceitType.DIVINATION);
        if (deceit == null) return false;

        ServerPlayer caster = diviner.getServer().getPlayerList().getPlayer(deceit.caster);
        if (caster == null) return false;
        DeceitConfig config = getConfig(caster);
        String targetName = target == null ? "?" : target.getGameProfile().getName();

        switch (config.divinationMode) {
            case 0 -> {
                int dx = config.fakeX - diviner.blockPosition().getX();
                int dz = config.fakeZ - diviner.blockPosition().getZ();
                int distance = (int) Math.sqrt(dx * dx + dz * dz);
                diviner.sendSystemMessage(Component.literal(String.format(
                        "§5You sense §d%s§5 at cords §d%d, %d, %d§5, about §d%d blocks §5away... their spirit feels like §dSequence %d§5.",
                        targetName, config.fakeX, config.fakeY, config.fakeZ, distance, config.fakeSequence)));
            }
            case 1 -> {
                ServerPlayer redirect = diviner.getServer().getPlayerList().getPlayerByName(config.redirectName);
                LivingEntity shown = redirect != null ? redirect : caster;
                BlockPos pos = shown.blockPosition();
                int dx = pos.getX() - diviner.blockPosition().getX();
                int dz = pos.getZ() - diviner.blockPosition().getZ();
                diviner.sendSystemMessage(Component.literal(String.format(
                        "§5You sense §d%s§5 at cords §d%d, %d, %d§5, about §d%d blocks §5away...",
                        targetName, pos.getX(), pos.getY(), pos.getZ(), (int) Math.sqrt(dx * dx + dz * dz))));
            }
            default -> {
                diviner.sendSystemMessage(Component.translatable("ability.lotmcraft.deceit.divination_higher").withStyle(ChatFormatting.DARK_RED));
                diviner.getData(ModAttachments.SANITY_COMPONENT).decreaseSanityAndSync(0.12f, diviner);
                diviner.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 6, 0));
                diviner.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 10, 0));
            }
        }
        return true;
    }

    public static void onFakeRespawn(ServerPlayer player) {
        if (!hasDeceit(player, DeceitType.HEALTH)) return;
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!isAllyDeceived(attacker, victim)) return;

        event.setCanceled(true);
        float amount = event.getAmount() * 0.8f;
        float newHealth = victim.getHealth() - amount;
        if (newHealth <= 0) {
            victim.setHealth(0.01f);
            victim.hurt(victim.damageSources().genericKill(), Float.MAX_VALUE);
        } else {
            victim.setHealth(newHealth);
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        LivingEntity attacker = victim.getLastAttacker();
        if (attacker != null && isAllyDeceived(attacker, victim) && victim.tickCount - victim.getLastHurtByMobTimestamp() < 2) {
            event.setCanceled(true);
        }
    }

    private static boolean isAllyDeceived(LivingEntity deceived, LivingEntity caster) {
        List<ActiveDeceit> list = active.get(deceived.getUUID());
        if (list == null) return false;
        long now = deceived.level().getGameTime();
        synchronized (list) {
            for (ActiveDeceit deceit : list) {
                if (deceit.type == DeceitType.ALLY && deceit.expires > now && deceit.caster.equals(caster.getUUID())) return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget != null && isAllyDeceived(mob, newTarget)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAbilityUse(AbilityUseEvent event) {
        LivingEntity user = event.getEntity();
        if (user == null || user.level().isClientSide) return;
        if (!hasDeceit(user, DeceitType.ABILITY)) return;

        float yaw = user.getYRot();
        float pitch = user.getXRot();
        user.setYRot(yaw + (RANDOM.nextFloat() - 0.5f) * 18f);
        user.setXRot(Math.max(-90, Math.min(90, pitch + (RANDOM.nextFloat() - 0.5f) * 10f)));
        user.setYHeadRot(user.getYRot());

        if (user instanceof ServerPlayer player && RANDOM.nextFloat() < 0.4f) {
            player.displayClientMessage(Component.translatable("ability.lotmcraft.deceit.ability_feeble").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    public static void onStartEating(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && event.getItem().has(DataComponents.FOOD) && isMeager(player)) {
            foodAtStart.put(player.getUUID(), player.getFoodData().getFoodLevel());
        }
    }

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        Integer before = foodAtStart.remove(player.getUUID());
        if (before == null || !isMeager(player)) return;
        player.getFoodData().setFoodLevel(Math.min(player.getFoodData().getFoodLevel(), before + 1));
        player.getFoodData().setSaturation(Math.min(player.getFoodData().getSaturationLevel(), 0.5f));
    }

    private static boolean isMeager(Player player) {
        ActiveDeceit deceit = getDeceit(player, DeceitType.HUNGER);
        if (deceit == null) return false;
        ServerPlayer caster = ((ServerPlayer) player).getServer().getPlayerList().getPlayer(deceit.caster);
        return caster != null && getConfig(caster).hungerMode == 3;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || frozen.isEmpty()) return;

        Map.Entry<Vec3, Long> state = frozen.get(entity.getUUID());
        if (state == null) return;
        if (entity.level().getGameTime() > state.getValue()) {
            frozen.remove(entity.getUUID());
            return;
        }
        Vec3 pos = state.getKey();
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity.position().distanceToSqr(pos) > 0.01) {
            entity.teleportTo(pos.x, pos.y, pos.z);
        }
        entity.hurtMarked = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel serverLevel) {
            LivingEntity host = de.jakob.lotm.beyonders.abilities.error.ParasitationAbility.getHostForEntity(serverLevel, event.getEntity());
            if (host != null) onParasiteReleased(host);
        }

        active.remove(event.getEntity().getUUID());
        frozen.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 0) return;
        long now = event.getServer().overworld().getGameTime();
        active.values().forEach(list -> list.removeIf(d -> d.expires < now));
        active.values().removeIf(List::isEmpty);
        sanityMasks.values().removeIf(until -> until < now);
    }

    public static void clearDeceitsOn(UUID target) {
        active.remove(target);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
