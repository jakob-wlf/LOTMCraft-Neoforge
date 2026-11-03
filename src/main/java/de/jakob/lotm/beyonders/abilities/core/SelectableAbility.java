package de.jakob.lotm.beyonders.abilities.core;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.AbilitySelectionPacket;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.UUID;

public abstract class SelectableAbility extends Ability {

    protected final HashMap<UUID, Integer> selectedAbilities = new HashMap<>();
    protected final HashMap<UUID, Integer> castedSubAbility = new HashMap<>();
    public SelectableAbility(String id, float cooldown, String... interactionFlags) {
        super(id, cooldown, interactionFlags);
    }

    public int getSelectedAbilityIndex(UUID uuid) {
        return selectedAbilities.getOrDefault(uuid, 0);
    }

    protected abstract String[] getAllAbilityNames();

    public String[] getSequenceDependantAbilityNames(int sequence) {
        return getAllAbilityNames();
    }

    public String[] getAbilityNamesCopy() {
        return getAllAbilityNames().clone();
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if(!(entity instanceof Player)) {
            castSelectedAbility(level, entity, getNPCSelectedAbility(entity, level));
            return;
        }

        if(!selectedAbilities.containsKey(entity.getUUID())) {
            selectedAbilities.put(entity.getUUID(), 0);
        }

        int selectedAbility = selectedAbilities.get(entity.getUUID());
        if(castedSubAbility.containsKey(entity.getUUID())) {
            selectedAbility = castedSubAbility.get(entity.getUUID());
            castedSubAbility.remove(entity.getUUID());
        }

        if(selectedAbility >= getSequenceDependantAbilityNames(BeyonderData.getSequence(entity)).length) {
            return;
        }

        castSelectedAbility(level, entity, selectedAbility);
    }

    /**
     * Replays a cast on the client with the sub-ability the server actually cast, which can differ from the
     * client's wheel selection when the cast came from a sub-ability keybind.
     */
    public void onClientAbilityUse(Level level, LivingEntity entity, int subAbility) {
        if(subAbility < 0 || !(entity instanceof Player)) {
            onAbilityUse(level, entity);
            return;
        }

        if(subAbility >= getSequenceDependantAbilityNames(BeyonderData.getSequence(entity)).length) {
            return;
        }

        castSelectedAbility(level, entity, subAbility);
    }

    /** The sub-ability the next cast will use: a pending keybind override, otherwise the wheel selection */
    public int getCastSubAbilityIndex(LivingEntity entity) {
        return castedSubAbility.getOrDefault(entity.getUUID(), selectedAbilities.getOrDefault(entity.getUUID(), 0));
    }

    protected abstract void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility);

    @Override
    public int getHoldTime(LivingEntity entity, int subIndex) {
        return super.getHoldTime(entity, subIndex >= 0 ? subIndex : getSelectedAbilityIndex(entity.getUUID()));
    }

    @Override
    public boolean showsHoldBar(LivingEntity entity, int subIndex) {
        return super.showsHoldBar(entity, subIndex >= 0 ? subIndex : getSelectedAbilityIndex(entity.getUUID()));
    }

    public void addSubAbilityOverride(LivingEntity entity, int selectedAbility) {
        castedSubAbility.put(entity.getUUID(), selectedAbility);
    }

    public void nextAbility(LivingEntity entity) {
        int sequence = BeyonderData.getSequence(entity);
        if(getSequenceDependantAbilityNames(sequence).length == 0)
            return;

        if(!selectedAbilities.containsKey(entity.getUUID())) {
            selectedAbilities.put(entity.getUUID(), 0);
        }

        int selectedAbility = selectedAbilities.get(entity.getUUID());
        selectedAbility++;
        if(selectedAbility >= getSequenceDependantAbilityNames(sequence).length) {
            selectedAbility = 0;
        }
        selectedAbilities.put(entity.getUUID(), selectedAbility);
        PacketHandler.sendToServer(new AbilitySelectionPacket(getId(), selectedAbility));
    }

    public String getSelectedAbility(LivingEntity entity) {
        int sequence = BeyonderData.getSequence(entity);

        if(getSequenceDependantAbilityNames(sequence).length == 0)
            return "";

        if(!selectedAbilities.containsKey(entity.getUUID())) {
            selectedAbilities.put(entity.getUUID(), 0);
        }

        int selectedAbility = selectedAbilities.get(entity.getUUID());

        if(selectedAbility >= getSequenceDependantAbilityNames(sequence).length) {
            selectedAbility = getSequenceDependantAbilityNames(sequence).length -1;
            selectedAbilities.put(entity.getUUID(), selectedAbility);
        }

        // Index into the same list the bounds were checked against; it isn't always a prefix of all names
        return getSequenceDependantAbilityNames(sequence)[selectedAbility];
    }

    public void setSelectedAbility(ServerPlayer player, int selectedAbility) {
        int sequence = BeyonderData.getSequence(player);
        if(getSequenceDependantAbilityNames(sequence).length == 0)
            return;

        if(selectedAbility < 0 || selectedAbility >= getSequenceDependantAbilityNames(sequence).length)
            return;

        selectedAbilities.put(player.getUUID(), selectedAbility);
    }

    public void previousAbility(LivingEntity entity) {
        // Same sequence as nextAbility() and the server's setSelectedAbility(), so both sides agree on the selection
        int sequence = BeyonderData.getSequence(entity);

        if(getSequenceDependantAbilityNames(sequence).length == 0)
            return;

        if(!selectedAbilities.containsKey(entity.getUUID())) {
            selectedAbilities.put(entity.getUUID(), 0);
        }

        int selectedAbility = selectedAbilities.get(entity.getUUID());
        selectedAbility--;
        if(selectedAbility <= -1) {
            selectedAbility = getSequenceDependantAbilityNames(sequence).length - 1;
        }
        selectedAbilities.put(entity.getUUID(), selectedAbility);
        PacketHandler.sendToServer(new AbilitySelectionPacket(getId(), selectedAbility));
    }

    public int getNPCSelectedAbility(LivingEntity entity, Level level) {
        return random.nextInt(getSequenceDependantAbilityNames(BeyonderData.getSequence(entity)).length);
    }
}