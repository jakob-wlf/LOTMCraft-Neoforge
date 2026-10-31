package de.jakob.lotm.rendering.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.AllyComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorClientFlags;
import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.gui.custom.error.*;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import de.jakob.lotm.util.ClientBeyonderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Vector3f;

import java.util.*;

// Client side counterpart of ErrorSyncPacket

@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public final class ErrorClientHandler {

    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1f, 0.82f, 0.25f), 0.6f);

    private static boolean wasTyping = false;
    private static int typingRefresh = 0;

    private ErrorClientHandler() {
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    private static ItemStack readItem(CompoundTag tag, String key) {
        HolderLookup.Provider provider = registries();
        if (provider == null || !tag.contains(key)) return ItemStack.EMPTY;
        return ItemStack.parseOptional(provider, tag.getCompound(key));
    }

    public static void handle(String key, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = now();

        switch (key) {
            case "valuables" -> {
                List<ErrorClientState.ValuableHint> hints = new ArrayList<>();
                ListTag list = data.getList("hints", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag tag = list.getCompound(i);
                    hints.add(new ErrorClientState.ValuableHint(new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")), tag.getFloat("s")));
                }
                ErrorClientState.valuables = hints;
                ErrorClientState.valuablesReceived = now;
            }
            case "sanity_reveal" -> ErrorClientState.sanityReveals.put(data.getInt("id"),
                    new ErrorClientState.SanityReveal(data.getFloat("sanity"), now + 20 * 8));
            case "connections" -> {
                List<ErrorClientState.Link> links = new ArrayList<>();
                ListTag list = data.getList("links", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag tag = list.getCompound(i);
                    links.add(new ErrorClientState.Link(tag.getInt("a"), tag.getInt("b"), tag.getString("t")));
                }
                ErrorClientState.links = links;
                ErrorClientState.linksReceived = now;
            }
            case "threats" -> {
                List<ErrorClientState.Threat> threats = new ArrayList<>();
                ListTag list = data.getList("threats", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag tag = list.getCompound(i);
                    threats.add(new ErrorClientState.Threat(tag.getString("p"), tag.getFloat("d")));
                }
                ErrorClientState.threats = threats;
                ErrorClientState.threatsReceived = now;
            }
            case "observation_reset" -> {
                ErrorClientState.valuables = new ArrayList<>();
                ErrorClientState.links = new ArrayList<>();
                ErrorClientState.threats = new ArrayList<>();
            }
            case "decryption_info" -> {
                ErrorClientState.decryptionTarget = data.getInt("id");
                ErrorClientState.decryptionLevel = data.getInt("level");
                ErrorClientState.decryptionFraction = data.getFloat("fraction");
                List<String> lines = new ArrayList<>();
                ListTag list = data.getList("lines", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) lines.add(list.getString(i));
                ErrorClientState.decryptionLines = lines;
                ErrorClientState.decryptionReceived = now;
            }
            case "reveal" -> ErrorClientState.revealed.put(data.getInt("id"), now + data.getInt("ticks"));
            case "theft_mark" -> {
                ErrorClientState.theftMark = data.getInt("id");
                ErrorClientState.theftMarkReceived = now;
            }
            case "treasure" -> {
                ErrorClientState.treasureTarget = data.getInt("id");
                ErrorClientState.treasureReceived = now;
                ErrorClientState.treasureName = data.getString("name");
                ErrorClientState.treasureValue = data.getInt("value");
                List<ItemStack> items = new ArrayList<>();
                List<Integer> values = new ArrayList<>();
                ListTag list = data.getList("items", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    ItemStack stack = readItem(list.getCompound(i), "item");
                    if (stack.isEmpty()) continue;
                    items.add(stack);
                    values.add(list.getCompound(i).getInt("value"));
                }
                ErrorClientState.treasureItems = items;
                ErrorClientState.treasureItemValues = values;
            }
            case "thorough_open" -> mc.setScreen(ThoroughTheftScreen.fromTag(data, registries()));
            case "thorough_update" -> {
                if (mc.screen instanceof ThoroughTheftScreen screen) screen.onUpdate(data, registries());
            }
            case "thorough_close" -> {
                if (mc.screen instanceof ThoroughTheftScreen) mc.setScreen(null);
            }
            case "gift_menu" -> mc.setScreen(new GiftConceptScreen(data));
            case "deceit_prepare" -> mc.setScreen(new DeceitPrepareScreen(data.getCompound("config")));
            case "deceit_wheel" -> mc.setScreen(new DeceitWheelScreen(data.getCompound("config"), data.getBoolean("area")));
            case "trap_setup" -> mc.setScreen(new TrapSetupScreen(data));
            case "self_characteristic" -> mc.setScreen(new SelfCharacteristicScreen(data));
            case "deceit" -> handleDeceit(mc, data, now);
            default -> {
            }
        }
    }

    private static void handleDeceit(Minecraft mc, CompoundTag data, long now) {
        long until = now + data.getInt("ticks");
        switch (data.getString("type")) {
            case "sanity" -> {
                ErrorClientState.fakeSanity = data.getFloat("value");
                ErrorClientState.fakeSanityUntil = until;
            }
            case "health" -> {
                ErrorClientState.fakeHealth = data.getFloat("value");
                ErrorClientState.fakeHealthUntil = until;
                ErrorClientState.fakeDeathShown = false;
            }
            case "spirituality" -> {
                ErrorClientState.spiritMode = data.getInt("mode");
                ErrorClientState.spiritValue = data.getFloat("value");
                ErrorClientState.spiritUntil = until;
            }
            case "hunger" -> {
                ErrorClientState.hungerMode = data.getInt("mode");
                ErrorClientState.hungerUntil = until;
                ErrorClientState.hungerStart = now;
            }
            case "inventory" -> {
                ItemStack[] items = new ItemStack[36];
                Arrays.fill(items, ItemStack.EMPTY);
                ListTag list = data.getList("items", Tag.TAG_COMPOUND);
                HolderLookup.Provider provider = registries();
                for (int i = 0; i < Math.min(36, list.size()); i++) {
                    if (provider != null) items[i] = ItemStack.parseOptional(provider, list.getCompound(i));
                }
                ErrorClientState.fakeInventory = items;
                ErrorClientState.fakeInventoryUntil = until;
            }
            case "ally" -> {
                UUID caster = data.getUUID("caster");
                boolean wasReal = ErrorClientState.fakeAllies.stream().noneMatch(ally -> ally.uuid().equals(caster))
                        && mc.player.getData(ModAttachments.ALLY_COMPONENT.get()).isAlly(caster);
                ErrorClientState.fakeAllies.removeIf(ally -> ally.uuid().equals(caster));
                ErrorClientState.fakeAllies.add(new ErrorClientState.FakeAlly(caster, data.getString("name"), until, wasReal));
                refreshAllies(mc);
            }
            case "ability" -> {
                ErrorClientFlags.abilityDeceiveMode = data.getInt("mode");
                ErrorClientFlags.abilityDeceiveUntil = until;
            }
            default -> {
            }
        }
    }

    // ==================== ALLY DECEIT ====================

    /**
     * Adds the deceiving Mentor of Deceit to the ally list the client displays.
     */
    public static AllyComponent withFakeAllies(AllyComponent component) {
        long now = now();
        AllyComponent result = component;
        for (ErrorClientState.FakeAlly ally : ErrorClientState.fakeAllies) {
            if (ally.until() > now && !result.isAlly(ally.uuid())) {
                result = result.addAlly(ally.uuid(), ally.name(), true);
            }
        }
        return result;
    }

    private static void refreshAllies(Minecraft mc) {
        if (mc.player == null) return;
        AllyComponent current = mc.player.getData(ModAttachments.ALLY_COMPONENT.get());
        mc.player.setData(ModAttachments.ALLY_COMPONENT.get(), withFakeAllies(current));
    }

    // ==================== TICK ====================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            ErrorClientFlags.localPlayer = null;
            return;
        }
        ErrorClientFlags.localPlayer = mc.player.getUUID();
        ErrorClientFlags.clientThread = Thread.currentThread();
        long now = mc.level.getGameTime();
        ErrorClientFlags.clientGameTime = now;

        tickTyping(mc);
        tickFakeAllies(mc, now);
        tickFakeDeath(mc, now);
        tickHeldItems(mc, now);

        ErrorClientState.sanityReveals.values().removeIf(reveal -> reveal.until() < now);
        ErrorClientState.revealed.values().removeIf(until -> until < now);
    }

    private static void tickTyping(Minecraft mc) {
        boolean typing = ErrorClientFlags.eloquenceActive && mc.screen instanceof ChatScreen;
        if (typing != wasTyping || (typing && ++typingRefresh >= 10)) {
            typingRefresh = 0;
            wasTyping = typing;
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("typing", typing);
            PacketHandler.sendToServer(new ErrorActionPacket("typing", tag));
        }
    }

    private static void tickFakeAllies(Minecraft mc, long now) {
        List<ErrorClientState.FakeAlly> expired = ErrorClientState.fakeAllies.stream().filter(ally -> ally.until() < now).toList();
        if (expired.isEmpty() || mc.player == null) return;
        ErrorClientState.fakeAllies.removeAll(expired);

        AllyComponent current = mc.player.getData(ModAttachments.ALLY_COMPONENT.get());
        for (ErrorClientState.FakeAlly ally : expired) {
            if (!ally.wasReal()) current = current.removeAlly(ally.uuid());
        }
        mc.player.setData(ModAttachments.ALLY_COMPONENT.get(), current);
    }

    private static void tickFakeDeath(Minecraft mc, long now) {
        if (ErrorClientState.fakeHealthUntil > now && ErrorClientState.fakeHealth <= 0.001f && !ErrorClientState.fakeDeathShown) {
            ErrorClientState.fakeDeathShown = true;
            mc.setScreen(new FakeDeathScreen());
        }
    }

    /**
     * Cryptologist: held items glimmer, and a spark jumps when someone switches what they hold.
     */
    private static void tickHeldItems(Minecraft mc, long now) {
        Player player = mc.player;
        if (player == null || !"error".equals(ClientBeyonderCache.getPathway(player.getUUID()))
                || ClientBeyonderCache.getSequence(player.getUUID()) > 7) {
            ErrorClientState.lastHeld.clear();
            return;
        }

        Set<Integer> seen = new HashSet<>();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20), e -> e != player && e.isAlive())) {
            seen.add(entity.getId());
            ItemStack held = entity.getMainHandItem();
            ItemStack previous = ErrorClientState.lastHeld.put(entity.getId(), held.copy());
            Vec3 hand = getHandPosition(entity);

            if (previous != null && !ItemStack.isSameItem(previous, held)) {
                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z,
                            (mc.level.random.nextDouble() - 0.5) * 0.2, mc.level.random.nextDouble() * 0.15, (mc.level.random.nextDouble() - 0.5) * 0.2);
                }
            }

            if (!held.isEmpty() && now % 6 == 0) {
                mc.level.addParticle(GOLD_DUST, hand.x + (mc.level.random.nextDouble() - 0.5) * 0.25, hand.y + (mc.level.random.nextDouble() - 0.5) * 0.25,
                        hand.z + (mc.level.random.nextDouble() - 0.5) * 0.25, 0, 0.01, 0);
            }
        }
        ErrorClientState.lastHeld.keySet().retainAll(seen);
    }

    public static Vec3 getHandPosition(LivingEntity entity) {
        double yaw = Math.toRadians(entity.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return entity.position().add(forward.scale(0.3)).add(right.scale(entity.getBbWidth() * 0.6)).add(0, entity.getBbHeight() * 0.5, 0);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ErrorClientState.reset();
        ErrorClientFlags.pickpocketActive = false;
        ErrorClientFlags.eloquenceActive = false;
        ErrorClientFlags.decryptionActive = false;
        ErrorClientFlags.observationFocusActive = false;
        ErrorClientFlags.treasureHunterActive = false;
        ErrorClientFlags.abilityDeceiveUntil = 0;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!ErrorClientFlags.decryptionActive) return;
        ItemStack stack = event.getItemStack();
        boolean generated = stack.getOrDefault(ModDataComponents.SEALED_ARTIFACT_GENERATED, true);
        if (!generated && stack.getOrDefault(ModDataComponents.SEALED_ARTIFACT_GENERATED_FAILED, false)) {
            event.getToolTip().add(Component.translatable("ability.lotmcraft.decryption.artifact_will_fail").withColor(0xd23c3c));
        }
    }
}
