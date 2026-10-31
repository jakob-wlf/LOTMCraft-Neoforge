package de.jakob.lotm.beyonders.abilities.error.handler;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

// Logic called from the Error pathway's mixins. Kept out of the mixin package and free of client-only imports.
public final class ErrorMixinHooks {

    private static final Random RANDOM = new Random();

    private ErrorMixinHooks() {
    }

    @Nullable
    private static CompoundTag getActiveLook(ItemStack stack) {
        if (stack.isEmpty() || ErrorClientFlags.localPlayer == null) return null;
        if (Thread.currentThread() != ErrorClientFlags.clientThread) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(DeceitHandler.ITEM_DECEIT_TAG)) return null;

        CompoundTag look = data.copyTag().getCompound(DeceitHandler.ITEM_DECEIT_TAG);
        if (!look.hasUUID("victim") || !ErrorClientFlags.isLocalPlayer(look.getUUID("victim"))) return null;
        if (look.getLong("until") < ErrorClientFlags.clientGameTime) return null;
        return look;
    }

    public static ItemStack getDisguisedStack(ItemStack stack) {
        CompoundTag look = getActiveLook(stack);
        if (look == null) return stack;
        ResourceLocation id = ResourceLocation.tryParse(look.getString("item"));
        if (id == null) return stack;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return stack;
        return new ItemStack(item, stack.getCount());
    }

    @Nullable
    public static Component getDisguisedName(ItemStack stack) {
        CompoundTag look = getActiveLook(stack);
        if (look == null) return null;
        String name = look.getString("name");
        if (!name.isEmpty()) return Component.literal(name);
        ItemStack disguise = getDisguisedStack(stack);
        return disguise == stack ? null : disguise.getItem().getName(disguise);
    }

    public static boolean shouldSwallowParticle() {
        long now = ErrorClientFlags.clientGameTime;
        if (ErrorClientFlags.abilityDeceiveUntil < now) return false;
        if (now - ErrorClientFlags.lastOwnAbilityUse > 40) return false;
        return RANDOM.nextFloat() < (ErrorClientFlags.abilityDeceiveMode == 0 ? 0.92f : 0.6f);
    }
}
