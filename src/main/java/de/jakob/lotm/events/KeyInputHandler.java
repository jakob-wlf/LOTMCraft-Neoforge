package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.artifacts.SealedArtifactData;
import de.jakob.lotm.data.ModDataComponents;
import de.jakob.lotm.gui.custom.ability_wheel.AbilityWheelScreen;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.handlers.ClientHandler;
import de.jakob.lotm.network.packets.toServer.*;
import de.jakob.lotm.rendering.AbilityHotBarRenderer;
import de.jakob.lotm.util.ClientBeyonderCache;
import de.jakob.lotm.util.data.AbilityWheelClientData;
import de.jakob.lotm.util.helper.AbilityBarHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public class KeyInputHandler {

    // set from the ability wheel screen when clicked
    public static int holdAbilityWheelCooldownTicks = 0;
    public static boolean wasWheelOpenedWithHold = false;

    private static KeyMapping holdKey;
    private static Runnable holdCast;
    private static String holdAbility;
    private static int holdNeeded;

    private static int needed() {
        int server = ClientHandler.holdNeeded();
        return server > 0 ? server : holdNeeded;
    }

    public static float holdProgress(float partialTick) {
        if (holdKey == null || needed() <= 0) return -1;
        return Math.min(1f, ClientHandler.holdCharge() / needed());
    }

    public static int holdTicksLeft() {
        if (holdKey == null) return 0;
        return (int) Math.ceil(Math.max(0f, needed() - ClientHandler.holdCharge()));
    }

    private static void hold(KeyMapping key, int ticks, String abilityId, Runnable cast) {
        if (ticks < 0) return;
        if (ticks == 0) {
            cast.run();
            return;
        }
        if (holdKey != null) return;
        holdKey = key;
        holdCast = cast;
        holdAbility = abilityId;
        holdNeeded = ticks;
        ClientHandler.setHoldCharge(0, 0);
    }

    private static void tickHold(Minecraft mc) {
        if (holdKey == null) return;
        if (mc.player == null || mc.screen != null || !mc.player.isAlive()) {
            holdKey = null;
            holdCast = null;
            ClientHandler.setHoldCharge(0, 0);
            return;
        }
        if (holdKey.isDown()) {
            while (holdKey.consumeClick()) {}
            if (holdAbility != null) PacketHandler.sendToServer(new HoldAbilityPacket(holdAbility));
            return;
        }
        Runnable cast = holdCast;
        boolean ready = ClientHandler.holdCharge() >= needed();
        holdKey = null;
        holdCast = null;
        ClientHandler.setHoldCharge(0, 0);
        if (ready) cast.run();
    }

    private static int holdTime(Player player, String abilityId) {
        if (player == null || abilityId == null) return 0;
        Ability ability = LOTMCraft.abilityHandler.getById(abilityId.split(":")[0]);
        if (ability == null) return 0;
        int ticks = ability.getHoldTime(player, getIndex(abilityId));
        if (ticks > 0 && player.getData(ModAttachments.COOLDOWN_COMPONENT).isOnCooldown(ability.getId())) return -1;
        return ticks;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        tickHold(mc);

        if(LOTMCraft.toggleGriefingKey != null && LOTMCraft.toggleGriefingKey.consumeClick()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                PacketHandler.sendToServer(new ToggleGriefingPacket());
            }
        }

        if(LOTMCraft.pathwayInfosKey != null && LOTMCraft.pathwayInfosKey.consumeClick()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                PacketHandler.sendToServer(new OpenIntrospectMenuPacket(ClientBeyonderCache.getSequence(player.getUUID()), ClientBeyonderCache.getPathway(player.getUUID())));
            }
        }

        if(LOTMCraft.enterSefirotKey != null && LOTMCraft.enterSefirotKey.consumeClick()) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                PacketHandler.sendToServer(new TeleportToSefirotPacket());
            }
        }

        if(LOTMCraft.nextAbilityKey != null && LOTMCraft.nextAbilityKey.consumeClick()) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            if (player.isCrouching()){
                ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (!stack.has(ModDataComponents.SEALED_ARTIFACT_DATA)) {
                    stack = player.getItemInHand(InteractionHand.OFF_HAND);
                }
                if (stack.has(ModDataComponents.SEALED_ARTIFACT_DATA)) {
                    SealedArtifactData data = stack.get(ModDataComponents.SEALED_ARTIFACT_DATA);
                    if (data != null || !data.abilities().isEmpty()) {
                        int selectedIndex = stack.getOrDefault(ModDataComponents.SEALED_ARTIFACT_SELECTED, 0);
                        Ability artifactAbility = data.abilities().get(selectedIndex);
                        if(artifactAbility instanceof SelectableAbility artifactSelectableAbility) {
                            artifactSelectableAbility.nextAbility(player);

                            player.displayClientMessage(Component.translatable(artifactSelectableAbility.getSelectedAbility(player)).withStyle(ChatFormatting.AQUA), true);
                            return;
                        }
                    }
                }
            }

            // if no return was triggered, run the normal code
            if(ClientBeyonderCache.isBeyonder(player.getUUID())) {
                if(AbilityWheelClientData.getSelectedAbility() < 0 || AbilityWheelClientData.getSelectedAbility() >= AbilityWheelClientData.getAbilityWheelAbilities().size()) {
                    return;
                }

                String abilityId = AbilityWheelClientData.getAbilityWheelAbilities().get(AbilityWheelClientData.getSelectedAbility());
                String baseId = abilityId.split(":")[0];
                int subIndex = getIndex(abilityId);
                if(subIndex > 0) return;
                Ability ability = LOTMCraft.abilityHandler.getById(baseId);
                if(!(ability instanceof SelectableAbility selectableAbility)) {
                    return;
                }

                selectableAbility.nextAbility(player);
            }
        }

        if(LOTMCraft.previousAbilityKey != null && LOTMCraft.previousAbilityKey.consumeClick()) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            if (player.isCrouching()){
                ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (!stack.has(ModDataComponents.SEALED_ARTIFACT_DATA)) {
                    stack = player.getItemInHand(InteractionHand.OFF_HAND);
                }
                if (stack.has(ModDataComponents.SEALED_ARTIFACT_DATA)) {
                    SealedArtifactData data = stack.get(ModDataComponents.SEALED_ARTIFACT_DATA);
                    if (data != null || !data.abilities().isEmpty()) {
                        int selectedIndex = stack.getOrDefault(ModDataComponents.SEALED_ARTIFACT_SELECTED, 0);

                        Ability artifactAbility = data.abilities().get(selectedIndex);
                        if(artifactAbility instanceof SelectableAbility artifactSelectableAbility) {
                            artifactSelectableAbility.previousAbility(player);

                            player.displayClientMessage(Component.translatable(artifactSelectableAbility.getSelectedAbility(player)).withStyle(ChatFormatting.AQUA), true);
                            return;
                        }
                    }
                }
            }

            // if no return was triggered, run the normal code
            if(ClientBeyonderCache.isBeyonder(player.getUUID())) {
                if(AbilityWheelClientData.getSelectedAbility() < 0 || AbilityWheelClientData.getSelectedAbility() >= AbilityWheelClientData.getAbilityWheelAbilities().size()) {
                    return;
                }

                String abilityId = AbilityWheelClientData.getAbilityWheelAbilities().get(AbilityWheelClientData.getSelectedAbility());
                String baseId = abilityId.split(":")[0];
                int subIndex = getIndex(abilityId);
                if(subIndex >= 0) return;
                Ability ability = LOTMCraft.abilityHandler.getById(baseId);
                if(!(ability instanceof SelectableAbility selectableAbility)) {
                    return;
                }

                selectableAbility.previousAbility(player);
            }
        }

        if (LOTMCraft.openWheelToggleKey != null && LOTMCraft.openWheelToggleKey.consumeClick()) {
            wasWheelOpenedWithHold = false;
            openAbilityWheel();
        }

        if (LOTMCraft.openWheelHoldKey != null && LOTMCraft.openWheelHoldKey.consumeClick() && mc.screen == null && holdAbilityWheelCooldownTicks <= 0) {
            wasWheelOpenedWithHold = true;
            openAbilityWheel();
        }

        // Handle use ability key
        if (LOTMCraft.useSelectedAbilityKey != null && LOTMCraft.useSelectedAbilityKey.consumeClick()) {
            List<String> wheel = AbilityWheelClientData.getAbilityWheelAbilities();
            int selected = AbilityWheelClientData.getSelectedAbility();
            String abilityId = selected >= 0 && selected < wheel.size() ? wheel.get(selected) : null;
            hold(LOTMCraft.useSelectedAbilityKey, holdTime(mc.player, abilityId), abilityId, () -> PacketHandler.sendToServer(new UseSelectedAbilityPacket()));
        }

        // Open shared ability wheel
        if (LOTMCraft.openSharedAbilityWheelKey != null && LOTMCraft.openSharedAbilityWheelKey.consumeClick()
                && mc.screen == null) {
            openSharedAbilityWheel();
        }

        // Use selected shared ability
        if (LOTMCraft.useSharedAbilityKey != null && LOTMCraft.useSharedAbilityKey.consumeClick()) {
            List<String> sharedWheel = AbilityWheelClientData.getSharedWheelAbilities();
            int idx = AbilityWheelClientData.getSelectedSharedAbility();
            if (!sharedWheel.isEmpty() && idx >= 0 && idx < sharedWheel.size()) {
                String abilityId = sharedWheel.get(idx);
                hold(LOTMCraft.useSharedAbilityKey, holdTime(mc.player, abilityId), abilityId, () -> PacketHandler.sendToServer(new UseSharedAbilityPacket(abilityId)));
            }
        }

        // Cycle sub-abilities of the selected shared ability forward
        if (LOTMCraft.nextSharedAbilityKey != null && LOTMCraft.nextSharedAbilityKey.consumeClick()) {
            Player player = mc.player;
            if (player != null) {
                List<String> sharedWheel = AbilityWheelClientData.getSharedWheelAbilities();
                int idx = AbilityWheelClientData.getSelectedSharedAbility();
                if (!sharedWheel.isEmpty() && idx >= 0 && idx < sharedWheel.size()) {
                    Ability sharedAbility = LOTMCraft.abilityHandler.getById(sharedWheel.get(idx));
                    if (sharedAbility instanceof SelectableAbility selectableShared) {
                        selectableShared.nextAbility(player);
                        player.displayClientMessage(
                            Component.translatable(selectableShared.getSelectedAbility(player)).withStyle(ChatFormatting.AQUA), true);
                    }
                }
            }
        }

        // Cycle sub-abilities of the selected shared ability backward
        if (LOTMCraft.previousSharedAbilityKey != null && LOTMCraft.previousSharedAbilityKey.consumeClick()) {
            Player player = mc.player;
            if (player != null) {
                List<String> sharedWheel = AbilityWheelClientData.getSharedWheelAbilities();
                int idx = AbilityWheelClientData.getSelectedSharedAbility();
                if (!sharedWheel.isEmpty() && idx >= 0 && idx < sharedWheel.size()) {
                    Ability sharedAbility = LOTMCraft.abilityHandler.getById(sharedWheel.get(idx));
                    if (sharedAbility instanceof SelectableAbility selectableShared) {
                        selectableShared.previousAbility(player);
                        player.displayClientMessage(
                            Component.translatable(selectableShared.getSelectedAbility(player)).withStyle(ChatFormatting.AQUA), true);
                    }
                }
            }
        }

        if(holdAbilityWheelCooldownTicks > 0) {
            holdAbilityWheelCooldownTicks--;
        }

        if (LOTMCraft.toggleAbilityHotbarKey != null && LOTMCraft.toggleAbilityHotbarKey.consumeClick()) {
            AbilityHotBarRenderer.toggle();
        }

        for (int i = 0; i < LOTMCraft.useAbilityBarKeys.length; i++) {
            KeyMapping keyMapping = LOTMCraft.useAbilityBarKeys[i];
            if (keyMapping != null && keyMapping.consumeClick()) {
                int slot = i;
                List<String> bar = mc.player != null ? AbilityBarHelper.getAbilities(mc.player) : List.of();
                String abilityId = slot < bar.size() ? bar.get(slot) : null;
                hold(keyMapping, holdTime(mc.player, abilityId), abilityId, () -> PacketHandler.sendToServer(new UseKeyboundAbilityPacket(slot)));
            }
        }


        if (LOTMCraft.returnToMainBody != null && LOTMCraft.returnToMainBody.consumeClick()) {
            PacketHandler.sendToServer(new ReturnToMainBodyPacket());
            PacketHandler.sendToServer(new StopDiscernmentPacket());
        }
        if (LOTMCraft.openArtifactWheel != null && LOTMCraft.openArtifactWheel.consumeClick()) {
            openArtifactWheel();
        }
        if(LOTMCraft.nextArtifactAbilityKey != null && LOTMCraft.nextArtifactAbilityKey.consumeClick()) {
            PacketHandler.sendToServer(new NextArtifactAbilityPacket());
        }
        if(LOTMCraft.openMarionetteMenuKey != null && LOTMCraft.openMarionetteMenuKey.consumeClick()) {
            PacketHandler.sendToServer(new RequestOpenMarionetteMenuPacket());
        }
        if(LOTMCraft.nextMarionetteKey != null && LOTMCraft.nextMarionetteKey.consumeClick()) {
            PacketHandler.sendToServer(new NextMarionettePacket());
        }
    }

    private static int getIndex(String s) {
        String[] parts = s.split(":");
        if (parts.length < 2) return -1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }



    @SubscribeEvent
    public static void onKeyReleased(ScreenEvent.KeyReleased.Post event) {
        if(event.getKeyCode() == LOTMCraft.openWheelHoldKey.getKey().getValue()) {
            if(Minecraft.getInstance().screen instanceof AbilityWheelScreen && wasWheelOpenedWithHold) {
                Minecraft.getInstance().screen.onClose();
                PacketHandler.sendToServer(new CloseAbilityWheelPacket());
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        int key = event.getKey();

        int number = -1;

        // Check if it's a number key (top row)
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            number = (key == GLFW.GLFW_KEY_0) ? 0 : (key - GLFW.GLFW_KEY_0);
        }

        // Or check numpad keys
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            number = key - GLFW.GLFW_KEY_KP_0;
        }

        if(number <= 0) {
            return;
        }

        if(!(Minecraft.getInstance().screen instanceof AbilityWheelScreen)) {
            return;
        }

        if((number - 1) >= AbilityWheelClientData.getAbilityWheelAbilities().size()) {
            return;
        }

        if(LOTMCraft.openWheelHoldKey.getKey().getNumericKeyValue().orElse(-1) == number) {
            return;
        }
        if(LOTMCraft.openWheelToggleKey.getKey().getNumericKeyValue().orElse(-1) == number) {
            return;
        }

        PacketHandler.sendToServer(new UpdateSelectedAbilityPacket(number - 1));
        AbilityWheelClientData.setAbilityWheelData(
                new ArrayList<>(AbilityWheelClientData.getAbilityWheelAbilities()),
                number - 1
        );
        PacketHandler.sendToServer(new CloseAbilityWheelPacket());
    }

    private static void openSharedAbilityWheel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!de.jakob.lotm.util.helper.ClientTeamData.hasTeam()) {
            mc.player.displayClientMessage(
                    Component.literal("You are not part of a Red Priest's team.").withStyle(ChatFormatting.RED), true);
            return;
        }

        List<String> sharedAbilities = AbilityWheelClientData.getSharedWheelAbilities();
        if (sharedAbilities.isEmpty()) {
            mc.player.displayClientMessage(
                    Component.literal("No abilities have been added to the shared wheel yet.").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        AbilityWheelClientData.sharedAbilityMode = true;
        PacketHandler.sendToServer(new OpenAbilityWheelPacket());
    }

    private static void openAbilityWheel() {
        Minecraft mc = Minecraft.getInstance();
        if (AbilityWheelClientData.getAbilityWheelAbilities().isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("lotm.ability_wheel.no_abilities"), true);
        } else {
            PacketHandler.sendToServer(new OpenAbilityWheelPacket());
        }
    }

    private static void openArtifactWheel() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!stack.has(ModDataComponents.SEALED_ARTIFACT_DATA)) {
            stack = mc.player.getItemInHand(InteractionHand.OFF_HAND);
            if (!stack.has(ModDataComponents.SEALED_ARTIFACT_DATA)) {
                return;
            }
        }
        SealedArtifactData data = stack.get(ModDataComponents.SEALED_ARTIFACT_DATA);
        if (data == null || data.abilities().isEmpty()) {
            return;
        }
        PacketHandler.sendToServer(new OpenArtifactWheelPacket(stack));
    }
}
