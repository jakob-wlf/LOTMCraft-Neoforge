package de.jakob.lotm.beyonders.abilities.error.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConceptStorage {

    private static final String ROOT = "lotm_error_concepts";
    private static final String EFFECTS = "effects";

    public enum Concept {
        DISTANCE("distance", "blocks"),
        MOVEMENT("movement", "s"),
        SIGHT("sight", "s"),
        THOUGHTS("thoughts", "s"),
        FIRE("fire", "s"),
        FROST("frost", "s"),
        TIME("time", "s"),
        VITALITY("vitality", "hp");

        public final String id;
        public final String unit;

        Concept(String id, String unit) {
            this.id = id;
            this.unit = unit;
        }

        public static Concept byId(String id) {
            for (Concept concept : values()) {
                if (concept.id.equals(id)) return concept;
            }
            return null;
        }
    }

    private ConceptStorage() {
    }

    private static CompoundTag root(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(ROOT, Tag.TAG_COMPOUND)) {
            data.put(ROOT, new CompoundTag());
        }
        return data.getCompound(ROOT);
    }

    public static int get(LivingEntity entity, Concept concept) {
        return root(entity).getInt(concept.id);
    }

    public static void add(LivingEntity entity, Concept concept, int amount) {
        if (amount <= 0) return;
        CompoundTag root = root(entity);
        root.putInt(concept.id, Math.min(100_000, root.getInt(concept.id) + amount));
    }

    public static int take(LivingEntity entity, Concept concept, int amount) {
        CompoundTag root = root(entity);
        int stored = root.getInt(concept.id);
        int taken = Math.min(stored, amount);
        root.putInt(concept.id, stored - taken);
        return taken;
    }

    public static void addEffect(LivingEntity entity, MobEffectInstance effect) {
        CompoundTag root = root(entity);
        ListTag list = root.getList(EFFECTS, Tag.TAG_COMPOUND);
        list.add(effect.save());
        if (list.size() > 24) list.remove(0);
        root.put(EFFECTS, list);
    }

    public static List<MobEffectInstance> getEffects(LivingEntity entity) {
        List<MobEffectInstance> effects = new ArrayList<>();
        ListTag list = root(entity).getList(EFFECTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(list.getCompound(i));
            if (effect != null) effects.add(effect);
        }
        return effects;
    }

    public static MobEffectInstance takeEffect(LivingEntity entity, int index) {
        CompoundTag root = root(entity);
        ListTag list = root.getList(EFFECTS, Tag.TAG_COMPOUND);
        if (index < 0 || index >= list.size()) return null;
        MobEffectInstance effect = MobEffectInstance.load(list.getCompound(index));
        list.remove(index);
        root.put(EFFECTS, list);
        return effect;
    }

    public static Map<Concept, Integer> getAll(LivingEntity entity) {
        Map<Concept, Integer> result = new LinkedHashMap<>();
        CompoundTag root = root(entity);
        for (Concept concept : Concept.values()) {
            int amount = root.getInt(concept.id);
            if (amount > 0) result.put(concept, amount);
        }
        return result;
    }
}
