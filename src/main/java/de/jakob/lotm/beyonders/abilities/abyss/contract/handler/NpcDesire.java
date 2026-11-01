package de.jakob.lotm.beyonders.abilities.abyss.contract.handler;

import de.jakob.lotm.beyonders.abilities.abyss.contract.ContractData;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.List;

public final class NpcDesire {
    private NpcDesire() {}
    public enum Kind { ITEM, KILL_VILLAGER }
    public record Desire(Kind kind, String itemId, int count, String profession, int level, String reason, String label, String targetName) {
        public String demand() {
            if (kind == Kind.ITEM) {
                if (label != null && !label.isEmpty()) return count > 1 ? count + "x " + label : label;
                return ContractData.describeTerm(ContractData.TermType.ITEM, itemId, count);
            }
            return "the death of a level " + level + " " + professionName(profession) + " named " + targetName;
        }
    }
    public static boolean isBeyonder(LivingEntity e) { return BeyonderData.isBeyonder(e); }
    public static String pathwayOf(LivingEntity e) { return isBeyonder(e) ? BeyonderData.getPathway(e) : ""; }
    public static int seqOf(LivingEntity e) { return isBeyonder(e) ? BeyonderData.getSequence(e) : 10; }
    private static String idName(String pathway, int seq) {
        String name = BeyonderData.getSequenceName(pathway, seq);
        if (name.isBlank() || name.equals("Unknown")) return null;
        return name.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }
    private static String displayName(String pathway, int seq) {
        String n = BeyonderData.getSequenceName(pathway, seq);
        return n.isBlank() || n.equals("Unknown") ? null : n;
    }
    private static String potionId(String pathway, int seq) {
        String name = idName(pathway, seq);
        if (name == null) return FALLBACK_ITEM;
        return "lotmcraft:" + name + "_potion";
    }
    private static String recipeId(String pathway, int seq) {
        String name = idName(pathway, seq);
        if (name == null) return FALLBACK_ITEM;
        return "lotmcraft:" + name;
    }
    private static final String FALLBACK_ITEM = "minecraft:nether_star";
    private static String existingOrFallback(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? id : FALLBACK_ITEM;
    }
    public static boolean shouldAggro(LivingEntity e) {
        String p = pathwayOf(e);
        return (p.equals("sun") || p.equals("twilight_giant")) && seqOf(e) <= 4;
    }
    public static void aggro(LivingEntity e, ServerPlayer player) {
        if (e instanceof Mob m) {
            m.setTarget(player);
            m.setLastHurtByMob(player);
        }
    }
    private static final String[] EMERALD_REASONS = {
            "\"I took out a loan from a man I should never have trusted, and the interest has eaten me alive. "
                    + "Every week the debt grows, and his collectors are getting bolder. Pay it off and I can sleep again.\"",
            "\"There's barely any food in my house. The harvest failed, the market prices have tripled, and "
                    + "I've been skipping meals so my family can eat. I just need enough to get us through the season.\"",
            "\"Call it greed if you like, but I want the wealth. I've watched the other villagers build "
                    + "bigger homes and bigger stalls while I stay in the same shack. Emeralds are the only measure "
                    + "anyone here respects, and I intend to have more of them.\"",
            "\"Do you know what these travelers offer me? Six cod and one emerald for one cooked cod. "
                    + "Six cod! They think I'm a fool, and I've been bleeding trade for months. "
                    + "I need the emeralds to stay in business.\""
    };
    private static final String[] RECIPE_REASONS = {
            "\"I've searched every ruin and every dusty shop for the next recipe and found nothing. "
                    + "I can feel my body reaching its limit. If I don't find it soon, I'll hit the end of my road "
                    + "and never get past it. Bring me the recipe and I'll buy back my own future.\"",
            "\"Others from my pathway have started hunting me. They know I'm close to my next step and they "
                    + "want to stop me, or take what I have. With the recipe I can advance properly. "
                    + "Without it, I walk into my advancement unready and they'll be waiting.\"",
            "\"The faction I belong to fears what I'm becoming. They smile at me, but they will never let me "
                    + "advance. Every request for the recipe has been 'lost' or 'delayed'. I'll get it "
                    + "myself, outside their reach, whatever the cost.\""
    };
    private static final String[] POTION_REASONS = {
            "\"I'm being hunted. They've found my hiding places twice already and I won't survive a third time. "
                    + "I need power, and I need it fast. Bring me the potion and I'll stop running.\"",
            "\"My life is running out, and I can feel it. The potion would stretch my days, maybe by years. "
                    + "I'm not ready to die, and I'll bargain away anything to avoid it, even my soul.\"",
            "\"There's someone I have to kill, and I don't care what it takes. I won't tell you who or why. "
                    + "The potion gives me the strength to finish it. Get it for me and the rest "
                    + "isn't your concern.\""
    };
    private static final String[] KILL_REASONS = {
            "\"A certain %s took the job I spent years working toward. I did everything right, "
                    + "and they stepped in and took it. I can't live in the same village as the person who "
                    + "took my dream. Make them go away.\"",
            "\"A %s stole the one I loved. They knew how I felt and did it anyway, smiling the whole time. "
                    + "I think about it every night. I want them gone.\"",
            "\"I want to see that %s bleed. I won't pretend there's a noble reason. "
                    + "I just want to watch it happen, and I'll pay for the privilege.\"",
            "\"That %s has a spouse I can't stop thinking about. With them out of the way, "
                    + "the road is clear. Don't ask me to explain it; just do it.\"",
            "\"A %s keeps getting the trades that should be mine. Every traveler walks right past my door and "
                    + "straight to theirs. At this rate I'll be out of business by winter, so "
                    + "I want them out of the picture.\""
    };
    private static String pick(RandomSource r, String[] a) { return a[r.nextInt(a.length)]; }
    public static Desire get(LivingEntity e) {
        CompoundTag data = e.getPersistentData();
        CompoundTag d = data.getCompound("lotm_desire");
        if (!d.getBoolean("rolled")) {
            d = roll(e);
            data.put("lotm_desire", d);
        }
        if (!d.getBoolean("has")) return null;
        if (d.getString("kind").equals("KILL_VILLAGER") && d.getString("tname").isEmpty()) {
            d.putString("tname", NpcNames.random(e.getRandom()));
            data.put("lotm_desire", d);
        }
        return new Desire(Kind.valueOf(d.getString("kind")), d.getString("item"), d.getInt("count"), d.getString("prof"), d.getInt("level"), d.getString("reason"), d.getString("label"), d.getString("tname"));
    }
    private static CompoundTag roll(LivingEntity e) {
        CompoundTag d = new CompoundTag();
        d.putBoolean("rolled", true);
        RandomSource r = e.getRandom();
        if (r.nextBoolean()) { d.putBoolean("has", false); return d; }   // 50%: wants nothing
        Kind kind;
        String item = "";
        int count = 0, level = 0;
        String prof = "", reason, label = "";
        if (e instanceof Villager v) {
            level = v.getVillagerData().getLevel();
            if (r.nextBoolean()) {
                kind = Kind.ITEM;
                item = "minecraft:emerald";
                count = 40 * level;
                reason = pick(r, EMERALD_REASONS);
            } else {
                kind = Kind.KILL_VILLAGER;
                prof = ownOrRandomProfession(v, r);
                reason = String.format(pick(r, KILL_REASONS), professionName(prof));
            }
        } else if (isBeyonder(e)) {
            kind = Kind.ITEM;
            count = 1;
            int seq = seqOf(e);
            String path = pathwayOf(e);
            if (seq == 0) {
                item = FALLBACK_ITEM;
                reason = "there is nothing left above them, only relics";
            } else if (seq >= 7) {                     // seq 9-7: potion
                item = existingOrFallback(potionId(path, seq - 1));
                reason = pick(r, POTION_REASONS);
                String n = displayName(path, seq - 1);
                if (n != null && !item.equals(FALLBACK_ITEM)) label = n + " Potion";
            } else {                                   // seq 6 and stronger: recipe
                item = existingOrFallback(recipeId(path, seq - 1));
                reason = pick(r, RECIPE_REASONS);
                String n = displayName(path, seq - 1);
                if (n != null && !item.equals(FALLBACK_ITEM)) label = n + " Recipe";
            }
        } else {
            kind = Kind.ITEM;
            item = "minecraft:iron_ingot";
            count = 4 + r.nextInt(13);
            reason = "they simply want something solid in their hands";
        }
        d.putBoolean("has", true);
        d.putString("kind", kind.name());
        d.putString("item", item);
        d.putInt("count", count);
        d.putString("prof", prof);
        d.putInt("level", level);
        d.putString("reason", reason);
        d.putString("label", label);
        return d;
    }
    private static String ownOrRandomProfession(Villager v, RandomSource r) {
        VillagerProfession own = v.getVillagerData().getProfession();
        if (own != VillagerProfession.NONE && own != VillagerProfession.NITWIT) return BuiltInRegistries.VILLAGER_PROFESSION.getKey(own).toString();
        List<String> all = new ArrayList<>();
        for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
            if (p == VillagerProfession.NONE || p == VillagerProfession.NITWIT) continue;
            all.add(BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).toString());
        }
        return all.get(r.nextInt(all.size()));
    }
    public static String professionName(String key) {
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        return path.isEmpty() ? "villager" : path.replace('_', ' ');
    }
    public static void check(ServerPlayer player, LivingEntity target) {
        if (shouldAggro(target)) {
            aggro(target, player);
            return;
        }
        Desire d = get(target);
        if (d == null) {
            player.sendSystemMessage(Component.literal("They want nothing for their soul.").withColor(0x9932cc));
            return;
        }
        player.sendSystemMessage(Component.literal("They want " + d.demand() + " in exchange for their soul.").withColor(0x9932cc));
        player.sendSystemMessage(Component.literal("Reason: " + d.reason()).withColor(0xCCCCCC));
    }
}