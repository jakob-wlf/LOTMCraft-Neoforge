package de.jakob.lotm.beyonders.abilities.error;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.CopiedAbilityComponent;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.util.helper.FxHandler;
import de.jakob.lotm.beyonders.abilities.error.handler.ErrorUtil;
import de.jakob.lotm.beyonders.abilities.error.handler.TheftHandler;
import de.jakob.lotm.events.ProhibitionHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.CopiedAbilityHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.*;

public class StealAuthorityAbility extends Ability {

    private static final Map<String, Map.Entry<String, List<String>>> AUTHORITIES = new HashMap<>();

    static {
        put("fool", "change", "graft", "shapeshift", "fooling", "miracle");
        put("door", "teleportation", "blink", "travelers_door", "player_teleportation", "teleportation_authority", "wandering", "door_opening", "exile", "waypoint", "door_substitution");
        put("sun", "light", "light", "holy", "sun", "unshadowed", "flaring", "halo", "eternal_daytime");
        put("red_priest", "flames_and_war", "flame", "fire", "pyrokinesis", "war", "conquer", "cull");
        put("tyrant", "storms", "lightning", "thunder", "wind", "water", "tsunami", "hurricane", "earthquake", "storm", "downpour", "ocean", "electromagnetic", "roar");
        put("darkness", "night", "conceal", "night", "nightmare", "midnight", "horror", "darkness", "requiem");
        put("death", "death", "death", "spirit", "underworld", "pale", "undying", "nation");
        put("demoness", "disaster", "disaster", "apocalypse", "chaos", "plague", "disease", "curse", "petrification", "black_flame", "frost", "collapse");
        put("mother", "life", "life", "heal", "plant", "nature", "bloom", "desolation", "golem", "mutation", "world_creation", "return_to_earth", "maternal");
        put("visionary", "mind", "mind", "dream", "manipulation", "psychological", "awe", "frenzy", "placate", "hypnosis", "telepathy", "envision", "story", "discernment", "fantasia");
        put("wheel_of_fortune", "fate", "luck", "misfortune", "blessing", "prophecy", "cycle_of_fate", "calamity", "baptism", "psyche");
        put("justiciar", "order", "prohibition", "law", "authority", "jurisdiction", "verdict", "imprison", "execution", "judgment", "balanc", "confinement", "punishment", "order");
        put("abyss", "desire", "desire", "corrupt", "malice", "defiling", "foulness", "devil", "blood", "fear");
        put("error", "theft", "theft", "gift", "deceit", "deceive", "loophole", "fate", "parasit", "time");
    }

    private static void put(String pathway, String authority, String... keywords) {
        AUTHORITIES.put(pathway, new AbstractMap.SimpleEntry<>(authority, List.of(keywords)));
    }

    public StealAuthorityAbility(String id) {
        super(id, 60);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        canBeShared = false;
        canBeUsedInArtifact = false;
        cannotBeStolen = true;

        hasDynamicCooldown = true;
        dynamicCooldown = new LinkedList<>(List.of(40, 60));

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(30000f, 20000f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("error", 1));
    }

    @Override
    protected float getSpiritualityCost() {
        return 20000;
    }

    @Override
    public void onAbilityUse(Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (ProhibitionHandler.IsInTheftZone(entity.position(), serverLevel, AbilityUtil.getSeqWithArt(entity, this))) return;

        LivingEntity target = AbilityUtil.getTargetEntity(entity, 60, 2);
        if (target == null || !BeyonderData.isBeyonder(target)) {
            message(entity, "ability.lotmcraft.steal_authority.no_target");
            return;
        }

        String pathway = BeyonderData.getPathway(target);
        Map.Entry<String, List<String>> authority = AUTHORITIES.get(pathway);
        if (authority == null) {
            message(entity, "ability.lotmcraft.steal_authority.no_authority");
            return;
        }

        int casterSeq = AbilityUtil.getSeqWithArt(entity, this);
        int targetSeq = BeyonderData.getSequence(target);
        if (AbilityUtil.isTargetSignificantlyStronger(casterSeq, targetSeq) || TheftHandler.doesTheftFail(entity, target, random, this)) {
            message(entity, "ability.lotmcraft.steal_authority.resisted");
            return;
        }

        List<Ability> affected = new ArrayList<>();
        for (Ability ability : LOTMCraft.abilityHandler.getByPathwayAndSequence(pathway, targetSeq)) {
            for (String keyword : authority.getValue()) {
                if (ability.getId().contains(keyword)) {
                    affected.add(ability);
                    break;
                }
            }
        }

        if (affected.isEmpty()) {
            message(entity, "ability.lotmcraft.steal_authority.no_authority");
            return;
        }

        int minutes = Math.min(8, 2 + Math.max(0, targetSeq - casterSeq));
        for (Ability ability : affected) {
            AbilityUtil.sealSpecificTargetAbility(entity, target, ability.getId(), "authority_theft_" + entity.getUUID(), minutes * 60 * 20);
        }

        if (entity instanceof ServerPlayer player) {
            List<Ability> copyable = new ArrayList<>(affected.stream().filter(a -> a.canBeCopied && !a.cannotBeStolen).toList());
            Collections.shuffle(copyable, random);
            for (Ability ability : copyable.subList(0, Math.min(2, copyable.size()))) {
                CopiedAbilityHelper.addAbility(player, new CopiedAbilityComponent.CopiedAbilityData(ability.getId(), "stolen", 2, target.getUUID().toString()));
            }
        }

        FxHandler.playOnEntity(serverLevel, "authority_theft", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.5));
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.6f);
        message(entity, "ability.lotmcraft.steal_authority.success",
                Component.translatable("ability.lotmcraft.steal_authority.authority." + authority.getKey()), target.getName(), minutes);
        if (target instanceof ServerPlayer victim) {
            victim.displayClientMessage(Component.translatable("ability.lotmcraft.steal_authority.victim",
                    Component.translatable("ability.lotmcraft.steal_authority.authority." + authority.getKey())).withColor(ErrorUtil.TIME_COLOR), false);
        }
    }

    private void message(LivingEntity entity, String key, Object... args) {
        AbilityUtil.sendActionBar(entity, Component.translatable(key, args).withColor(ErrorUtil.TIME_COLOR));
    }
}
