package de.jakob.lotm.events.custom;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

public class ChangeBeyonderDataEvent extends Event {

    public LivingEntity entity;
    public String oldPathway;
    public String newPathway;
    public int oldSequence;
    public int newSequence;

    public ChangeBeyonderDataEvent(LivingEntity entity, String oldPathway, String newPathway, int oldSequence, int newSequence) {
        this.entity = entity;
        this.oldPathway = oldPathway;
        this.newPathway = newPathway;
        this.oldSequence = oldSequence;
        this.newSequence = newSequence;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }

    public String getOldPathway() {
        return oldPathway;
    }

    public void setOldPathway(String oldPathway) {
        this.oldPathway = oldPathway;
    }

    public String getNewPathway() {
        return newPathway;
    }

    public void setNewPathway(String newPathway) {
        this.newPathway = newPathway;
    }

    public int getOldSequence() {
        return oldSequence;
    }

    public void setOldSequence(int oldSequence) {
        this.oldSequence = oldSequence;
    }

    public int getNewSequence() {
        return newSequence;
    }

    public void setNewSequence(int newSequence) {
        this.newSequence = newSequence;
    }
}
