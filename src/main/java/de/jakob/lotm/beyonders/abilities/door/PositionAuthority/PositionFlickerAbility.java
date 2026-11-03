package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.ControllingUtils;
import de.jakob.lotm.entity.ModEntities;
import de.jakob.lotm.entity.custom.BeyonderNPCEntity;
import de.jakob.lotm.entity.custom.ability_entities.ControlBodyDouble;
import de.jakob.lotm.gui.custom.marionettes.MarionetteMenu;
import de.jakob.lotm.gui.custom.marionettes.MarionetteMenuProvider;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityBarHelper;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import de.jakob.lotm.util.shapeShifting.ShapeShiftingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.FlickerClonePacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class PositionFlickerAbility extends PositionAuthorityAbility {

    private static final String OWNER = "PositionFlickerOwner";
    private static final ResourceLocation ANCHOR = ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "flicker_anchor");
    private static final int WARN_INTERVAL = 60;
    private static final Map<UUID, Long> warned = new HashMap<>();
    private static final int BLINK_PERIOD = 20;
    private static final int BLINK_TICKS = 4;

    public PositionFlickerAbility(String id) {
        super(id, 60);

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(0, 20, 40));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(10000f, 20000f, 32000f));

        NeoForge.EVENT_BUS.addListener(PositionFlickerAbility::onDeath);
        NeoForge.EVENT_BUS.addListener(PositionFlickerAbility::onDrops);
        NeoForge.EVENT_BUS.addListener(PositionFlickerAbility::onExperience);
        NeoForge.EVENT_BUS.addListener(PositionFlickerAbility::onTarget);
        NeoForge.EVENT_BUS.addListener(PositionFlickerAbility::onTrack);
        NeoForge.EVENT_BUS.addListener(PositionFlickerAbility::onAttacked);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, PositionFlickerAbility::onJoin);
        NeoForge.EVENT_BUS.addListener(PositionFlickerAbility::onLeave);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || ownerOf(event.getEntity()) == null) return;
        event.setCanceled(false);
        if (event.getLevel() instanceof ServerLevel level) holdChunk(level, event.getEntity());
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && ownerOf(event.getEntity()) != null && event.getLevel() instanceof ServerLevel level) {
            releaseChunk(level, event.getEntity());
        }
    }

    @Override
    protected float getSpiritualityCost() {
        return 15000;
    }

    @Override
    protected String command() {
        return "flicker";
    }

    @Override
    protected boolean acceptsDestination() {
        return true;
    }

    @Override
    protected boolean acceptsHostile() {
        return true;
    }

    @Override
    public void useAbility(ServerLevel serverLevel, LivingEntity entity, boolean consumeSpirituality, boolean hasToHaveAbility, boolean hasToMeetRequirements, boolean isCopied) {
        if (!ordered(entity) && entity instanceof ServerPlayer player) {
            if (hasAbility(player, false)) manage(player);
            return;
        }
        super.useAbility(serverLevel, entity, consumeSpirituality, hasToHaveAbility, hasToMeetRequirements, isCopied);
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq) {
        apply(caster, target, seq, false);
    }

    @Override
    protected void apply(ServerPlayer caster, ServerPlayer target, int seq, boolean hostile) {
        spawn(caster, target.serverLevel(), target.position(), hostile);
    }

    @Override
    protected void applyAt(ServerPlayer caster, ServerPlayer target, ServerLevel level, Vec3 pos, int seq, boolean hostile) {
        spawn(caster, level, pos, hostile);
    }

    private static void spawn(ServerPlayer caster, ServerLevel level, Vec3 pos, boolean hostile) {
        BeyonderNPCEntity clone = new BeyonderNPCEntity(ModEntities.BEYONDER_NPC.get(), level, hostile, "",
                BeyonderData.getPathway(caster), BeyonderData.getSequence(caster), false, false);
        clone.setTargetPlayerUUID(caster.getUUID());
        clone.setQuestId("");
        clone.setShouldDrop(false);
        clone.getPersistentData().putBoolean("Initialized", true);
        clone.getPersistentData().putUUID(OWNER, caster.getUUID());
        clone.setCustomName(caster.getName());
        clone.setCustomNameVisible(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            clone.setItemSlot(slot, caster.getItemBySlot(slot).copy());
            clone.setDropChance(slot, 0f);
        }
        clone.moveTo(pos.x, pos.y, pos.z, caster.getYRot(), caster.getXRot());
        clone.setPersistenceRequired();
        ChunkPos chunk = new ChunkPos(BlockPos.containing(pos));
        level.getChunk(chunk.x, chunk.z);
        for (MobEffectInstance effect : caster.getActiveEffects()) clone.addEffect(new MobEffectInstance(effect));
        AllyUtil.makeAllies(caster, clone, false);
        for (BeyonderNPCEntity other : clones(caster)) AllyUtil.makeAllies(clone, other, false);
        if (!level.addFreshEntity(clone)) return;
        float fraction = caster.getMaxHealth() <= 0 ? 1f : caster.getHealth() / caster.getMaxHealth();
        ServerScheduler.scheduleDelayed(2, () -> {
            if (clone.isAlive()) clone.setHealth(clone.getMaxHealth() * fraction);
        }, level);

        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1, pos.z, 60, 0.4, 0.9, 0.4, 0.2);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.6f);
        AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_flicker.summoned", level.dimension().location().toString(),
                (int) pos.x, (int) pos.y, (int) pos.z).withColor(BeyonderData.pathwayInfos.get("door").color()));
    }

    private static final Map<String, Integer> heldChunks = new HashMap<>();
    private static final Map<UUID, ChunkPos> tickets = new HashMap<>();

    private static void holdChunk(ServerLevel level, Entity entity) {
        if (tickets.containsKey(entity.getUUID())) return;
        ChunkPos chunk = new ChunkPos(entity.blockPosition());
        tickets.put(entity.getUUID(), chunk);
        String key = level.dimension().location() + "|" + chunk.toLong();
        int count = heldChunks.getOrDefault(key, 0);
        if (count == 0) level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk);
        heldChunks.put(key, count + 1);
    }

    private static void releaseChunk(ServerLevel level, Entity entity) {
        ChunkPos chunk = tickets.remove(entity.getUUID());
        if (chunk == null) return;
        String key = level.dimension().location() + "|" + chunk.toLong();
        int count = heldChunks.getOrDefault(key, 0) - 1;
        if (count <= 0) {
            heldChunks.remove(key);
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk);
        } else {
            heldChunks.put(key, count);
        }
    }

    @Nullable
    public static UUID ownerOf(Entity entity) {
        if (!(entity instanceof BeyonderNPCEntity) || !entity.getPersistentData().hasUUID(OWNER)) return null;
        return entity.getPersistentData().getUUID(OWNER);
    }

    private static List<BeyonderNPCEntity> clones(ServerPlayer owner) {
        List<BeyonderNPCEntity> result = new ArrayList<>();
        for (ServerLevel level : owner.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof BeyonderNPCEntity npc && npc.isAlive() && owner.getUUID().equals(ownerOf(npc))) result.add(npc);
            }
        }
        return result;
    }

    @Nullable
    private static BeyonderNPCEntity find(ServerPlayer owner, UUID id) {
        for (ServerLevel level : owner.server.getAllLevels()) {
            if (level.getEntity(id) instanceof BeyonderNPCEntity npc && npc.isAlive() && owner.getUUID().equals(ownerOf(npc))) return npc;
        }
        return null;
    }

    private static void manage(ServerPlayer player) {
        List<MarionetteMenu.ServantRow> rows = new ArrayList<>();
        for (BeyonderNPCEntity clone : clones(player)) {
            String detail = Math.round(clone.getHealth()) + "/" + Math.round(clone.getMaxHealth()) + " HP  "
                    + clone.level().dimension().location() + "  "
                    + clone.blockPosition().getX() + " " + clone.blockPosition().getY() + " " + clone.blockPosition().getZ();
            rows.add(new MarionetteMenu.ServantRow(clone.getUUID(), clone.getName().getString(), detail, true,
                    BeyonderData.getPathway(clone), BeyonderData.getSequence(clone), anchored(clone)));
        }
        player.openMenu(MarionetteMenuProvider.flickers(), buf -> MarionetteMenu.writeFlickers(buf, rows));
    }

    public static void remove(ServerPlayer player, UUID id) {
        BeyonderNPCEntity clone = find(player, id);
        if (clone == null) return;
        if (clone.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.PORTAL, clone.getX(), clone.getY() + 1, clone.getZ(), 40, 0.4, 0.9, 0.4, 0.2);
        }
        AllyUtil.removeAllies(player, clone, false);
        clone.discard();
    }

    private static boolean anchored(LivingEntity clone) {
        AttributeInstance speed = clone.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(ANCHOR);
    }

    public static void anchor(ServerPlayer player, UUID id) {
        BeyonderNPCEntity clone = find(player, id);
        AttributeInstance speed = clone == null ? null : clone.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (speed.hasModifier(ANCHOR)) {
            speed.removeModifier(ANCHOR);
            return;
        }
        speed.addPermanentModifier(new AttributeModifier(ANCHOR, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        clone.getNavigation().stop();
    }

    public static void possess(ServerPlayer player, UUID id) {
        BeyonderNPCEntity clone = find(player, id);
        if (clone == null) return;
        if (ControllingUtils.isControlling(player)) ControllingUtils.cancel(player, 0, true, false);
        int color = BeyonderData.pathwayInfos.get("door").color();
        if (clone.level() != player.level()) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.position_flicker.other_dimension").withColor(color));
            return;
        }
        clone.getData(ModAttachments.ABILITY_WHEEL_COMPONENT).setAbilities(new ArrayList<>(player.getData(ModAttachments.ABILITY_WHEEL_COMPONENT).getAbilities()));
        clone.getData(ModAttachments.ABILITY_WHEEL_COMPONENT).setSelectedAbility(player.getData(ModAttachments.ABILITY_WHEEL_COMPONENT).getSelectedAbility());
        AbilityBarHelper.setAbilities(clone, new ArrayList<>(player.getData(ModAttachments.ABILITY_BAR_COMPONENT).getAbilities()));
        if (!ControllingUtils.startControlling(player, clone, true, true)) return;
        ShapeShiftingUtil.resetShape(player);
        ControlBodyDouble body = player.getData(ModAttachments.ENTITY_CONTROLLING_COMPONENT).getBodyDouble();
        if (body != null) {
            for (BeyonderNPCEntity other : clones(player)) AllyUtil.makeAllies(other, body, false);
        }
    }

    private static float backlash(int sequence) {
        if (sequence <= 0) return 0.05f;
        if (sequence == 1) return 0.25f;
        return 0.5f;
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        UUID ownerId = ownerOf(event.getEntity());
        if (ownerId == null) return;
        warned.remove(event.getEntity().getUUID());
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return;
        AllyUtil.removeAllies(owner, event.getEntity(), false);
        ServerScheduler.scheduleDelayed(1, () -> {
            if (!owner.isAlive()) return;
            owner.setHealth(owner.getHealth() - owner.getMaxHealth() * backlash(BeyonderData.getSequence(owner)));
        }, level);
    }

    public static void onDrops(LivingDropsEvent event) {
        if (ownerOf(event.getEntity()) != null) event.setCanceled(true);
    }

    public static void onExperience(LivingExperienceDropEvent event) {
        if (ownerOf(event.getEntity()) != null) event.setCanceled(true);
    }

    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof BeyonderNPCEntity clone) || ownerOf(clone) == null) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || AbilityUtil.mayTarget(clone, target)) return;
        Player other = clone.level().getNearestPlayer(clone.getX(), clone.getY(), clone.getZ(), clone.getAttributeValue(Attributes.FOLLOW_RANGE),
                entity -> entity instanceof Player player && !player.isSpectator() && !player.isCreative() && AbilityUtil.mayTarget(clone, player));
        event.setNewAboutToBeSetTarget(clone.isHostile() ? other : null);
    }

    private static void onAttacked(LivingDamageEvent.Post event) {
        LivingEntity clone = event.getEntity();
        if (!(clone.level() instanceof ServerLevel level)) return;
        UUID ownerId = ownerOf(clone);
        if (ownerId == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return;
        long now = level.getGameTime();
        Long last = warned.get(clone.getUUID());
        if (last != null && now - last < WARN_INTERVAL) return;
        warned.put(clone.getUUID(), now);
        Entity attacker = event.getSource().getEntity();
        Component who = attacker == null ? Component.translatable("ability.lotmcraft.position_flicker.someone") : attacker.getDisplayName();
        AbilityUtil.sendActionBar(owner, Component.translatable("ability.lotmcraft.position_flicker.attacked", who,
                level.dimension().location().toString(), clone.getBlockX(), clone.getBlockY(), clone.getBlockZ())
                .withColor(BeyonderData.pathwayInfos.get("door").color()));
    }

    private static void onTrack(PlayerEvent.StartTracking event) {
        UUID owner = ownerOf(event.getTarget());
        if (owner != null && event.getEntity() instanceof ServerPlayer player) {
            PacketHandler.sendToPlayer(player, new FlickerClonePacket(event.getTarget().getId(), owner));
        }
    }

    public static void mark(Entity entity, UUID owner) {
        entity.getPersistentData().putUUID(OWNER, owner);
    }

    public static boolean flickeredOut(Entity entity) {
        return ownerOf(entity) != null && entity.tickCount % BLINK_PERIOD < BLINK_TICKS;
    }

    @EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
    public static class Rendering {
        @SubscribeEvent
        public static void onRender(RenderLivingEvent.Pre<?, ?> event) {
            if (flickeredOut(event.getEntity())) event.setCanceled(true);
        }
    }
}
