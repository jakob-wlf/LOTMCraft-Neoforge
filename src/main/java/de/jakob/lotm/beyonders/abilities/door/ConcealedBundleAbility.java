package de.jakob.lotm.beyonders.abilities.door;

import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.item.custom.ConcealedBundleItem;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.ParticleUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;

public class ConcealedBundleAbility extends Ability {

    private static final String BUNDLE_ID = "ConcealedBundleId";

    public ConcealedBundleAbility(String id) {
        super(id, 5);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeShared = false;

        dynamicSpirituality = new LinkedList<>(List.of(120000f, 24000f, 12000f, 8000f, 4000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 4));
    }

    @Override
    protected float getSpiritualityCost() {
        return 4000;
    }

    public static int capacity(int seq) {
        return 16 << (4 - Mth.clamp(seq, 0, 4));
    }

    public static int sequenceOf(int capacity) {
        for (int seq = 0; seq < 4; seq++) {
            if (capacity >= capacity(seq)) return seq;
        }
        return 4;
    }

    public static int maxOpens(int capacity) {
        if (capacity <= 16)  return 20;
        if (capacity <= 32)  return 40;
        if (capacity <= 64)  return 60;
        if (capacity <= 128) return 120;
        return 200;
    }

    private static CompoundTag persisted(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static int issue(LivingEntity entity) {
        CompoundTag data = persisted(entity);
        int id = data.getInt(BUNDLE_ID) + 1;
        data.putInt(BUNDLE_ID, id);
        return id;
    }

    public static boolean stale(ItemStack stack, MinecraftServer server) {
        UUID owner = stack.get(ModDataComponents.CONCEALED_BUNDLE_OWNER);
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        return player != null && stack.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_ID, 0) != persisted(player).getInt(BUNDLE_ID);
    }

    @Nullable
    private InteractionHand bundleHand(LivingEntity entity) {
        int capacity = capacity(AbilityUtil.getSeqWithArt(entity, this));
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            if (stack.is(Items.BUNDLE)) return hand;
            if (stack.is(ModItems.CONCEALED_BUNDLE.get()) && ConcealedBundleItem.isOwner(stack, entity)) return hand;
        }
        return null;
    }

    @Override
    public void useAbility(ServerLevel serverLevel, LivingEntity entity, boolean consumeSpirituality, boolean hasToHaveAbility, boolean hasToMeetRequirements, boolean isCopied) {
        if (bundleHand(entity) == null) {
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.concealed_bundle.no_bundle").withColor(BeyonderData.pathwayInfos.get("door").color()));
            return;
        }
        super.useAbility(serverLevel, entity, consumeSpirituality, hasToHaveAbility, hasToMeetRequirements, isCopied);
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        InteractionHand hand = bundleHand(entity);
        if (hand == null) return;

        int capacity = capacity(AbilityUtil.getSeqWithArt(entity, this));
        ItemStack held = entity.getItemInHand(hand);

        if (held.is(ModItems.CONCEALED_BUNDLE.get())) {
            held.set(ModDataComponents.CONCEALED_BUNDLE_SIZE, Math.max(capacity, held.getOrDefault(ModDataComponents.CONCEALED_BUNDLE_SIZE, 0)));
            held.set(ModDataComponents.CONCEALED_BUNDLE_OPENS, 0);
        } else {
            List<ItemStack> items = new ArrayList<>();
            List<ItemStack> overflow = new ArrayList<>();
            for (ItemStack stack : held.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemsCopy()) {
                (items.size() < capacity ? items : overflow).add(stack);
            }

            ItemStack bundle = new ItemStack(ModItems.CONCEALED_BUNDLE.get());
            bundle.set(ModDataComponents.CONCEALED_BUNDLE_OWNER, entity.getUUID());
            bundle.set(ModDataComponents.CONCEALED_BUNDLE_SIZE, capacity);
            bundle.set(ModDataComponents.CONCEALED_BUNDLE_ID, issue(entity));
            bundle.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            entity.setItemInHand(hand, bundle);

            for (ItemStack stack : overflow) {
                if (entity instanceof Player player) player.getInventory().placeItemBackInInventory(stack);
                else entity.spawnAtLocation(stack);
            }
        }

        serverLevel.playSound(null, entity.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1, 1.2f);
        ParticleUtil.spawnParticles(serverLevel, ParticleTypes.ENCHANT, entity.getEyePosition(), 40, .4, .4, .4, .5);
    }
}
