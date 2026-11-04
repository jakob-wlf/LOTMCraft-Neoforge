package de.jakob.lotm.attachments;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;

public class AbilityWheelComponent implements INBTSerializable<CompoundTag> {
    public static final int MAX_WHEELS = 6;

    private final ArrayList<ArrayList<String>> wheels = new ArrayList<>();
    private final ArrayList<Integer> selected = new ArrayList<>();
    private int activeWheel = 0;

    public AbilityWheelComponent() {
        addEmptyWheel();
    }

    public ArrayList<String> getAbilities() {
        return wheels.get(activeWheel);
    }

    public void setAbilities(ArrayList<String> abilities) {
        wheels.set(activeWheel, abilities);
        clampSelected(activeWheel);
    }

    public int getSelectedAbility() {
        return selected.get(activeWheel);
    }

    public int selectedAt(int index) {
        return selected.get(index);
    }

    public void setSelectedAbility(int selectedAbility) {
        selected.set(activeWheel, selectedAbility);
    }

    public int getActiveWheel() {
        return activeWheel;
    }

    public int getWheelCount() {
        return wheels.size();
    }

    public ArrayList<String> wheel(int index) {
        return wheels.get(index);
    }

    public void setActiveWheel(int index) {
        if (index >= 0 && index < wheels.size()) {
            activeWheel = index;
        }
    }

    public void nextWheel() {
        if (wheels.size() > 1) {
            activeWheel = (activeWheel + 1) % wheels.size();
        }
    }

    public boolean addWheel() {
        if (wheels.size() >= MAX_WHEELS) {
            return false;
        }
        addEmptyWheel();
        activeWheel = wheels.size() - 1;
        return true;
    }

    public boolean removeWheel() {
        if (wheels.size() <= 1) {
            return false;
        }
        wheels.remove(activeWheel);
        selected.remove(activeWheel);
        if (activeWheel >= wheels.size()) {
            activeWheel = wheels.size() - 1;
        }
        return true;
    }

    public void clampSelected(int index) {
        int size = wheels.get(index).size();
        int value = selected.get(index);
        if (value < 0 || value >= size) {
            selected.set(index, Math.max(0, size - 1));
        }
    }

    public AbilityWheelComponent copy() {
        AbilityWheelComponent result = new AbilityWheelComponent();
        result.copyFrom(this);
        return result;
    }

    public void copyFrom(AbilityWheelComponent other) {
        wheels.clear();
        selected.clear();
        for (ArrayList<String> wheel : other.wheels) {
            wheels.add(new ArrayList<>(wheel));
        }
        selected.addAll(other.selected);
        activeWheel = other.activeWheel;
        if (wheels.isEmpty()) {
            addEmptyWheel();
        }
        if (activeWheel < 0 || activeWheel >= wheels.size()) {
            activeWheel = 0;
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag wheelsTag = new ListTag();
        for (int i = 0; i < wheels.size(); i++) {
            CompoundTag wheelTag = new CompoundTag();
            ListTag list = new ListTag();
            for (String ability : wheels.get(i)) {
                list.add(StringTag.valueOf(ability));
            }
            wheelTag.put("Abilities", list);
            wheelTag.putInt("Selected", selected.get(i));
            wheelsTag.add(wheelTag);
        }
        tag.put("Wheels", wheelsTag);
        tag.putInt("ActiveWheel", activeWheel);

        ListTag active = new ListTag();
        for (String ability : getAbilities()) {
            active.add(StringTag.valueOf(ability));
        }
        tag.put("Abilities", active);
        tag.putInt("SelectedAbility", getSelectedAbility());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        wheels.clear();
        selected.clear();
        if (tag.contains("Wheels", Tag.TAG_LIST)) {
            ListTag wheelsTag = tag.getList("Wheels", Tag.TAG_COMPOUND);
            for (int i = 0; i < wheelsTag.size(); i++) {
                CompoundTag wheelTag = wheelsTag.getCompound(i);
                ArrayList<String> wheel = new ArrayList<>();
                if (wheelTag.contains("Abilities", Tag.TAG_LIST)) {
                    ListTag list = wheelTag.getList("Abilities", Tag.TAG_STRING);
                    for (int j = 0; j < list.size(); j++) {
                        wheel.add(list.getString(j));
                    }
                }
                wheels.add(wheel);
                selected.add(wheelTag.getInt("Selected"));
                clampSelected(i);
            }
            activeWheel = tag.getInt("ActiveWheel");
        } else if (tag.contains("Abilities", Tag.TAG_LIST)) {
            ArrayList<String> wheel = new ArrayList<>();
            ListTag list = tag.getList("Abilities", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                wheel.add(list.getString(i));
            }
            wheels.add(wheel);
            selected.add(tag.getInt("SelectedAbility"));
            clampSelected(0);
            activeWheel = 0;
        }
        if (wheels.isEmpty()) {
            addEmptyWheel();
        }
        if (activeWheel < 0 || activeWheel >= wheels.size()) {
            activeWheel = 0;
        }
    }

    private void addEmptyWheel() {
        wheels.add(new ArrayList<>());
        selected.add(0);
    }
}
