package de.jakob.lotm.util.data;

import java.util.ArrayList;
import java.util.List;

public class AbilityWheelClientData {
    private static final List<List<String>> wheels = new ArrayList<>();
    private static final List<Integer> selectedPerWheel = new ArrayList<>();
    private static int activeWheel;

    // When true, the ability wheel is showing shared abilities rather than the player's own wheel
    public static boolean sharedAbilityMode = false;

    // Personal shared wheel ordering, set from IntrospectScreen's Shared tab
    private static List<String> sharedWheelAbilities = new ArrayList<>();
    private static int selectedSharedAbility = 0;

    public static List<String> getSharedWheelAbilities() {
        return sharedWheelAbilities;
    }

    public static void setSharedWheelAbilities(List<String> abilities) {
        sharedWheelAbilities = new ArrayList<>(abilities);
    }

    public static int getSelectedSharedAbility() {
        return selectedSharedAbility;
    }

    public static void setSelectedSharedAbility(int index) {
        selectedSharedAbility = index;
    }

    private static List<String> copiedAbilityIds = new ArrayList<>();
    private static List<String> copiedAbilityCopyTypes = new ArrayList<>();
    private static List<Integer> copiedAbilityRemainingUses = new ArrayList<>();

    public static List<String> getAbilityWheelAbilities() {
        if (wheels.isEmpty()) {
            return List.of();
        }
        return wheels.get(Math.min(activeWheel, wheels.size() - 1));
    }

    public static int getSelectedAbility() {
        if (selectedPerWheel.isEmpty()) {
            return 0;
        }
        return selectedPerWheel.get(Math.min(activeWheel, selectedPerWheel.size() - 1));
    }

    public static int getActiveWheel() {
        return activeWheel;
    }

    public static int getWheelCount() {
        return wheels.size();
    }

    public static boolean hasAnyAbilities() {
        for (List<String> wheel : wheels) {
            if (!wheel.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static void setActiveWheel(int index) {
        if (!wheels.isEmpty()) {
            activeWheel = Math.floorMod(index, wheels.size());
        }
    }

    public static void setAbilityWheelData(ArrayList<String> abilities, int selected) {
        if (wheels.isEmpty()) {
            wheels.add(new ArrayList<>(abilities));
            selectedPerWheel.add(selected);
            activeWheel = 0;
            return;
        }
        int index = Math.min(activeWheel, wheels.size() - 1);
        wheels.set(index, new ArrayList<>(abilities));
        while (selectedPerWheel.size() < wheels.size()) {
            selectedPerWheel.add(0);
        }
        selectedPerWheel.set(index, selected);
    }

    public static void setWheels(List<List<String>> incoming, int active, List<Integer> selectedIncoming) {
        wheels.clear();
        selectedPerWheel.clear();
        for (List<String> wheel : incoming) {
            wheels.add(new ArrayList<>(wheel));
        }
        if (wheels.isEmpty()) {
            wheels.add(new ArrayList<>());
        }
        selectedPerWheel.addAll(selectedIncoming);
        while (selectedPerWheel.size() < wheels.size()) {
            selectedPerWheel.add(0);
        }
        activeWheel = Math.floorMod(active, wheels.size());
    }

    public static List<String> getCopiedAbilityIds() {
        return copiedAbilityIds.stream().map(s -> s.split(":")[0] + ":-1:copied").toList();
    }

    public static List<String> getCopiedAbilityCopyTypes() {
        return copiedAbilityCopyTypes;
    }

    public static List<Integer> getCopiedAbilityRemainingUses() {
        return copiedAbilityRemainingUses;
    }

    public static void setCopiedAbilityData(ArrayList<String> abilityIds, ArrayList<String> copyTypes, ArrayList<Integer> remainingUses) {
        copiedAbilityIds = abilityIds;
        copiedAbilityCopyTypes = copyTypes;
        copiedAbilityRemainingUses = remainingUses;
    }

    public static void clearCache() {
        wheels.clear();
        selectedPerWheel.clear();
        activeWheel = 0;
        sharedAbilityMode = false;
        sharedWheelAbilities.clear();
        selectedSharedAbility = 0;
        copiedAbilityIds.clear();
        copiedAbilityCopyTypes.clear();
        copiedAbilityRemainingUses.clear();
    }
}