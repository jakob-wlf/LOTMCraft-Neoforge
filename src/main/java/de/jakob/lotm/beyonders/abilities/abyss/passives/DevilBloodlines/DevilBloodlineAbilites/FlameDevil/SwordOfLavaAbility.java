package de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbilites.FlameDevil;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.passives.DevilBloodlines.DevilBloodlineAbility;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.damage.ModDamageTypes;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.particle.ModParticles;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.DamageLookup;
import de.jakob.lotm.util.helper.ParticleUtil;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class SwordOfLavaAbility extends Ability {

    private static final int CONJURE_COST = 120;

    private static final int[] SWORD_DAMAGE = {7, 10, 13};

    // Projectile
    public static final int PROJECTILE_DURABILITY_COST = 12;
    public static final int PROJECTILE_COOLDOWN_TICKS = 20;
    private static final double PROJECTILE_SPEED = 1.4;
    private static final int PROJECTILE_MAX_TICKS = 38;
    private static final double PROJECTILE_HIT_RADIUS = 1.8;
    private static final double ARC_TILT = Math.toRadians(38);

    private static SwordOfLavaAbility instance;

    public SwordOfLavaAbility(String id) {
        super(id, 60f, "burning");
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        instance = this;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("abyss", 6));
    }
    @Override
    public float getSpiritualityCost() {
        return 120;
    }
    @Override
    public boolean meetsBloodlineRequirement(LivingEntity entity) {
        // Shadow clones never have a bloodline
        if (entity instanceof AvatarEntity) return false;
        return DevilBloodlineAbility.hasBloodline(entity, DevilBloodlineAbility.Bloodline.FLAME_DEVIL);
    }

    //Conjuring
    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        // Using the ability again dismisses the sword
        if (hasLavaSword(player)) {
            removeLavaSwords(player);
            player.displayClientMessage(Component.literal("The lava sword cools and fades.").withColor(0xE25822), true);
            return;
        }
        int slot = getEmptyHotbarSlot(player);
        if (slot < 0) {
            player.displayClientMessage(Component.literal("You need an empty slot in your hotbar.").withColor(0xE25822), true);
            return;
        }
        ItemStack sword = new ItemStack(ModItems.LAVA_SWORD.get());
        sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "lava_sword_damage"), getSwordDamage(BeyonderData.getSequence(player)), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
        player.getInventory().setItem(slot, sword);
        if (!player.hasInfiniteMaterials()) {BeyonderData.reduceSpirituality(player, CONJURE_COST);}
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.0f);
    }

    private static int getSwordDamage(int sequence) {return sequence <= 2 ? SWORD_DAMAGE[2] : sequence <= 4 ? SWORD_DAMAGE[1] : SWORD_DAMAGE[0];}
    private static int getEmptyHotbarSlot(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < 9; i++) if (inventory.getItem(i).isEmpty()) return i;
        return -1;
    }
    public static boolean hasLavaSword(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).getItem() instanceof LavaSwordItem) return true;
        return false;
    }
    private static void removeLavaSwords(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).getItem() instanceof LavaSwordItem) inventory.setItem(i, ItemStack.EMPTY);
    }
    //The lava sword projectile
    public static boolean fireLavaSword(ServerLevel level, Player owner) {
        if (instance == null) return false;
        instance.launchProjectile(level, owner);
        return true;
    }
    private void launchProjectile(ServerLevel level, Player owner) {
        Vec3 forward = owner.getLookAngle().normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = computeUp(forward, right);
        Vec3 sweepAxis = right.scale(Math.cos(ARC_TILT)).add(up.scale(Math.sin(ARC_TILT))).normalize();
        boolean griefing = BeyonderData.isGriefingEnabled(owner);
        double damage = DamageLookup.lookupDamage(6, 1.0) * multiplier(owner) * BloodlineFilthFlameSpiritAbility.getFireDamageMultiplier(owner);
        int seq = AbilityUtil.getSeqWithArt(owner, this);
        Vec3[] position = {owner.getEyePosition().add(0, -0.3, 0).add(forward.scale(0.8))};
        Set<UUID> alreadyHit = new HashSet<>();
        level.playSound(null, BlockPos.containing(position[0]), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.45f);
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(level, position[0], owner, this, interactionFlags, 3.0, 20));
        AtomicReference<UUID> taskRef = new AtomicReference<>();
        UUID taskId = ServerScheduler.scheduleForDuration(0, 1, PROJECTILE_MAX_TICKS, () -> {
            boolean finished = projectileTick(level, owner, position, forward, sweepAxis, damage, seq, griefing, alreadyHit);
            if (finished && taskRef.get() != null) {ServerScheduler.cancel(taskRef.get());}}, () -> {}, level);
        taskRef.set(taskId);
    }
    private boolean projectileTick(ServerLevel level, Player owner, Vec3[] position, Vec3 forward, Vec3 sweepAxis, double damage, int seq, boolean griefing, Set<UUID> alreadyHit) {
        Vec3 next = position[0].add(forward.scale(PROJECTILE_SPEED));
        BlockPos nextPos = BlockPos.containing(next);
        // Water puts it out
        if (level.getFluidState(nextPos).is(FluidTags.WATER)) {
            level.playSound(null, nextPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 2.0f, 0.6f);
            ParticleUtil.spawnParticles(level, ParticleTypes.LARGE_SMOKE, position[0], 16, .5, .05);
            return true;
        }
        // A solid block stops it
        if (!level.getBlockState(nextPos).getCollisionShape(level, nextPos).isEmpty()) {
            impact(level, position[0], griefing);
            return true;
        }
        position[0] = next;
        drawCrescent(level, next, sweepAxis, forward);
        AbilityUtil.getNearbyEntities(owner, level, next, PROJECTILE_HIT_RADIUS).stream().filter(t -> AbilityUtil.mayDamage(owner, t)).filter(t -> !alreadyHit.contains(t.getUUID())).forEach(t -> {
                    alreadyHit.add(t.getUUID());
                    if (AbilityUtil.isTargetSignificantlyStronger(seq, BeyonderData.getSequence(t))) return;
                    t.hurt(ModDamageTypes.source(level, ModDamageTypes.FIRE, owner), (float) damage);
                    if (griefing || !(t instanceof Player)) {t.setRemainingFireTicks(20 * 5);}
                    t.setDeltaMovement(t.getDeltaMovement().add(forward.scale(1.0).add(0, 0.3, 0)));
                    t.hurtMarked = true;
                    ParticleUtil.spawnParticles(level, ParticleTypes.LAVA, t.getEyePosition(), 18, 0.4, 0.07);
                    ParticleUtil.spawnParticles(level, ParticleTypes.FLAME, t.getEyePosition(), 10, 0.3, 0.08);
                });
        return false;
    }
    private void impact(ServerLevel level, Vec3 pos, boolean griefing) {
        level.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.75f);
        level.playSound(null, BlockPos.containing(pos), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 2.5f, 0.65f);
        ParticleUtil.spawnParticles(level, ParticleTypes.LAVA, pos, 20, 1.0, 0.1);
        ParticleUtil.spawnParticles(level, ParticleTypes.LARGE_SMOKE, pos, 14, 1.2, 0.05);
        if (griefing) igniteBlockAt(level, BlockPos.containing(pos));
    }
    private void drawCrescent(ServerLevel level, Vec3 center, Vec3 sweepAxis, Vec3 forward) {
        for (int k = -4; k <= 4; k++) {
            double lateral = k * 0.4;
            // The tips trail behind the middle, which gives the crescent shape
            Vec3 p = center.add(sweepAxis.scale(lateral)).add(forward.scale(-0.05 * lateral * lateral));
            level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0, 0.02, 0, 0.0);
            if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.05, 0.06, 0.05, 0.004);
            if (random.nextInt(6) == 0) level.sendParticles(ModParticles.BLACK_FLAME.get(), p.x, p.y, p.z, 1, 0.03, 0.05, 0.03, 0.002);
            if (random.nextInt(5) == 0) level.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.1, p.z, 1, 0.02, 0.05, 0.02, 0.003);
            if (random.nextInt(4) == 0) level.sendParticles(ParticleTypes.DRIPPING_LAVA, p.x, p.y, p.z, 1, 0, -0.02, 0, 0.0);
        }
    }

    private Vec3 computeUp(Vec3 forward, Vec3 right) {
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 up = worldUp.subtract(forward.scale(forward.dot(worldUp)));
        if (up.lengthSqr() < 1.0E-4) up = right.cross(forward);
        return up.normalize();
    }

    private void igniteBlockAt(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return;
        level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
    }
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer().level().isClientSide()) return;
        ItemStack tossed = event.getEntity().getItem();
        if (tossed.getItem() instanceof LavaSwordItem) {
            event.getPlayer().getInventory().add(tossed);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent
    public static void onDeathDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        event.getDrops().removeIf(itemEntity -> itemEntity.getItem().getItem() instanceof LavaSwordItem);
    }
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        removeLavaSwords(event.getEntity());
    }
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        removeLavaSwords(event.getEntity());
    }
}