package de.jakob.lotm.network.packets.toServer;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.DisabledAbilitiesComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityHoldEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.HoldCastProgressPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.UUID;

public record HoldAbilityPacket(String abilityId) implements CustomPacketPayload {
    private static final HashMap<UUID, Float> charge = new HashMap<>();
    private static final HashMap<UUID, Long> seen = new HashMap<>();

    public static final Type<HoldAbilityPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "hold_ability"));

    public static final StreamCodec<FriendlyByteBuf, HoldAbilityPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            HoldAbilityPacket::abilityId,
            HoldAbilityPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoldAbilityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.isAlive()) return;
            String[] parts = packet.abilityId().split(":");
            Ability ability = LOTMCraft.abilityHandler.getById(parts[0]);
            if (ability == null || !ability.hasAbility(player, false)) return;
            if (player.getData(ModAttachments.COOLDOWN_COMPONENT).isOnCooldown(ability.getId())) return;
            int subIndex = -1;
            if (parts.length > 1) {
                try {
                    subIndex = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            if (subIndex < 0 && ability instanceof SelectableAbility selectable) subIndex = selectable.getSelectedAbilityIndex(player.getUUID());
            int needed = ability.getHoldTime(player, subIndex);
            if (needed <= 0) return;

            UUID id = player.getUUID();
            long now = player.level().getGameTime();
            if (seen.getOrDefault(id, Long.MIN_VALUE) < now - 2) charge.put(id, 0f);
            seen.put(id, now);

            DisabledAbilitiesComponent disabled = player.getData(ModAttachments.DISABLED_ABILITIES_COMPONENT);
            boolean sealed = !ability.canAlwaysBeUsed && (disabled.isAbilityUsageDisabled() || disabled.isSpecificAbilityDisabled(ability.getId()));
            if (sealed) {
                PacketHandler.sendToPlayer(player, new HoldCastProgressPacket(charge.getOrDefault(id, 0f), needed));
                return;
            }

            AbilityHoldEvent event = new AbilityHoldEvent(player, ability, subIndex);
            NeoForge.EVENT_BUS.post(event);
            float next = Math.min(needed, charge.getOrDefault(id, 0f) + event.getSpeed());
            charge.put(id, next);

            ability.onHolding(player.serverLevel(), player, subIndex, (int) next, needed);
            PacketHandler.sendToPlayer(player, new HoldCastProgressPacket(next, needed));
        });
    }
}
