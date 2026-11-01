package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.*;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.ControllingUtils;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.ability_entities.ControlBodyDouble;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.util.data.Location;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeceiveWorldAbility extends SelectableAbility {

    private static final ResourceKey<Level> HISTORICAL_VOID = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "historical_void"));

    private static final Map<UUID, Location> historyReturn = new ConcurrentHashMap<>();

    private final Random rng = new Random();

    public DeceiveWorldAbility(String id) {
        super(id, 8);
        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(3, 4, 6, 8));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(6000f, 3600f, 2600f, 2000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 3));
    }

    @Override
    protected float getSpiritualityCost() {
        return 2000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.deceive_world.body",
                "ability.lotmcraft.deceive_world.connection",
                "ability.lotmcraft.deceive_world.block",
                "ability.lotmcraft.deceive_world.history"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            if (entity instanceof Player player && level.isClientSide) player.playSound(SoundEvents.BELL_RESONATE, 1, 0.8f);
            return;
        }

        switch (selectedAbility) {
            case 0 -> deceiveBody(serverLevel, player);
            case 1 -> deceiveConnection(serverLevel, player);
            case 2 -> deceiveBlock(serverLevel, player);
            case 3 -> deceiveHistory(serverLevel, player);
        }
    }

    private void fail(ServerPlayer player, String key) {
        AbilityUtil.sendActionBar(player, Component.translatable(key).withColor(ErrorUtil.DECEIT_COLOR));
    }

    private void deceiveBody(ServerLevel level, ServerPlayer player) {
        LivingEntity looked = AbilityUtil.getTargetEntity(player, 40, 2);
        if (looked == null) {
            fail(player, "ability.lotmcraft.deceive_world.no_target");
            return;
        }

        LivingEntity main = ErrorUtil.getMainBody(level, looked);
        LivingEntity person = main != null ? main : looked;

        if (DeceitHandler.getDeceiveCount(player, person) < 4) {
            fail(player, "ability.lotmcraft.deceive_world.body_not_deceived");
            return;
        }

        List<LivingEntity> bodies = new ArrayList<>(ErrorUtil.getSecondBodies(level, person));
        if (main != null) {
            bodies.remove(looked);
            bodies.addFirst(looked);
        }
        bodies.removeIf(body -> body instanceof ControlBodyDouble);
        if (bodies.isEmpty()) {
            fail(player, "ability.lotmcraft.deceive_world.no_second_body");
            return;
        }

        int casterSeq = AbilityUtil.getSeqWithArt(player, this);
        int personSeq = ErrorUtil.seqOf(person);
        if (AbilityUtil.isTargetSignificantlyStronger(casterSeq, personSeq)) {
            fail(player, "ability.lotmcraft.deceive_world.resisted");
            return;
        }

        LivingEntity body = bodies.getFirst();
        float chance = Math.max(0.05f, Math.min(0.9f, 0.55f + 0.1f * (personSeq - casterSeq)));
        if (LoopholeHandler.isLoopholeFound(person, player)) chance = Math.min(0.95f, chance + 0.25f);

        FxHandler.play(level, "deceit_body", body.position().add(0, body.getBbHeight() / 2, 0), 1.2);
        body.hurt(ModDamageTypes.source(level, ModDamageTypes.BEYONDER_GENERIC, player), Float.MAX_VALUE);

        if (rng.nextFloat() >= chance) {
            fail(player, "ability.lotmcraft.deceive_world.resisted");
            return;
        }

        FxHandler.play(level, "deceit_body", person.position().add(0, person.getBbHeight() / 2, 0), 1.2);
        person.hurt(ModDamageTypes.source(level, ModDamageTypes.BEYONDER_GENERIC, player), Float.MAX_VALUE);
        level.playSound(null, person.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
    }

    private void deceiveConnection(ServerLevel level, ServerPlayer player) {
        LivingEntity looked = AbilityUtil.getTargetEntity(player, 40, 2, false, false, true, false);
        if (looked == null) {
            fail(player, "ability.lotmcraft.deceive_world.no_target");
            return;
        }
        if (ProhibitionHandler.isInMarionetteZone(looked.position(), level, ErrorUtil.seqOf(looked))) {
            fail(player, "ability.lotmcraft.deceive_world.resisted");
            return;
        }

        int casterSeq = AbilityUtil.getSeqWithArt(player, this);
        LivingEntity main = ErrorUtil.getMainBody(level, looked);
        LivingEntity person = main != null ? main : looked;
        int personSeq = ErrorUtil.seqOf(person);

        if (AbilityUtil.isTargetSignificantlyStronger(casterSeq, personSeq)) {
            fail(player, "ability.lotmcraft.deceive_world.resisted");
            return;
        }

        boolean instant = casterSeq < personSeq || DeceitHandler.getDeceiveCount(player, person) >= 3;
        if (!instant && rng.nextFloat() > 0.45f) {
            fail(player, "ability.lotmcraft.deceive_world.resisted");
            return;
        }

        if (looked instanceof ServerPlayer controller && ControllingUtils.isControlling(controller) && main instanceof ControlBodyDouble bodyDouble) {
            Vec3 front = controller.position();
            FxHandler.play(level, "deceit_swap", front.add(0, 1, 0), 1);
            bodyDouble.teleportTo(front.x, front.y, front.z);
            ControllingUtils.cancel(controller, 0, true, false);
            return;
        }

        LivingEntity other;
        if (main != null) {
            other = main;
        } else {
            List<LivingEntity> bodies = ErrorUtil.getSecondBodies(level, looked);
            if (bodies.isEmpty()) {
                fail(player, "ability.lotmcraft.deceive_world.no_second_body");
                return;
            }
            other = bodies.get(rng.nextInt(bodies.size()));
        }

        if (other.level() != looked.level()) {
            fail(player, "ability.lotmcraft.deceive_world.too_far");
            return;
        }

        Vec3 a = looked.position();
        Vec3 b = other.position();
        FxHandler.play(level, "deceit_swap", a.add(0, 1, 0), 1);
        FxHandler.play(level, "deceit_swap", b.add(0, 1, 0), 1);
        looked.teleportTo(b.x, b.y, b.z);
        other.teleportTo(a.x, a.y, a.z);
        level.playSound(null, BlockPos.containing(a), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1, 0.8f);
    }

    private void deceiveBlock(ServerLevel level, ServerPlayer player) {
        BlockPos pos = AbilityUtil.getTargetBlock(player, 8, false);
        if (pos == null || !DeceitBlockHandler.canDeceive(level.getBlockState(pos), level.getBlockEntity(pos))) {
            fail(player, "ability.lotmcraft.deceive_world.block_invalid");
            return;
        }

        int ticks = (int) (20 * 60 * 10 * multiplier(player));
        DeceitBlockHandler.deceive(level, pos, ticks);
        FxHandler.play(level, "deceit_block", pos.getCenter(), 1);
        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.deceive_world.block_deceived",
                level.getBlockState(pos).getBlock().getName()).withColor(ErrorUtil.DECEIT_COLOR));
    }

    private void deceiveHistory(ServerLevel level, ServerPlayer player) {
        Location back = historyReturn.remove(player.getUUID());
        if (back != null && player.level().dimension() == HISTORICAL_VOID) {
            if (back.getLevel() instanceof ServerLevel returnLevel) {
                player.teleportTo(returnLevel, back.getPosition().x, back.getPosition().y, back.getPosition().z, Set.of(), player.getYRot(), player.getXRot());
            }
            return;
        }

        LivingEntity looked = AbilityUtil.getTargetEntity(player, 40, 2);
        UUID targetId = looked != null ? looked.getUUID() : ObservationHandler.getLastDeepDecrypted(player);
        if (targetId == null || ObservationHandler.getDecryptionLevel(ObservationHandler.getDecryptionProgress(player, targetId)) < 3) {
            fail(player, "ability.lotmcraft.deceive_world.history_not_decrypted");
            return;
        }

        LivingEntity target = ErrorUtil.findLiving(level, targetId);
        if (target == null || target.level().dimension() != HISTORICAL_VOID || !(target.level() instanceof ServerLevel voidLevel)) {
            fail(player, "ability.lotmcraft.deceive_world.history_no_body");
            return;
        }

        historyReturn.put(player.getUUID(), new Location(player.position(), level));
        Vec3 offset = target.getLookAngle().multiply(1, 0, 1).normalize().scale(-1.5);
        Vec3 destination = target.position().add(offset);
        FxHandler.play(level, "deceit_swap", player.position().add(0, 1, 0), 1);
        player.teleportTo(voidLevel, destination.x, destination.y, destination.z, Set.of(), player.getYRot(), player.getXRot());
        voidLevel.playSound(null, BlockPos.containing(destination), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.6f);
    }
}
