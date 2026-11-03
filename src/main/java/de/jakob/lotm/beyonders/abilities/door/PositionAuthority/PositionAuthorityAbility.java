package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.door.DimensionSightAbility;
import de.jakob.lotm.beyonders.abilities.door.PlayerTeleportationAbility;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.HoldCastProgressPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import javax.annotation.Nullable;
import java.util.*;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public abstract class PositionAuthorityAbility extends Ability {

    private static final String PREFIX = "position:";
    private record Order(ServerPlayer target, @Nullable ServerLevel level, @Nullable Vec3 pos) {}

    private static final HashMap<UUID, Order> pending = new HashMap<>();
    private static final HashSet<UUID> charging = new HashSet<>();

    protected int castTicks = 40;

    protected PositionAuthorityAbility(String id, float cooldown) {
        super(id, cooldown);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeShared = false;
        cannotBeStolen = true;
    }

    protected abstract String command();

    protected abstract void apply(ServerPlayer caster, ServerPlayer target, int seq);

    protected boolean acceptsDestination() {
        return false;
    }

    protected void applyAt(ServerPlayer caster, ServerPlayer target, ServerLevel level, Vec3 pos, int seq) {
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 2));
    }

    @Override
    public boolean canUse(LivingEntity entity, boolean hasToHaveAbility, boolean doesConsumeSpirituality, boolean isCopied) {
        return pending.containsKey(entity.getUUID()) && super.canUse(entity, hasToHaveAbility, doesConsumeSpirituality, isCopied);
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer caster)) return;
        Order order = pending.remove(caster.getUUID());
        if (order == null || !order.target().isAlive()) return;
        int seq = AbilityUtil.getSeqWithArt(caster, this);
        if (order.level() != null) applyAt(caster, order.target(), order.level(), order.pos(), seq);
        else                       apply(caster, order.target(), seq);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String raw = event.getRawText().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith(PREFIX)) return;
        String rest = raw.substring(PREFIX.length()).trim();
        String lower = rest.toLowerCase(Locale.ROOT);

        PositionAuthorityAbility chosen = null;
        for (Ability ability : LOTMCraft.abilityHandler.getAbilities()) {
            if (!(ability instanceof PositionAuthorityAbility position)) continue;
            String command = position.command() + " ";
            if (!lower.startsWith(command)) continue;
            if (chosen == null || position.command().length() > chosen.command().length()) chosen = position;
        }
        if (chosen == null) return;

        ServerPlayer caster = event.getPlayer();
        if (!chosen.hasAbility(caster, false)) return;

        // It's a command, not a chat message: don't broadcast who is being targeted
        // Sorry dont execute me pls q_q
        event.setCanceled(true);

        int color = BeyonderData.pathwayInfos.get("door").color();
        String[] args = rest.substring(chosen.command().length()).trim().split("\\s+");

        boolean destination = chosen.acceptsDestination() && (args.length == 4 || args.length == 5);
        String name = destination && args.length == 4 ? caster.getGameProfile().getName() : args[0];

        ServerPlayer target = caster.server.getPlayerList().getPlayerByName(name);
        if (target == null || (target == caster && !destination) || (target != caster && DimensionSightAbility.unwatchable(caster, target))) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.not_found", name).withColor(color));
            return;
        }
        if (PlayerTeleportationAbility.unreachable(target) || PlayerTeleportationAbility.unreachable(caster)) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.unreachable", name).withColor(color));
            return;
        }

        ServerLevel level = null;
        Vec3 pos = null;
        if (destination) {
            int at = args.length - 4;
            ResourceLocation dimension = ResourceLocation.tryParse(args[at].toLowerCase(Locale.ROOT));
            level = dimension == null ? null : caster.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) {
                AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.unknown_dimension", args[at]).withColor(color));
                return;
            }
            if (PlayerTeleportationAbility.unreachable(level.dimension())) {
                AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.unreachable", args[at]).withColor(color));
                return;
            }
            pos = coordinates(args, at + 1, target.position());
            if (pos == null || !level.getWorldBorder().isWithinBounds(BlockPos.containing(pos))
                    || pos.y < level.getMinBuildHeight() || pos.y > level.getMaxBuildHeight() + 256) {
                AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.invalid_coordinates").withColor(color));
                return;
            }
        }

        PositionAuthorityAbility ability = chosen;
        Order order = new Order(target, level, pos);
        caster.server.execute(() -> ability.charge(caster, order));
    }

    @Nullable
    private static Vec3 coordinates(String[] args, int from, Vec3 origin) {
        double[] values = new double[3];
        double[] base = {origin.x, origin.y, origin.z};
        try {
            for (int i = 0; i < 3; i++) {
                String arg = args[from + i];
                if (arg.startsWith("~")) values[i] = base[i] + (arg.length() > 1 ? Double.parseDouble(arg.substring(1)) : 0);
                else                     values[i] = Double.parseDouble(arg);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new Vec3(values[0], values[1], values[2]);
    }

    private void charge(ServerPlayer caster, Order order) {
        if (!charging.add(caster.getUUID())) return;
        pending.put(caster.getUUID(), order);
        boolean ready = canUse(caster);
        pending.remove(caster.getUUID());
        if (!ready) {
            charging.remove(caster.getUUID());
            return;
        }

        ServerPlayer target = order.target();
        int color = BeyonderData.pathwayInfos.get("door").color();
        if (target != caster) AbilityUtil.sendActionBar(target, Component.translatable("ability.lotmcraft.position_authority.ripple").withColor(color));
        int[] ticks = {0};
        ServerScheduler.scheduleForDuration(0, 1, castTicks, () -> {
            ticks[0]++;
            PacketHandler.sendToPlayer(caster, new HoldCastProgressPacket(Math.min(ticks[0], castTicks), castTicks));
        }, () -> {
            charging.remove(caster.getUUID());
            PacketHandler.sendToPlayer(caster, new HoldCastProgressPacket(0, 0));
            if (!caster.isAlive() || caster.hasDisconnected() || !target.isAlive() || target.hasDisconnected()) return;
            if (target != caster && DimensionSightAbility.unwatchable(caster, target)) {
                AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.not_found", target.getName()).withColor(color));
                return;
            }
            if (PlayerTeleportationAbility.unreachable(target) || PlayerTeleportationAbility.unreachable(caster)) {
                AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.unreachable", target.getName()).withColor(color));
                return;
            }
            pending.put(caster.getUUID(), order);
            useAbility(caster.serverLevel(), caster);
            pending.remove(caster.getUUID());
        }, caster.serverLevel());
    }
}
