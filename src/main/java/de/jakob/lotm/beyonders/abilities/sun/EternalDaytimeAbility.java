package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUseEvent;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.beyonders.abilities.demoness.InvisibilityAbility;
import de.jakob.lotm.beyonders.abilities.demoness.ShadowConcealmentAbility;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.entity.custom.ability_entities.darkness_pathway.ConcealedDomainEntity;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.PlayPhotonBlockEffectPacket;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;


@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class EternalDaytimeAbility extends Ability {

    private static final double RADIUS = 250;
    private static final int LIGHT_SPACING = 7;

    private static final String[] INTERACTION_FLAGS = {"purification", "purification_holy", "light_source", "light_strong", "light_weak"};

    private static final Set<String> CONCEALMENT_ABILITIES = Set.of(
            "lesser_concealment_ability",
            "concealment_ability",
            "identity_concealment_ability",
            "concealed_domain_ability",
            "shadow_concealment_ability",
            "invisibility_ability",
            "space_concealment_ability",
            "psychological_invisibility_ability",
            "historical_void_hiding_ability"
    );

    private static final Set<String> SLEEP_ABILITIES = Set.of(
            "sleep_inducement_ability"
    );

    private static final Set<String> SLEEP_SUB_ABILITIES = Set.of(
            "ability.lotmcraft.midnight_poem.lullaby",
            "ability.lotmcraft.nightmare.nightmare"
    );

    public EternalDaytimeAbility(String id) {
        super(id, 3f, INTERACTION_FLAGS);
        interactionRadius = RADIUS;
        canBeShared = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeUsedInArtifact = false;
        postsUsedAbilityEventManually = true;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 0));
    }

    @Override
    protected float getSpiritualityCost() {
        return 7000;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        EternalDaytimeData data = EternalDaytimeData.get(serverLevel.getServer());

        if(data.getDomain(entity.getUUID()) != null) {
            data.removeDomain(serverLevel.getServer(), entity.getUUID());
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_DEACTIVATE, entity.getSoundSource(), 5f, .8f);
            return;
        }

        Vec3 center = entity.position();
        EternalDaytimeData.Domain domain = new EternalDaytimeData.Domain(
                entity.getUUID(),
                serverLevel.dimension(),
                center,
                RADIUS * multiplier(entity),
                AbilityUtil.getSeqWithArt(entity, this)
        );
        data.addDomain(domain);

        maintainLights(serverLevel, domain, data);

        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, entity.getSoundSource(), 10f, .7f);
        PacketHandler.sendToNearbyPlayers(
                new PlayPhotonBlockEffectPacket("eternal_daytime", BlockPos.containing(center), 0, 0, 0, 1, null, -1, false, false, null),
                serverLevel,
                center,
                512
        );
    }

    public static boolean isInEternalDaytime(LivingEntity entity) {
        return getAffectingDomain(entity) != null;
    }

    private static EternalDaytimeData.Domain getAffectingDomain(LivingEntity entity) {
        if(!(entity.level() instanceof ServerLevel serverLevel)) return null;

        EternalDaytimeData.Domain domain = EternalDaytimeData.get(serverLevel.getServer())
                .getAffectingDomain(serverLevel, entity.position(), BeyonderData.getSequence(entity));

        if(domain != null && domain.owner.equals(entity.getUUID())) return null;
        return domain;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;

        long gameTime = level.getGameTime();
        if(gameTime % 10 != 0) return;

        EternalDaytimeData data = EternalDaytimeData.get(level.getServer());
        if(data.getDomains().isEmpty()) return;

        for(EternalDaytimeData.Domain domain : new ArrayList<>(data.getDomains())) {
            if(!domain.dimension.equals(level.dimension())) continue;

            if(gameTime % 40 == 0) {
                maintainLights(level, domain, data);
            }

            affectEntities(level, domain);
            revealConcealedDomains(level, domain);

            if(gameTime % 20 == 0) {
                postInteraction(level, domain);
            }
        }
    }

    private static void maintainLights(ServerLevel level, EternalDaytimeData.Domain domain, EternalDaytimeData data) {
        int radius = (int) domain.radius;
        int cx = (int) Math.floor(domain.center.x);
        int cz = (int) Math.floor(domain.center.z);
        boolean changed = false;

        for(int x = -radius; x <= radius; x += LIGHT_SPACING) {
            for(int z = -radius; z <= radius; z += LIGHT_SPACING) {
                if(x * x + z * z > radius * radius) continue;

                int bx = cx + x;
                int bz = cz + z;
                if(!level.hasChunk(bx >> 4, bz >> 4)) continue;

                BlockPos pos = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz);
                if(level.getBlockState(pos).isAir()) {
                    level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
                    changed |= domain.lightBlocks.add(pos);
                }
            }
        }

        for(BlockPos pos : domain.lightBlocks) {
            if(level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
            }
        }

        if(changed) data.setDirty();
    }

    private static void affectEntities(ServerLevel level, EternalDaytimeData.Domain domain) {
        LivingEntity owner = level.getEntity(domain.owner) instanceof LivingEntity living ? living : null;

        AABB area = new AABB(
                domain.center.x - domain.radius, level.getMinBuildHeight(), domain.center.z - domain.radius,
                domain.center.x + domain.radius, level.getMaxBuildHeight(), domain.center.z + domain.radius
        );

        for(LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> domain.contains(level, e.position()))) {
            if(entity.getUUID().equals(domain.owner)) continue;
            if(BeyonderData.getSequence(entity) < domain.casterSequence) continue;

            purify(entity, owner);

            if(entity.hasEffect(ModEffects.ASLEEP)) {
                entity.removeEffect(ModEffects.ASLEEP);
            }

            reveal(level, entity);
        }
    }

    private static void purify(LivingEntity entity, LivingEntity owner) {
        boolean isEvil = AbilityUtil.isUndeadOrEvil(entity);
        if(isEvil && owner != null && !AbilityUtil.mayDamage(owner, entity)) return;

        entity.addEffect(new MobEffectInstance(ModEffects.PURIFICATION, 40, isEvil ? 1 : 0, false, false, false));
    }

    private static void reveal(ServerLevel level, LivingEntity entity) {
        boolean revealed = false;

        if(entity.hasEffect(ModEffects.CONCEALMENT)) {
            entity.removeEffect(ModEffects.CONCEALMENT);
            revealed = true;
        }

        if(entity.hasEffect(MobEffects.INVISIBILITY)
                || InvisibilityAbility.invisiblePlayers.contains(entity.getUUID())
                || ShadowConcealmentAbility.invisiblePlayers.contains(entity.getUUID())) {
            entity.removeEffect(MobEffects.INVISIBILITY);
            entity.setInvisible(false);
            revealed = true;
        }

        if(LOTMCraft.abilityHandler.getById("psychological_invisibility_ability") instanceof ToggleAbility psychologicalInvisibility
                && psychologicalInvisibility.isActiveForEntity(entity)) {
            psychologicalInvisibility.onAbilityUse(level, entity);
            revealed = true;
        }

        if(entity instanceof ServerPlayer player && revealIdentity(player)) {
            revealed = true;
        }

        if(revealed) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 5, 0, false, false, false));
        }
    }

    private static boolean revealIdentity(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(NeoForgeMod.NAMETAG_DISTANCE);
        if(attribute == null || attribute.getValue() != 0) return false;

        attribute.setBaseValue(64);
        if(player.getServer() != null) {
            player.getServer().getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(
                                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
                            ),
                            List.of(player)
                    )
            );
        }
        return true;
    }

    private static void revealConcealedDomains(ServerLevel level, EternalDaytimeData.Domain domain) {
        AABB area = new AABB(
                domain.center.x - domain.radius, level.getMinBuildHeight(), domain.center.z - domain.radius,
                domain.center.x + domain.radius, level.getMaxBuildHeight(), domain.center.z + domain.radius
        );

        level.getEntitiesOfClass(ConcealedDomainEntity.class, area, e -> domain.contains(level, e.position())).forEach(concealedDomain -> {
            LivingEntity concealedOwner = concealedDomain.getOwner();
            if(concealedOwner != null && BeyonderData.getSequence(concealedOwner) < domain.casterSequence) return;
            concealedDomain.discard();
        });
    }

    private static void postInteraction(ServerLevel level, EternalDaytimeData.Domain domain) {
        if(!(level.getEntity(domain.owner) instanceof LivingEntity owner)) return;

        Ability ability = LOTMCraft.abilityHandler.getById("eternal_daytime_ability");
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(level, domain.center, owner, ability, INTERACTION_FLAGS, domain.radius, 40));
    }

    @SubscribeEvent
    public static void onAbilityUse(AbilityUseEvent event) {
        LivingEntity entity = event.getEntity();
        if(entity.level().isClientSide) return;

        if(!isBlockedInEternalDaytime(event.getAbility(), entity)) return;
        if(!isInEternalDaytime(entity)) return;

        event.setCanceled(true);
        AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.eternal_daytime.blocked").withColor(0xFFffc53d));
    }

    private static boolean isBlockedInEternalDaytime(Ability ability, LivingEntity entity) {
        String id = ability.getId();
        if(CONCEALMENT_ABILITIES.contains(id) || SLEEP_ABILITIES.contains(id)) {
            return !(ability instanceof ToggleAbility toggleAbility && toggleAbility.isActiveForEntity(entity));
        }

        return ability instanceof SelectableAbility selectableAbility && SLEEP_SUB_ABILITIES.contains(selectableAbility.getSelectedAbility(entity));
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if(entity.level().isClientSide) return;

        var effect = event.getEffectInstance().getEffect().value();
        if(effect != ModEffects.ASLEEP.value() && effect != ModEffects.CONCEALMENT.value() && effect != MobEffects.INVISIBILITY.value()) return;

        if(isInEternalDaytime(entity)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
