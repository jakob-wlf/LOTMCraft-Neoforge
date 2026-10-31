package de.jakob.lotm.beyonders.abilities.sun;

import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.FxHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HolyOathAbility extends ToggleAbility {
    public HolyOathAbility(String id) {
        super(id, "morale_boost");

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(40f, 35f, 30f, 15f, 11.5f, 8.5f, 6.5f, 5f));
    }

    @Override
    protected float getSpiritualityCost() {
        return 0;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("sun", 7));
    }

    private static final Map<UUID, UUID> auras = new HashMap<>();

    @Override
    public void start(Level level, LivingEntity entity) {
        if(!(level instanceof ServerLevel serverLevel))
            return;

        FxHandler.playOnBody(serverLevel, "holy_oath_start", entity);
        FxHandler.cancelOnEntity(serverLevel, auras.put(entity.getUUID(), FxHandler.playOnBody(serverLevel, "holy_oath", entity)));
    }

    @Override
    public void tick(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        if (BeyonderData.getSpirituality(entity) <= 0) {
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal("Your spirituality is exhausted.").withColor(0xFF422a2a)
                ));
            }

            cancel((ServerLevel) level, entity);

            return;
        }

        entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 20, 1, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 6, 2, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 20 * 6, 6, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 1, false, false, false));
    }

    @Override
    public void stop(Level level, LivingEntity entity) {
        if(level instanceof ServerLevel serverLevel)
            FxHandler.cancelOnEntity(serverLevel, auras.remove(entity.getUUID()));
    }
}
