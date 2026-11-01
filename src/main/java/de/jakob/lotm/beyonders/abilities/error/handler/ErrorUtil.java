package de.jakob.lotm.beyonders.abilities.error.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.MarionetteComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbility;
import de.jakob.lotm.beyonders.abilities.core.PassiveAbilityHandler;
import de.jakob.lotm.beyonders.abilities.fool.marionettes.ControllingUtils;
import de.jakob.lotm.beyonders.artifacts.SealedArtifactItem;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItem;
import de.jakob.lotm.beyonders.potions.BeyonderPotion;
import de.jakob.lotm.beyonders.potions.PotionRecipeItem;
import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.entity.custom.AvatarEntity;
import de.jakob.lotm.entity.custom.ability_entities.ControlBodyDouble;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.item.PotionIngredient;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorSyncPacket;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ErrorUtil {

    public static final int ERROR_COLOR = 0x4742c9;
    public static final int DECEIT_COLOR = 0x8a4fd6;
    public static final int FATE_COLOR = 0x6d32a8;
    public static final int TIME_COLOR = 0x3fb6c9;

    public static final String OWNER_UUID_TAG = "lotm_owner_uuid";
    public static final String OWNER_NAME_TAG = "lotm_owner_name";

    private ErrorUtil() {
    }


    public static int seqOf(LivingEntity entity) {
        return BeyonderData.isBeyonder(entity) ? BeyonderData.getSequence(entity) : LOTMCraft.NON_BEYONDER_SEQ;
    }

    public static boolean isErrorOfSequence(LivingEntity entity, int sequence) {
        return BeyonderData.isBeyonder(entity)
                && "error".equals(BeyonderData.getPathway(entity))
                && BeyonderData.getSequence(entity) <= sequence;
    }

    public static boolean hasPassive(LivingEntity entity, String id) {
        PassiveAbility passive = PassiveAbilityHandler.getById(id);
        return passive != null && passive.shouldApplyTo(entity);
    }

    public static boolean hasAbility(LivingEntity entity, String id) {
        Ability ability = LOTMCraft.abilityHandler.getById(id);
        return ability != null && ability.hasAbility(entity, true);
    }

    public static void send(ServerPlayer player, String key, CompoundTag data) {
        PacketHandler.sendToPlayer(player, new ErrorSyncPacket(key, data));
    }

    public static void send(LivingEntity entity, String key, CompoundTag data) {
        if (entity instanceof ServerPlayer player) {
            send(player, key, data);
        }
    }

    public static boolean isSpiritualItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof BeyonderCharacteristicItem
                || stack.getItem() instanceof BeyonderPotion
                || stack.getItem() instanceof PotionIngredient
                || stack.getItem() instanceof PotionRecipeItem
                || stack.getItem() instanceof SealedArtifactItem) {
            return true;
        }

        if (stack.has(ModDataComponents.SEALED_ARTIFACT_DATA) || stack.has(ModDataComponents.ABILITY_USES)) {
            return true;
        }

        return stack.is(ModItems.SUN_ITEM.get()) || stack.is(ModItems.MOON_ITEM.get()) || stack.is(ModItems.EXCAVATED_AREA_ITEM.get());
    }

    public static int getItemValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        int perItem;
        if (stack.getItem() instanceof BeyonderCharacteristicItem characteristic) {
            perItem = 400 + (9 - characteristic.getSequence()) * 350;
        } else if (stack.getItem() instanceof SealedArtifactItem || stack.has(ModDataComponents.SEALED_ARTIFACT_DATA)) {
            perItem = 600;
        } else if (stack.getItem() instanceof BeyonderPotion potion) {
            perItem = 300 + (9 - potion.getSequence()) * 250;
        } else if (stack.getItem() instanceof PotionIngredient ingredient) {
            perItem = 60 + (9 - ingredient.getSequence()) * 40;
        } else if (stack.getItem() instanceof PotionRecipeItem) {
            perItem = 120;
        } else if (stack.is(Items.NETHER_STAR) || stack.is(Items.ELYTRA) || stack.is(Items.TOTEM_OF_UNDYING)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.BEACON) || stack.is(Items.DRAGON_EGG)) {
            perItem = 500;
        } else if (stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_BLOCK) || stack.is(Items.NETHERITE_SCRAP)
                || stack.is(Items.ANCIENT_DEBRIS)) {
            perItem = 200;
        } else if (stack.is(Items.DIAMOND) || stack.is(Items.DIAMOND_BLOCK) || stack.is(Items.DIAMOND_ORE)
                || stack.is(Items.DEEPSLATE_DIAMOND_ORE)) {
            perItem = stack.is(Items.DIAMOND_BLOCK) ? 400 : 45;
        } else if (stack.is(Items.EMERALD) || stack.is(Items.EMERALD_BLOCK)) {
            perItem = stack.is(Items.EMERALD_BLOCK) ? 180 : 20;
        } else if (stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_BLOCK) || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.RAW_GOLD) || stack.is(Items.GOLD_NUGGET)) {
            perItem = stack.is(Items.GOLD_BLOCK) ? 90 : (stack.is(Items.GOLD_NUGGET) ? 1 : 10);
        } else if (stack.is(ItemTags.TRIM_TEMPLATES) || stack.is(Items.ENCHANTED_BOOK)) {
            perItem = 40;
        } else {
            perItem = switch (stack.getRarity()) {
                case EPIC -> 150;
                case RARE -> 60;
                case UNCOMMON -> 15;
                default -> 0;
            };
        }

        if (stack.isEnchanted()) {
            perItem += 30 + 15 * stack.getEnchantments().size();
        }

        if (stack.isDamageableItem() && stack.getMaxDamage() > 1000) {
            perItem += 60;
        }

        return perItem * stack.getCount();
    }

    public static boolean isValuable(ItemStack stack) {
        return isSpiritualItem(stack) || getItemValue(stack) >= 40 || stack.getRarity() == Rarity.EPIC;
    }

    public static List<ItemStack> getCarriedItems(LivingEntity entity) {
        List<ItemStack> items = new ArrayList<>();
        if (entity instanceof Player player) {
            for (ItemStack stack : player.getInventory().items) if (!stack.isEmpty()) items.add(stack);
            for (ItemStack stack : player.getInventory().armor) if (!stack.isEmpty()) items.add(stack);
            for (ItemStack stack : player.getInventory().offhand) if (!stack.isEmpty()) items.add(stack);
        } else {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty()) items.add(stack);
            }
        }
        return items;
    }

    public static int getCarriedValue(LivingEntity entity) {
        int value = 0;
        for (ItemStack stack : getCarriedItems(entity)) value += getItemValue(stack);
        return value;
    }

    public static boolean carriesValuables(LivingEntity entity) {
        for (ItemStack stack : getCarriedItems(entity)) {
            if (isValuable(stack)) return true;
        }
        return false;
    }

    public static void markOwner(ItemStack stack, LivingEntity owner) {
        if (stack.isEmpty() || owner == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(OWNER_UUID_TAG, owner.getUUID());
            tag.putString(OWNER_NAME_TAG, owner.getName().getString());
        });
    }

    @Nullable
    public static UUID getMarkedOwner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(OWNER_UUID_TAG) ? tag.getUUID(OWNER_UUID_TAG) : null;
    }

    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Nullable
    public static LivingEntity getMainBody(ServerLevel level, LivingEntity entity) {
        if (entity instanceof AvatarEntity avatar && avatar.getOriginalOwner() != null) {
            return findLiving(level, avatar.getOriginalOwner());
        }

        MarionetteComponent marionette = entity.getData(ModAttachments.MARIONETTE_COMPONENT);
        if (marionette.isMarionette() && marionette.getControllerUUID() != null && !marionette.getControllerUUID().isEmpty()) {
            try {
                return findLiving(level, UUID.fromString(marionette.getControllerUUID()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (entity.getPersistentData().getBoolean("VoidSummoned") && entity.getPersistentData().hasUUID("VoidSummonOwner")) {
            return findLiving(level, entity.getPersistentData().getUUID("VoidSummonOwner"));
        }

        if (entity instanceof Player player && ControllingUtils.isControlling(player)) {
            ControlBodyDouble bodyDouble = findBodyDouble(level, player.getUUID());
            if (bodyDouble != null) return bodyDouble;
        }

        return null;
    }

    public static boolean isTrueBody(ServerLevel level, LivingEntity entity) {
        return getMainBody(level, entity) == null;
    }

    public static List<LivingEntity> getSecondBodies(ServerLevel level, LivingEntity owner) {
        List<LivingEntity> bodies = new ArrayList<>();
        AABB area = owner.getBoundingBox().inflate(256);
        String ownerId = owner.getUUID().toString();

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != owner && e.isAlive())) {
            if (candidate instanceof AvatarEntity avatar && owner.getUUID().equals(avatar.getOriginalOwner())) {
                bodies.add(candidate);
                continue;
            }
            if (candidate instanceof ControlBodyDouble bodyDouble && owner.getUUID().equals(bodyDouble.getOwnerUUID())) {
                bodies.add(candidate);
                continue;
            }
            MarionetteComponent marionette = candidate.getData(ModAttachments.MARIONETTE_COMPONENT);
            if (marionette.isMarionette() && ownerId.equals(marionette.getControllerUUID())) {
                bodies.add(candidate);
                continue;
            }
            if (candidate.getPersistentData().getBoolean("VoidSummoned")
                    && candidate.getPersistentData().hasUUID("VoidSummonOwner")
                    && owner.getUUID().equals(candidate.getPersistentData().getUUID("VoidSummonOwner"))) {
                bodies.add(candidate);
            }
        }
        return bodies;
    }

    @Nullable
    public static ControlBodyDouble findBodyDouble(ServerLevel level, UUID owner) {
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof ControlBodyDouble bodyDouble && owner.equals(bodyDouble.getOwnerUUID())) {
                    return bodyDouble;
                }
            }
        }
        return null;
    }

    @Nullable
    public static LivingEntity findLiving(ServerLevel level, UUID uuid) {
        if (uuid == null) return null;
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    public static String describeEntity(LivingEntity entity) {
        return entity.getName().getString();
    }
}
