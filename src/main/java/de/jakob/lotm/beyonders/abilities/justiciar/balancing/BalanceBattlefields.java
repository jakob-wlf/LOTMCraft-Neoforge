package de.jakob.lotm.beyonders.abilities.justiciar.balancing;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.RingEffectManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Logical battlefields share the world, but have separate rosters and interaction rules. */
public final class BalanceBattlefields {
    private static final List<Session> ACTIVE = new ArrayList<>();
    private static final Map<LivingEntity, Long> LAST_NOTICE = new WeakHashMap<>();
    public static final double RADIUS = 120;
    private static final ChatFormatting[] COLORS = {ChatFormatting.GOLD, ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.GREEN, ChatFormatting.BLUE, ChatFormatting.RED};
    private static final Vector3f[] RGB = {new Vector3f(1f,.75f,.2f), new Vector3f(.2f,.9f,1f),
            new Vector3f(.9f,.4f,1f), new Vector3f(.4f,1f,.3f), new Vector3f(.3f,.5f,1f), new Vector3f(1f,.3f,.2f)};

    private BalanceBattlefields() {}

    public static void create(ServerLevel level, LivingEntity owner) {
        ACTIVE.removeIf(session -> {
            if (!session.owner.getUUID().equals(owner.getUUID())) return false;
            session.finish();
            return true;
        });
        var participants = level.getEntitiesOfClass(LivingEntity.class, new AABB(owner.position(), owner.position()).inflate(RADIUS),
                entity -> entity.isAlive() && !entity.isSpectator() && entity.distanceTo(owner) <= RADIUS
                        && (entity == owner || entity instanceof Player || BeyonderData.isBeyonder(entity)
                        || entity.getData(ModAttachments.MARIONETTE_COMPONENT).isMarionette()
                        || entity instanceof Mob mob && mob.getTarget() != null));
        // Roster membership is fixed; mutually allied components can never be split by matching.
        List<BattlePartitioner.Combatant> combatants = participants.stream().map(entity -> {
            Set<UUID> allies = new HashSet<>();
            AllyUtil.getAllAllies(entity).forEach(ally -> allies.add(ally.uuid()));
            participants.stream().filter(other -> entity.isAlliedTo(other) || AllyUtil.areAllies(entity, other)
                    || AllyUtil.areAllies(other, entity)).forEach(other -> allies.add(other.getUUID()));
            double strength = Math.max(1, BeyonderData.getMultiplier(entity)) * Math.sqrt(Math.max(1, entity.getMaxHealth()));
            return new BattlePartitioner.Combatant(entity.getUUID(), strength, allies);
        }).toList();
        BattlePartitioner.Plan plan = BattlePartitioner.divide(combatants, owner.getUUID());
        Session session = new Session(level, owner, plan, participants);
        ACTIVE.add(session);
        for (LivingEntity entity : participants) session.announce(entity);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(session.center) > RADIUS + 32) continue;
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1f, .6f);
            RingEffectManager.createRingForPlayer(session.center.add(0,.4,0), (float) RADIUS, 36,
                    1f,.75f,.2f,.35f,.6f,1.2f,1f,false,level,player);
        }
    }

    public static boolean canInteract(LivingEntity source, LivingEntity target) {
        if (source == null || target == null || source == target) return true;
        for (Session session : ACTIVE) {
            if (!session.active()) continue;
            int a = session.cell(source), b = session.cell(target);
            if (!BattlePartitioner.permits(a, b)) return false;
            if ((a >= 0 && !session.inside(source)) || (b >= 0 && !session.inside(target))) return false;
        }
        return true;
    }

    /** Attribution for persistent areas whose caster is no longer on the current call stack. */
    public static boolean canApplyFrom(UUID source, LivingEntity target) {
        if (source.equals(target.getUUID())) return true;
        for (Session session : ACTIVE) {
            if (!session.active()) continue;
            int a = session.cells.getOrDefault(source, -3);
            if (!BattlePartitioner.permits(a, session.cell(target))) return false;
        }
        return true;
    }

    public static boolean blocked(LivingEntity entity) {
        for (Session session : ACTIVE) {
            if (session.active() && (session.cell(entity) == -1
                    || session.cell(entity) >= 0 && !session.inside(entity))) return true;
        }
        return false;
    }

    public static boolean canTravel(LivingEntity entity, ServerLevel destination, Vec3 position) {
        for (Session session : ACTIVE) {
            if (session.active() && session.cell(entity) >= 0
                    && (destination != session.level || position.distanceTo(session.center) > RADIUS - .5)) return false;
        }
        return true;
    }

    public static void enforceMovement(LivingEntity entity) {
        for (Session session : ACTIVE) {
            if (!session.active()) continue;
            int cell = session.cell(entity);
            if (cell >= 0) {
                if (!session.inside(entity)) {
                    Vec3 last = session.positions.getOrDefault(entity.getUUID(), session.center);
                    entity.teleportTo(session.level, last.x, last.y, last.z, Set.of(), entity.getYRot(), entity.getXRot());
                    entity.setDeltaMovement(Vec3.ZERO);
                    entity.hurtMarked = true;
                    notice(entity);
                } else if (entity.position().distanceTo(session.center) < RADIUS - 1) {
                    session.positions.put(entity.getUUID(), entity.position());
                }
            } else if (cell == -1 && session.inside(entity)) {
                Vec3 outward = entity.position().subtract(session.center).multiply(1,0,1).normalize();
                if (outward.lengthSqr() < .001) outward = new Vec3(1,0,0);
                entity.setDeltaMovement(outward.scale(.6).add(0,entity.getDeltaMovement().y,0));
                entity.hurtMarked = true;
                notice(entity);
            }
        }
    }

    public static void tick() {
        var iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (!session.active()) { session.finish(); iterator.remove(); continue; }
            if (session.level.getGameTime() % 5 == 0) session.resolve();
            if (session.level.getGameTime() % 10 == 0) session.visuals();
        }
    }

    public static void clear() { ACTIVE.clear(); LAST_NOTICE.clear(); }

    public static void notice(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        long now = player.serverLevel().getServer().overworld().getGameTime();
        if (now - LAST_NOTICE.getOrDefault(entity, -100L) < 40) return;
        LAST_NOTICE.put(entity, now);
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.balancing.interference_prohibited")
                .withStyle(ChatFormatting.RED));
    }

    private static final class Session {
        final ServerLevel level;
        final LivingEntity owner;
        final Vec3 center;
        final long expiry;
        final Map<UUID, LivingEntity> members = new HashMap<>();
        final Map<UUID, Integer> cells;
        final Map<UUID, Integer> factions;
        final Map<UUID, Vec3> positions = new HashMap<>();
        final Set<Integer> contested = new HashSet<>();
        boolean ended;

        Session(ServerLevel level, LivingEntity owner, BattlePartitioner.Plan plan, List<LivingEntity> participants) {
            this.level = level; this.owner = owner; this.center = owner.position();
            this.expiry = level.getGameTime() + 20 * 60;
            this.cells = new HashMap<>(plan.cells()); this.factions = plan.factions();
            participants.forEach(entity -> { members.put(entity.getUUID(), entity); positions.put(entity.getUUID(), entity.position()); });
            for (int cell : new HashSet<>(cells.values())) {
                if (cell >= 0 && cells.entrySet().stream().filter(entry -> entry.getValue() == cell)
                        .map(entry -> factions.get(entry.getKey())).distinct().count() > 1) contested.add(cell);
            }
        }

        boolean active() { return !ended && owner.isAlive() && owner.level() == level && !owner.isRemoved() && level.getGameTime() < expiry; }
        boolean inside(LivingEntity entity) { return entity.level() == level && entity.position().distanceTo(center) <= RADIUS; }
        int cell(LivingEntity entity) {
            if (members.get(entity.getUUID()) == entity && entity.isAlive()) return cells.getOrDefault(entity.getUUID(), -2);
            return inside(entity) ? -1 : -3;
        }

        void announce(LivingEntity entity) {
            int cell = cells.getOrDefault(entity.getUUID(), -1);
            Component message = cell < 0 ? Component.translatable("ability.lotmcraft.balancing.waiting").withStyle(ChatFormatting.RED)
                    : Component.translatable("ability.lotmcraft.balancing.assigned", cell + 1).withStyle(COLORS[cell % COLORS.length]);
            entity.sendSystemMessage(message);
            AbilityUtil.sendActionBar(entity, message);
        }

        void resolve() {
            for (int cell : new HashSet<>(contested)) {
                Set<Integer> aliveFactions = new HashSet<>();
                members.forEach((id, entity) -> {
                    if (cells.getOrDefault(id,-2) == cell && entity.isAlive() && !entity.isRemoved()) aliveFactions.add(factions.get(id));
                });
                if (aliveFactions.size() > 1) continue;
                Optional<Integer> waiting = members.entrySet().stream()
                        .filter(entry -> cells.getOrDefault(entry.getKey(),-2) == -1 && entry.getValue().isAlive() && !entry.getValue().isRemoved())
                        .map(entry -> factions.get(entry.getKey())).sorted().findFirst();
                if (aliveFactions.size() == 1 && waiting.isPresent()) {
                    members.forEach((id, entity) -> {
                        if (cells.getOrDefault(id,-2) == -1 && factions.get(id).equals(waiting.get())) {
                            cells.put(id, cell); announce(entity);
                        }
                    });
                } else {
                    contested.remove(cell);
                    members.forEach((id, entity) -> {
                        if (cells.getOrDefault(id,-2) == cell) {
                            cells.put(id,-2);
                            if (entity.isAlive()) entity.sendSystemMessage(Component.translatable("ability.lotmcraft.balancing.battle_finished").withStyle(ChatFormatting.GOLD));
                        }
                    });
                    if (contested.isEmpty()) ended = true;
                }
            }
        }

        void visuals() {
            members.forEach((id, entity) -> {
                int cell = cells.getOrDefault(id,-2);
                if (cell < 0 || !entity.isAlive() || entity.level() != level) return;
                DustParticleOptions dust = new DustParticleOptions(RGB[cell % RGB.length], 1.2f);
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12;
                    level.sendParticles(dust, entity.getX() + Math.cos(angle), entity.getY()+.2,
                            entity.getZ() + Math.sin(angle), 1,0,0,0,0);
                }
            });
        }

        void finish() {
            for (LivingEntity entity : members.values()) {
                if (!entity.isAlive()) continue;
                entity.sendSystemMessage(Component.translatable("ability.lotmcraft.balancing.division_ended").withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
