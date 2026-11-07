package de.jakob.lotm.beyonders.abilities.justiciar.balancing;

import java.util.*;

/** Deterministic matching of whole allied components. Independent of Minecraft for testing. */
public final class BattlePartitioner {
    public record Combatant(UUID id, double strength, Set<UUID> allies) {
        public Combatant {
            strength = Double.isFinite(strength) ? Math.max(0.01, strength) : 1;
            allies = Set.copyOf(allies);
        }
    }
    public record Plan(Map<UUID, Integer> cells, Map<UUID, Integer> factions) {
        public Plan { cells = Map.copyOf(cells); factions = Map.copyOf(factions); }
    }
    private record Group(int faction, List<Combatant> members, double strength) {}

    private BattlePartitioner() {}

    public static Plan divide(List<Combatant> input, UUID owner) {
        var nodes = new TreeMap<UUID, Combatant>();
        for (Combatant node : input) {
            if (nodes.put(node.id, node) != null) throw new IllegalArgumentException("Duplicate combatant");
        }
        var unseen = new TreeSet<>(nodes.keySet());
        var groups = new ArrayList<Group>();
        var factions = new HashMap<UUID, Integer>();
        while (!unseen.isEmpty()) {
            var queue = new ArrayDeque<UUID>();
            queue.add(unseen.pollFirst());
            var members = new ArrayList<Combatant>();
            while (!queue.isEmpty()) {
                Combatant current = nodes.get(queue.removeFirst());
                members.add(current);
                var adjacent = unseen.stream().filter(id -> current.allies.contains(id)
                        || nodes.get(id).allies.contains(current.id)).toList();
                for (UUID id : adjacent) { unseen.remove(id); queue.add(id); }
            }
            int faction = groups.size();
            members.forEach(member -> factions.put(member.id, faction));
            groups.add(new Group(faction, members, members.stream().mapToDouble(Combatant::strength).sum()));
        }
        Comparator<Group> order = Comparator.comparingDouble(Group::strength).reversed()
                .thenComparing(group -> group.members.getFirst().id);
        groups.sort(order);
        var cells = new HashMap<UUID, Integer>();
        if (groups.size() == 1) {
            groups.getFirst().members.forEach(member -> cells.put(member.id, 0));
            return new Plan(cells, factions);
        }
        int cell = 0;
        while (groups.size() >= 2) {
            Group first = groups.stream().filter(group -> group.members.stream().anyMatch(member -> member.id.equals(owner)))
                    .findFirst().orElse(groups.getFirst());
            groups.remove(first);
            Group opponent = groups.stream().min(Comparator.comparingDouble((Group group) ->
                            Math.abs(Math.log(first.strength / group.strength)))
                    .thenComparing(group -> group.members.getFirst().id)).orElseThrow();
            groups.remove(opponent);
            for (Combatant member : first.members) cells.put(member.id, cell);
            for (Combatant member : opponent.members) cells.put(member.id, cell);
            cell++;
        }
        // An unmatched faction waits as a whole, rather than being forced against its allies.
        if (!groups.isEmpty()) groups.getFirst().members.forEach(member -> cells.put(member.id, -1));
        return new Plan(cells, factions);
    }

    public static boolean permits(int sourceCell, int targetCell) {
        if (sourceCell == -1 || targetCell == -1) return false;
        if (sourceCell >= 0 || targetCell >= 0) return sourceCell == targetCell;
        return true;
    }
}
