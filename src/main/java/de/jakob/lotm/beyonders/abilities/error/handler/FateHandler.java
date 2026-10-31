package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.ParasitationComponent;
import de.jakob.lotm.beyonders.abilities.visionary.prophecy.Prophecy;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class FateHandler {

    private static final Map<UUID, List<Prophecy>> counterable = new ConcurrentHashMap<>();

    private static final Map<UUID, Erosion> erosions = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Long>> fateImmunity = new ConcurrentHashMap<>();
    private static final Map<UUID, StolenFate> lastStolen = new ConcurrentHashMap<>();

    public record Erosion(UUID thief, long until, float fraction) {}

    public static class StolenFate {
        public final UUID target;
        public final String targetName;
        public final long time;
        public final float fraction;
        public final List<MobEffectInstance> effects = new ArrayList<>();
        public int luck;
        public final List<UUID> itemTags = new ArrayList<>();

        public StolenFate(UUID target, String targetName, long time, float fraction) {
            this.target = target;
            this.targetName = targetName;
            this.time = time;
            this.fraction = fraction;
        }
    }

    public static final String FATE_ITEM_TAG = "lotm_stolen_fate";

    @Nullable
    private static ServerPlayer onlinePlayer(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    public static void onProphecyAdded(UUID targetId, Prophecy prophecy) {
        ServerPlayer target = onlinePlayer(targetId);
        if (target == null || !ErrorUtil.hasPassive(target, "fate_ability")) return;
        if (prophecy.casterId() != null && prophecy.casterId().equals(targetId)) return;

        ServerPlayer caster = prophecy.casterId() == null ? null : onlinePlayer(prophecy.casterId());
        int targetSeq = BeyonderData.getSequence(target);
        int casterSeq = caster == null ? LOTMCraft.NON_BEYONDER_SEQ : BeyonderData.getSequence(caster);
        if (casterSeq < targetSeq) return;

        String casterName = caster == null ? "?" : caster.getGameProfile().getName();
        String trigger = prophecy.triggerType() == null ? "?" : ObservationHandler.prettify(prophecy.triggerType().name().toLowerCase(Locale.ROOT));
        String action = prophecy.trigger() == null ? "?" : ObservationHandler.prettify(prophecy.trigger().getActionType().name().toLowerCase(Locale.ROOT));

        target.sendSystemMessage(Component.translatable("ability.lotmcraft.fate.prophecy_sensed", casterName, trigger, action)
                .withStyle(ChatFormatting.DARK_PURPLE));

        if (casterSeq > targetSeq) {
            counterable.computeIfAbsent(targetId, k -> Collections.synchronizedList(new ArrayList<>())).add(prophecy);
            target.sendSystemMessage(Component.translatable("ability.lotmcraft.fate.prophecy_counterable").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    public static int counterTamper(ServerPlayer player) {
        List<Prophecy> list = counterable.remove(player.getUUID());
        if (list == null || list.isEmpty()) return 0;

        int returned = 0;
        for (Prophecy prophecy : list) {
            if (prophecy.casterId() == null) continue;
            BeyonderData.playerMap.removeProphecy(player.getUUID(), prophecy);
            if (!BeyonderData.playerMap.contains(prophecy.casterId())) continue;
            BeyonderData.playerMap.addProphecy(prophecy.casterId(),
                    new Prophecy(prophecy.casterId(), prophecy.trigger(), prophecy.triggerType(), player.getUUID()));
            ServerPlayer caster = onlinePlayer(prophecy.casterId());
            if (caster != null) {
                caster.sendSystemMessage(Component.translatable("ability.lotmcraft.fate.prophecy_returned").withStyle(ChatFormatting.DARK_RED));
            }
            returned++;
        }
        return returned;
    }

    public static boolean hasCounterable(UUID player) {
        List<Prophecy> list = counterable.get(player);
        return list != null && !list.isEmpty();
    }

    public static void startErosion(LivingEntity victim, LivingEntity thief, float fraction, int ticks) {
        erosions.put(victim.getUUID(), new Erosion(thief.getUUID(), victim.level().getGameTime() + ticks, fraction));
    }

    public static void stopErosion(UUID victim) {
        erosions.remove(victim);
    }

    public static void grantImmunity(LivingEntity thief, LivingEntity formerOwner, int ticks) {
        fateImmunity.computeIfAbsent(thief.getUUID(), k -> new ConcurrentHashMap<>())
                .put(formerOwner.getUUID(), thief.level().getGameTime() + ticks);
    }

    public static void setLastStolen(UUID thief, StolenFate fate) {
        lastStolen.put(thief, fate);
    }

    @Nullable
    public static StolenFate getLastStolen(UUID thief) {
        return lastStolen.get(thief);
    }

    @Nullable
    public static StolenFate takeLastStolen(UUID thief) {
        return lastStolen.remove(thief);
    }

    public static boolean hasRecentlyStolenFate(LivingEntity thief, LivingEntity target) {
        StolenFate fate = lastStolen.get(thief.getUUID());
        return fate != null && fate.target.equals(target.getUUID()) && thief.level().getGameTime() - fate.time < 20 * 60 * 5;
    }

    public static void markFateItem(ItemStack stack, UUID fateId) {
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putUUID(FATE_ITEM_TAG, fateId));
    }

    @Nullable
    public static UUID getFateItemId(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        return tag.hasUUID(FATE_ITEM_TAG) ? tag.getUUID(FATE_ITEM_TAG) : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Map<UUID, Long> immunity = fateImmunity.get(victim.getUUID());
        if (immunity == null) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;
        Long until = immunity.get(attacker.getUUID());
        if (until != null && until > victim.level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 0 || erosions.isEmpty()) return;

        for (Map.Entry<UUID, Erosion> entry : new ArrayList<>(erosions.entrySet())) {
            Erosion erosion = entry.getValue();
            LivingEntity victim = null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(entry.getKey()) instanceof LivingEntity living) {
                    victim = living;
                    break;
                }
            }
            if (victim == null) continue;
            if (!victim.isAlive() || victim.level().getGameTime() > erosion.until) {
                erosions.remove(entry.getKey());
                continue;
            }
            ServerLevel level = (ServerLevel) victim.level();
            LivingEntity thief = ErrorUtil.findLiving(level, erosion.thief);
            float damage = Math.max(1f, victim.getMaxHealth() * 0.035f * erosion.fraction);
            victim.hurt(ModDamageTypes.source(level, ModDamageTypes.BEYONDER_GENERIC, thief), damage);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;

        erosions.remove(dead.getUUID());

        UUID owner = null;
        String kind = null;
        if (dead instanceof AvatarEntity avatar && avatar.getOriginalOwner() != null) {
            owner = avatar.getOriginalOwner();
            kind = "avatar";
        } else {
            ParasitationComponent parasite = dead.getData(ModAttachments.PARASITE_COMPONENT);
            if (parasite.isParasited() && parasite.getParasiteUUID() != null) {
                owner = parasite.getParasiteUUID();
                kind = "host";
            }
        }
        if (owner == null) return;

        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer == null || !ErrorUtil.hasPassive(ownerPlayer, "fate_ability")) return;

        Entity killer = event.getSource().getEntity();
        String killerName = killer == null ? "?" : killer.getName().getString();
        ownerPlayer.sendSystemMessage(Component.translatable("ability.lotmcraft.fate.death_sensed_" + kind, killerName,
                dead.blockPosition().getX(), dead.blockPosition().getY(), dead.blockPosition().getZ()).withStyle(ChatFormatting.DARK_PURPLE));
    }

    public static void redirectHunters(ServerLevel level, LivingEntity thief, LivingEntity victim) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, thief.getBoundingBox().inflate(48), m -> m.getTarget() == thief)) {
            if (AbilityUtil.mayTarget(mob, victim)) mob.setTarget(victim);
        }
    }
}
