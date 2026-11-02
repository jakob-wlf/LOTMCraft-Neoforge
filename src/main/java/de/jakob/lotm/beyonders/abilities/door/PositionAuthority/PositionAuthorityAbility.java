package de.jakob.lotm.beyonders.abilities.door.PositionAuthority;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.door.DimensionSightAbility;
import de.jakob.lotm.beyonders.abilities.door.PlayerTeleportationAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.*;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public abstract class PositionAuthorityAbility extends Ability {

    private static final String PREFIX = "position:";

    private static final HashMap<UUID, ServerPlayer> pending = new HashMap<>();

    protected PositionAuthorityAbility(String id, float cooldown) {
        super(id, cooldown);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeShared = false;
        cannotBeStolen = true;
    }

    protected abstract String command();

    protected abstract void apply(ServerPlayer caster, ServerPlayer target, int seq);

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("door", 2));
    }

    @Override
    public boolean canUse(LivingEntity entity, boolean hasToHaveAbility, boolean doesConsumeSpirituality, boolean isCopied) {
        return pending.containsKey(entity.getUUID()) && super.canUse(entity, hasToHaveAbility, doesConsumeSpirituality, isCopied);
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer caster)) return;
        ServerPlayer target = pending.remove(caster.getUUID());
        if (target == null || !target.isAlive()) return;
        apply(caster, target, AbilityUtil.getSeqWithArt(caster, this));
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String raw = event.getRawText().trim();
        if (!raw.toLowerCase(Locale.ROOT).startsWith(PREFIX)) return;
        String rest = raw.substring(PREFIX.length()).trim();
        String lower = rest.toLowerCase(Locale.ROOT);

        PositionAuthorityAbility chosen = null;
        for (Ability ability : LOTMCraft.abilityHandler.getAbilities()) {
            if (!(ability instanceof PositionAuthorityAbility position)) continue;
            String command = position.command() + " ";
            if (!lower.startsWith(command)) continue;
            if (chosen == null || position.command().length() > chosen.command().length()) chosen = position;
        }
        if (chosen == null) return;

        ServerPlayer caster = event.getPlayer();
        if (!chosen.hasAbility(caster, false)) return;

        // It's a command, not a chat message: don't broadcast who is being targeted
        event.setCanceled(true);

        String name = rest.substring(chosen.command().length()).trim();
        ServerPlayer target = caster.server.getPlayerList().getPlayerByName(name);
        int color = BeyonderData.pathwayInfos.get("door").color();
        if (target == null || target == caster || DimensionSightAbility.unwatchable(caster, target)) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.not_found", name).withColor(color));
            return;
        }
        if (PlayerTeleportationAbility.unreachable(target) || PlayerTeleportationAbility.unreachable(caster)) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.position_authority.unreachable", name).withColor(color));
            return;
        }

        PositionAuthorityAbility ability = chosen;
        caster.server.execute(() -> {
            pending.put(caster.getUUID(), target);
            ability.useAbility((ServerLevel) caster.level(), caster);
            pending.remove(caster.getUUID());
        });
    }
}
