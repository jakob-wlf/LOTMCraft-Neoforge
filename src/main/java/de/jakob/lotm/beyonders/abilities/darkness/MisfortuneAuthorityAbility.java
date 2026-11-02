package de.jakob.lotm.beyonders.abilities.darkness;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.wheel_of_fortune.calamities.Earthquake;
import de.jakob.lotm.beyonders.abilities.wheel_of_fortune.calamities.Meteor;
import de.jakob.lotm.beyonders.abilities.wheel_of_fortune.calamities.Tornado;
import de.jakob.lotm.entity.custom.ability_entities.VolcanoEntity;
import de.jakob.lotm.entity.custom.ability_entities.tyrant_pathway.TsunamiEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class MisfortuneAuthorityAbility extends SelectableAbility {
    private static final int DOOM_REACH = 500;
    private static final int MISFORTUNE_REACH = 250;
    private static final int MISFORTUNE_AMMOUNT = -400;
    private static final int LUCK_FLOOR = -6360;
    private static final float UPKEEP = 80f;
    private static final long DISASTER_TICKS = 20L * ThreadLocalRandom.current().nextInt(1, 2);
    private static final Earthquake EARTHQUAKE = new Earthquake();
    private static final Meteor METEOR = new Meteor();
    private static final Tornado TORNADO = new Tornado();

    private static final int CATASTROPHE_REACH = 48;
    private static final Set<UUID> doom = new HashSet<>();
    private static final Set<UUID> nation = new HashSet<>();
    private static final Set<UUID> catastrophe = new HashSet<>();
    private static final Map<UUID, Long> nextDisaster = new HashMap<>();
    private static final Map<UUID, Long> nextCatastrophe = new HashMap<>();

    public MisfortuneAuthorityAbility(String id) {
        super(id, 5);
        canBeUsedByNPC = false;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 1));
    }

    @Override
    protected float getSpiritualityCost() {
        return 10000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.misfortune_authority.nations_doom",
                "ability.lotmcraft.misfortune_authority.nation_of_misfortune",
                "ability.lotmcraft.misfortune_authority.aura_of_catastrophe"
        };
    }

    @Override
    public String[] getSequenceDependantAbilityNames(int sequence) {
        return switch (sequence) {
            case 1 -> new String[]{
                    "ability.lotmcraft.misfortune_authority.nations_doom",
                    "ability.lotmcraft.misfortune_authority.nation_of_misfortune"
            };
            default -> new String[]{
                    "ability.lotmcraft.misfortune_authority.nations_doom",
                    "ability.lotmcraft.misfortune_authority.nation_of_misfortune",
                    "ability.lotmcraft.misfortune_authority.aura_of_catastrophe"
            };
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (!(entity instanceof Player)) abilityIndex = 0;
        switch (abilityIndex) {
            case 0 -> nationDoom(entity);
            case 1 -> nationOfMisfortune(level, entity);
            case 2 -> {
                if (BeyonderData.getSequence(entity) > 0) return;
                aura(level, entity);
            }
        }
    }

    private void nationDoom(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (doom.remove(player.getUUID())) bar(player, "The nation's doom lifts.");
        else {
            doom.add(player.getUUID());
            bar(player, "The nation's doom settles.");
        }
    }

    private void nationOfMisfortune(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return;
        if (nation.remove(player.getUUID())) {
            nextDisaster.remove(player.getUUID());
            bar(player, "The nation of misfortune fades.");
            return;
        }
        nation.add(player.getUUID());
        nextDisaster.put(player.getUUID(), serverLevel.getGameTime() + DISASTER_TICKS);
        bar(player, "A nation of misfortune rises.");
    }

    private void aura(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return;
        if (catastrophe.remove(player.getUUID())) {
            nextCatastrophe.remove(player.getUUID());
            bar(player, "The aura of catastrophe fades.");
            return;
        }
        catastrophe.add(player.getUUID());
        nextCatastrophe.put(player.getUUID(), serverLevel.getGameTime() + DISASTER_TICKS);
        bar(player, "An aura of catastrophe settles.");
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        UUID id = player.getUUID();
        boolean dooming = doom.contains(id);
        boolean misfortunate = nation.contains(id);
        boolean catastrophic = catastrophe.contains(id);
        if (!dooming && !misfortunate && !catastrophic) return;
        if (player.tickCount % 100 == 0) {
            if (dooming && !pay(player)) {
                doom.remove(id);
                bar(player, "The nation's doom lifts.");
            }
            if (misfortunate && !pay(player)) {
                nation.remove(id);
                nextDisaster.remove(id);
                bar(player, "The nation of misfortune fades.");
            }
            if (catastrophic && !pay(player)) {
                catastrophe.remove(id);
                nextCatastrophe.remove(id);
                bar(player, "The aura of catastrophe fades.");
            }
            dooming = doom.contains(id);
            misfortunate = nation.contains(id);
            catastrophic = catastrophe.contains(id);
            if (!dooming && !misfortunate && !catastrophic) return;
        }
        if ((dooming || catastrophic) && player.tickCount % 40 == 0) stackDoom(player, level);
        if (misfortunate) {
            long due = nextDisaster.getOrDefault(id, 0L);
            if (level.getGameTime() >= due) {
                spawnDisaster(player, level, MISFORTUNE_REACH);
                nextDisaster.put(id, level.getGameTime() + DISASTER_TICKS);
            }
        }
        if (catastrophic) {
            long due = nextCatastrophe.getOrDefault(id, 0L);
            if (level.getGameTime() >= due) {
                spawnDisaster(player, level, CATASTROPHE_REACH);
                nextCatastrophe.put(id, level.getGameTime() + DISASTER_TICKS);
            }
        }
    }

    private static boolean pay(ServerPlayer player) {
        if (BeyonderData.getSpirituality(player) <= UPKEEP) return false;
        BeyonderData.reduceSpirituality(player, UPKEEP);
        return true;
    }

    private static void stackDoom(ServerPlayer caster, ServerLevel level) {
        int casterSeq = BeyonderData.getSequence(caster);
        for (LivingEntity target : inSquare(caster, level, DOOM_REACH)) {
            int targetSeq = BeyonderData.getSequence(target);
            if (targetSeq < casterSeq) continue;
            float scale = 1f;
            if (targetSeq == 0) scale *= 0.2f;
            if (ConcealmentAbility.sees(target) || ConcealmentAbility.peaceful(target) || ConcealmentAbility.hiddenFromHarm(target)) scale *= 0.25f;
            if ("wheel_of_fortune".equals(BeyonderData.getPathway(target))) scale *= 0.4f;
            int step = Math.round(MISFORTUNE_AMMOUNT * scale);
            if (step > -1) step = -1;
            AbilityUtil.addTargetLuckWithMin(caster, target, step, LUCK_FLOOR);
        }
    }

    private static void spawnDisaster(ServerPlayer caster, ServerLevel level, int reach) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = caster.getX() + random.nextDouble(-reach, reach);
        double z = caster.getZ() + random.nextDouble(-reach, reach);
        Vec3 pos = new Vec3(x, caster.getY(), z);
        boolean griefing = BeyonderData.isGriefingEnabled(caster);
        boolean water = nearWater(level, BlockPos.containing(pos));
        int roll = random.nextInt(water ? 5 : 4);
        switch (roll) {
            case 0 -> METEOR.spawnCalamity(level, pos, 8f, griefing, caster);
            case 1 -> TORNADO.spawnCalamity(level, pos, 8f, griefing, caster);
            case 2 -> EARTHQUAKE.spawnCalamity(level, pos, 8f, griefing, caster);
            case 3 -> level.addFreshEntity(new VolcanoEntity(level, pos, 8f, caster));
            default -> {
                double angle = random.nextDouble() * Math.PI * 2;
                Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                level.addFreshEntity(new TsunamiEntity(level, pos, direction, 8f, griefing, caster));
            }
        }
    }

    private static boolean nearWater(ServerLevel level, BlockPos center) {
        for (int x = -6; x <= 6; x++) {
            for (int y = -3; y <= 2; y++) {
                for (int z = -6; z <= 6; z++) {
                    if (level.getFluidState(center.offset(x, y, z)).is(Fluids.WATER)) return true;
                }
            }
        }
        return false;
    }

    private static List<LivingEntity> inSquare(ServerPlayer caster, ServerLevel level, int reach) {
        AABB box = new AABB(
                caster.getX() - reach, level.getMinBuildHeight(), caster.getZ() - reach,
                caster.getX() + reach, level.getMaxBuildHeight(), caster.getZ() + reach);
        return level.getEntitiesOfClass(LivingEntity.class, box, living -> living != caster && living.isAlive());
    }

    private static void bar(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withColor(0xFF3300b5)));
    }
}
