package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.CopiedAbilityComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.error.handler.ConceptStorage;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.TheftHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.TimeHandler;
import de.jakob.lotm.beyonders.abilities.twilight_giant.handlers.TwilightAging;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.AbilitySelectionPacket;
import de.jakob.lotm.rendering.effectRendering.EffectIds;
import de.jakob.lotm.rendering.effectRendering.EffectManager;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.AllyUtil;
import de.jakob.lotm.util.helper.CopiedAbilityHelper;
import de.jakob.lotm.util.scheduling.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.*;

public class GiftAbility extends SelectableAbility {

    private static final Map<UUID, Long> openMenus = new java.util.concurrent.ConcurrentHashMap<>();

    public GiftAbility(String id) {
        super(id, 1f);
        canBeUsedByNPC = false;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(1, 1, 1, 2, 2, 3, 3));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(4000f, 1600f, 1000f, 670f, 650f, 430f, 400f));

    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 6));
    }

    @Override
    public float getSpiritualityCost() {
        return 60;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.gift_ability.item",
                "ability.lotmcraft.gift_ability.menu",
                "ability.lotmcraft.gift_ability.health",
                "ability.lotmcraft.gift_ability.digestion",
                "ability.lotmcraft.gift_ability.luck",
                "ability.lotmcraft.gift_ability.age"
        };
    }

    @Override
    public String[] getSequenceDependantAbilityNames(int sequence) {
        String[] all = getAllAbilityNames();
        if (sequence >= 6) return Arrays.copyOf(all, 2);
        if (sequence >= 2) return Arrays.copyOf(all, 3);
        return all;
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
        switch(selectedAbility){
            case 0 -> giftItem(level, entity);
            case 1 -> openGiftingMenu(level, entity);
            case 2 -> giftHealth(level, entity);
            case 3 -> giftDigestion(level, entity);
            case 4 -> giftLuck(level, entity);
            case 5 -> giftAge(level, entity);
        }
    }

    private void giftAge(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (BeyonderData.getSequence(entity) > 0) return;
        LivingEntity target = AbilityUtil.getTargetEntity(entity, baseDistance, 2, true, true);
        if (target == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.no_target").withColor(0x6d32a8));
            return;
        }
        float targetYears = TwilightAging.years(target);
        if (targetYears < 10f || !TwilightAging.setYears(entity, TwilightAging.years(entity) + 10f)) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.failed").withColor(0x6d32a8));
            return;
        }
        TwilightAging.setYears(target, targetYears - 10f);
        EffectManager.playEffect(EffectIds.GIFTING_PARTICLES, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), player, entity);
    }

    private void openGiftingMenu(Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            if (entity instanceof Player player && level.isClientSide) {
                player.playSound(SoundEvents.BELL_RESONATE, 1, 1);
            }
            return;
        }

        CompoundTag data = new CompoundTag();

        ListTag concepts = new ListTag();
        for (Map.Entry<ConceptStorage.Concept, Integer> entry : ConceptStorage.getAll(player).entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", entry.getKey().id);
            tag.putInt("amount", entry.getValue());
            tag.putString("unit", entry.getKey().unit);
            concepts.add(tag);
        }
        data.put("concepts", concepts);

        ListTag effects = new ListTag();
        List<MobEffectInstance> storedEffects = ConceptStorage.getEffects(player);
        for (int i = 0; i < storedEffects.size(); i++) {
            MobEffectInstance effect = storedEffects.get(i);
            CompoundTag tag = new CompoundTag();
            tag.putInt("index", i);
            tag.putString("name", effect.getEffect().value().getDisplayName().getString());
            tag.putInt("amplifier", effect.getAmplifier());
            tag.putInt("seconds", effect.getDuration() / 20);
            effects.add(tag);
        }
        data.put("effects", effects);

        ListTag abilities = new ListTag();
        CopiedAbilityComponent copied = player.getData(ModAttachments.COPIED_ABILITY_COMPONENT);
        for (int i = 0; i < copied.size(); i++) {
            CopiedAbilityComponent.CopiedAbilityData ability = copied.getAbility(i);
            if (ability == null || !"stolen".equals(ability.copyType())) continue;
            CompoundTag tag = new CompoundTag();
            tag.putInt("index", i);
            tag.putString("id", ability.abilityId());
            tag.putInt("uses", ability.remainingUses());
            abilities.add(tag);
        }
        data.put("abilities", abilities);

        openMenus.put(player.getUUID(), player.serverLevel().getGameTime() + 20 * 60);
        ErrorUtil.send(player, "gift_menu", data);
    }

    private int getGiftDistance(LivingEntity entity) {
        int seq = AbilityUtil.getSeqWithArt(entity, this);
        return (int) (Math.clamp(1L << (9 - seq), 10, 200) * multiplier(entity));
    }

    public void giftFromMenu(ServerPlayer player, String kind, String id, int index) {
        ServerLevel level = player.serverLevel();
        Long until = openMenus.remove(player.getUUID());
        if (until == null || until < level.getGameTime()) return;

        LivingEntity target = AbilityUtil.getTargetEntity(player, getGiftDistance(player), 2, true, true);

        if (target == null) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.gift.no_target").withColor(0x6d32a8));
            return;
        }

        boolean success = switch (kind) {
            case "concept" -> giftConcept(level, player, target, ConceptStorage.Concept.byId(id));
            case "effect" -> giftEffect(player, target, index);
            case "ability" -> giftStolenAbility(player, target, index);
            default -> false;
        };

        if (!success) {
            AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.gift.failed").withColor(0x6d32a8));
            return;
        }

        EffectManager.playEffect(EffectIds.GIFTING_PARTICLES, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), player, player);
        AbilityUtil.sendActionBar(player, Component.translatable("ability.lotmcraft.gift.success", target.getName()).withColor(0x6d32a8));
    }

    private boolean giftConcept(ServerLevel level, ServerPlayer player, LivingEntity target, ConceptStorage.Concept concept) {
        if (concept == null) return false;
        boolean friendly = AllyUtil.areAllies(player, target);

        switch (concept) {
            case DISTANCE -> {
                int distance = (int) TheftHandler.getDistancePerSeq(AbilityUtil.getSeqWithArt(player, this));
                int taken = ConceptStorage.take(player, concept, distance);
                if (taken <= 0) return false;
                pushAlongLook(level, player, target, taken);
            }
            case MOVEMENT -> {
                int seconds = ConceptStorage.take(player, concept, 30);
                if (seconds <= 0) return false;
                if (friendly) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, seconds * 20, 2, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.JUMP, seconds * 20, 1, false, true, true));
                } else {
                    Random random = new Random();
                    ServerScheduler.scheduleForDuration(0, 10, seconds * 20, () -> {
                        if (!target.isAlive()) return;
                        Vec3 push = new Vec3(random.nextDouble() - 0.5, 0.15, random.nextDouble() - 0.5).normalize().scale(0.7);
                        target.setDeltaMovement(push);
                        target.hurtMarked = true;
                    }, level);
                }
            }
            case SIGHT -> {
                int seconds = ConceptStorage.take(player, concept, 30);
                if (seconds <= 0) return false;
                if (friendly) {
                    target.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, seconds * 20, 0, false, true, true));
                    AbilityUtil.getNearbyEntities(target, level, target.position(), 24).forEach(e -> {
                        if (!AllyUtil.areAllies(target, e)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, seconds * 20, 0, false, false, false));
                    });
                } else {
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, seconds * 20, 0, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, seconds * 20, 0, false, true, true));
                }
            }
            case THOUGHTS -> {
                int seconds = ConceptStorage.take(player, concept, 20);
                if (seconds <= 0) return false;
                if (friendly) {
                    target.getData(ModAttachments.COOLDOWN_COMPONENT).addToAll(-seconds * 20);
                } else {
                    AbilityUtil.sealAllTargetAbilities(player, target, "gifted_thoughts", seconds * 20);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20, 2, false, true, true));
                    if (target instanceof Mob mob) mob.setTarget(null);
                }
            }
            case FIRE -> {
                int seconds = ConceptStorage.take(player, concept, 60);
                if (seconds <= 0) return false;
                target.setRemainingFireTicks(target.getRemainingFireTicks() + seconds * 20);
            }
            case FROST -> {
                int seconds = ConceptStorage.take(player, concept, 60);
                if (seconds <= 0) return false;
                target.setTicksFrozen(target.getTicksRequiredToFreeze() + seconds * 20);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20, 3, false, true, true));
            }
            case TIME -> {
                int seconds = ConceptStorage.take(player, concept, 60);
                if (seconds <= 0) return false;
                boolean restored = TimeHandler.giftTime(target, seconds * 20);
                TimeHandler.notifyGift(player, target, restored);
            }
            case VITALITY -> {
                int health = ConceptStorage.take(player, concept, (int) target.getMaxHealth());
                if (health <= 0) return false;
                target.heal(health);
            }
        }
        return true;
    }

    private boolean giftEffect(ServerPlayer player, LivingEntity target, int index) {
        MobEffectInstance effect = ConceptStorage.takeEffect(player, index);
        if (effect == null) return false;
        target.addEffect(new MobEffectInstance(effect));
        return true;
    }

    private boolean giftStolenAbility(ServerPlayer player, LivingEntity target, int index) {
        CopiedAbilityComponent copied = player.getData(ModAttachments.COPIED_ABILITY_COMPONENT);
        CopiedAbilityComponent.CopiedAbilityData data = copied.getAbility(index);
        if (data == null || !"stolen".equals(data.copyType())) return false;

        if (target instanceof ServerPlayer targetPlayer) {
            CopiedAbilityHelper.addAbility(targetPlayer, new CopiedAbilityComponent.CopiedAbilityData(
                    data.abilityId(), "gifted", data.remainingUses(), player.getUUID().toString()));
        } else {
            Ability ability = LOTMCraft.abilityHandler.getById(data.abilityId());
            if (ability == null || !(target.level() instanceof ServerLevel serverLevel)) return false;
            ability.useAbility(serverLevel, target, false, false, false, true);
        }
        CopiedAbilityHelper.removeAbilityIndex(player, index);
        return true;
    }

    private void pushAlongLook(Level level, LivingEntity entity, LivingEntity target, int distance) {
        Vec3 eyePos = entity.getEyePosition();
        Vec3 lookVec = entity.getLookAngle();
        Vec3 reach = eyePos.add(lookVec.scale(distance));

        BlockHitResult hitResult = level.clip(new ClipContext(eyePos, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));

        Vec3 finalPos = hitResult.getType() != HitResult.Type.MISS
                ? hitResult.getLocation().subtract(lookVec.scale(0.5))
                : reach;

        target.teleportTo(finalPos.x, finalPos.y, finalPos.z);
    }

    private void giftLuck(Level level, LivingEntity entity){
        if(!(entity instanceof ServerPlayer player)) {
            if(entity instanceof Player player && entity.level().isClientSide) {
                player.playSound(SoundEvents.BELL_RESONATE, 1, 1);
            }
            return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(entity, baseDistance, 2, true, true);

        if(target == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.no_target").withColor(0x6d32a8));
            return;
        }

        var luck = entity.getData(ModAttachments.LUCK_COMPONENT.get());
        if(luck.getLuck() == 0){
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.failed").withColor(0x6d32a8));
            return;
        }

        EffectManager.playEffect(EffectIds.GIFTING_PARTICLES, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), player, entity);

        var targetLuck = target.getData(ModAttachments.LUCK_COMPONENT.get());
        AbilityUtil.setTargetLuck(entity, target,targetLuck.getLuck() + luck.getLuck());
        AbilityUtil.setTargetLuck(entity, entity, 0);
    }

    private void giftDigestion(Level level, LivingEntity entity){
        if(!(entity instanceof ServerPlayer player)) {
            if(entity instanceof Player player && entity.level().isClientSide) {
                player.playSound(SoundEvents.BELL_RESONATE, 1, 1);
            }
            return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(entity, baseDistance, 2, true, true);

        if(target == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.no_target").withColor(0x6d32a8));
            return;
        }

        if(!BeyonderData.isBeyonder(target) || !(target instanceof ServerPlayer targetPlayer)){
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.failed").withColor(0x6d32a8));
            return;
        }

        float targetDigestion = BeyonderData.getDigestionProgress(targetPlayer);
        if(targetDigestion == 1.0f){
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.failed").withColor(0x6d32a8));
            return;
        }

        if(BeyonderData.getDigestionProgress(player) <= 0.0f){
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.failed").withColor(0x6d32a8));
            return;
        }

        EffectManager.playEffect(EffectIds.GIFTING_PARTICLES, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), player, entity);

        float base = 0.25f;
        float actual = BeyonderData.getDigestionProgress(player) - base < 0 ? (base + (BeyonderData.getDigestionProgress(player) - base)) : base;

        BeyonderData.digest(targetPlayer, actual, false);
        BeyonderData.digest(player, -actual, false);
    }

    private void giftHealth(Level level, LivingEntity entity){
        if(!(entity instanceof ServerPlayer player)) {
            if(entity instanceof Player player && entity.level().isClientSide) {
                player.playSound(SoundEvents.BELL_RESONATE, 1, 1);
            }
            return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(entity, baseDistance, 2, true, true);

        if(target == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.no_target").withColor(0x6d32a8));
            return;
        }

        float maxHealth = entity.getMaxHealth();
        float healthToGift = maxHealth/4;
        float currentHealth = entity.getHealth();

        if(currentHealth - healthToGift <= maxHealth/2){
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.failed").withColor(0x6d32a8));
            return;
        }

        EffectManager.playEffect(EffectIds.GIFTING_PARTICLES, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), player, entity);

        target.setHealth(target.getHealth() + healthToGift);
        entity.setHealth(currentHealth - healthToGift);
    }

    private void giftItem(Level level, LivingEntity entity){
        if(!(entity instanceof ServerPlayer player)) {
            if(entity instanceof Player player && entity.level().isClientSide) {
                player.playSound(SoundEvents.BELL_RESONATE, 1, 1);
            }
            return;
        }

        ItemStack offHandItem = player.getItemInHand(InteractionHand.OFF_HAND);

        if(offHandItem.isEmpty()) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.no_item").withColor(0x6d32a8));
            return;
        }

        LivingEntity target = AbilityUtil.getTargetEntity(entity, baseDistance, 2, true, true);

        if(target == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.gift.no_target").withColor(0x6d32a8));
            return;
        }

        EffectManager.playEffect(EffectIds.GIFTING_PARTICLES, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), player, entity);

        if(!isItemWithEffect(offHandItem)) {
            var capability = target.getCapability(Capabilities.ItemHandler.ENTITY);

            if(capability != null && hasInventory(target)) {
                ItemStack toInsert = offHandItem.copy();

                for(int i = 0; i < capability.getSlots(); i++) {
                    toInsert = capability.insertItem(i, toInsert, false);
                    if(toInsert.isEmpty()) {
                        break; // Successfully inserted everything
                    }
                }

                if(!toInsert.isEmpty()) {
                    target.spawnAtLocation(toInsert);
                }
            } else {
                target.spawnAtLocation(offHandItem.copy());
            }
        }
        else {
            handleGiftEffect(level, entity, target, offHandItem);
        }

        offHandItem.setCount(0);
    }

    private void handleGiftEffect(Level level, LivingEntity entity, LivingEntity target, ItemStack offHandItem) {
        if(offHandItem.is(Items.TNT)) {
            Random rand = new Random();
            for(int i = 0; i < Math.min(offHandItem.getCount(), 30); i++) {
                PrimedTnt tnt = new PrimedTnt(level, target.getX() + rand.nextDouble(-1, 1), target.getY() + rand.nextDouble(-1, 1), target.getZ() + rand.nextDouble(-1, 1), entity);
                tnt.setFuse(10);
                level.addFreshEntity(tnt);
            }
        }
        else if(offHandItem.is(Items.ANVIL)) {
            for(int i = 0; i < offHandItem.getCount(); i++) {
                FallingBlockEntity anvil = FallingBlockEntity.fall(level, BlockPos.containing(target.getX(), target.getY() + 10 + i, target.getZ()), Blocks.ANVIL.defaultBlockState());
                anvil.disableDrop();
                anvil.setHurtsEntities(3, 20);
            }
        }
        else if(offHandItem.is(Items.ENDER_PEARL)) {
            target.teleportTo(entity.getX(), entity.getY(), entity.getZ());
        }
        else if(offHandItem.is(Items.FIRE_CHARGE)) {
            target.setRemainingFireTicks(target.getRemainingFireTicks() + 20 * offHandItem.getCount());
        }
        else if(offHandItem.is(Items.LAVA_BUCKET)) {
            target.setRemainingFireTicks(target.getRemainingFireTicks() + 30 * offHandItem.getCount());
        }
        else if(offHandItem.is(Items.WATER_BUCKET)) {
            for(int i = 0; i < offHandItem.getCount(); i++) {
                target.clearFire();
            }
        }
    }

    public boolean hasInventory(Entity entity) {
        return entity.getCapability(Capabilities.ItemHandler.ENTITY) != null;
    }

    private final Item[] itemsWithEffects = new Item[]{
            Items.TNT,
            Items.ANVIL,
            Items.ENDER_PEARL,
            Items.FIRE_CHARGE,
            Items.LAVA_BUCKET,
            Items.WATER_BUCKET,

    };

    private boolean isItemWithEffect(ItemStack offHandItem) {
        for (Item item : itemsWithEffects) {
            if (offHandItem.is(item)) {
                return true;
            }
        }
        return false;
    }
}
