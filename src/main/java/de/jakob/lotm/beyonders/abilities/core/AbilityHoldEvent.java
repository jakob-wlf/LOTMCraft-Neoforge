package de.jakob.lotm.beyonders.abilities.core;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

public class AbilityHoldEvent extends Event {

    private final LivingEntity entity;
    private final Ability ability;
    private final int subIndex;
    private float speed = 1f;
    private final int tick;

    public AbilityHoldEvent(LivingEntity entity, Ability ability, int subIndex, int next) {
        this.entity = entity;
        this.ability = ability;
        this.subIndex = subIndex;
        this.tick = next;
    }

    public int getTick() {
        return tick;
    }
    public LivingEntity getEntity() {
        return entity;
    }

    public Ability getAbility() {
        return ability;
    }

    public int getSubIndex() {
        return subIndex;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0f, speed);
    }

    public void multiply(float factor) {
        setSpeed(speed * factor);
    }

    public boolean is(String abilityId) {
        return ability.getId().equals(abilityId);
    }

    public boolean is(String abilityId, int subIndex) {
        return is(abilityId) && this.subIndex == subIndex;
    }

    public void setSpeed(String abilityId, float speed) {
        if (is(abilityId)) setSpeed(speed);
    }

    public void setSpeed(String abilityId, int subIndex, float speed) {
        if (is(abilityId, subIndex)) setSpeed(speed);
    }

    public void multiply(String abilityId, float factor) {
        if (is(abilityId)) multiply(factor);
    }

    public void multiply(String abilityId, int subIndex, float factor) {
        if (is(abilityId, subIndex)) multiply(factor);
    }
}