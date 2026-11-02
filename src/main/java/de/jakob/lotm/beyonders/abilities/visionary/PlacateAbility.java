package de.jakob.lotm.beyonders.abilities.visionary;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.VirtualPersonaComponent;
import de.jakob.lotm.beyonders.abilities.abyss.MaliceSeed.MaliceSeedAbility;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.visionary.handlers.VisionaryHandler;
import de.jakob.lotm.beyonders.abilities.visionary.prophecy.Prophecy;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PlacateAbility extends SelectableAbility {
    public PlacateAbility(String id) {
        super(id, 5, "morale_boost");
        interactionRadius = 18;
        interactionCacheTicks = 20 * 5;
        canAlwaysBeUsed = true;

        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(1200f, 800f, 470f, 280f, 260f, 160f, 134f, 100f));
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("visionary", 7));
    }

    @Override
    public float getSpiritualityCost() {
        return 50;
    }

    @Override
    public String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.placate.self",
                "ability.lotmcraft.placate.others",
                "ability.lotmcraft.placate.check_cue",
                "ability.lotmcraft.placate.remove_cue"
        };
    }

    @Override
    public void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if(!(entity instanceof Player))
            abilityIndex = 0;

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this);
        String path = BeyonderData.getPathway(entity);

        if(VisionaryHandler.shouldBeAffectedWithMindWorldSeal(entitySeq, path)){
            AbilityUtil.sendActionBar(entity,
                    Component.translatable("ability.lotmcraft.mind_world_authority_ability.is_sealed")
                            .withColor(0xFFff124d));
            return;
        }

        switch (abilityIndex) {
            case 0 -> placateYourself(level, entity);
            case 1 -> placateOthers(level, entity);
            case 2 -> checkCue(level, entity);
            case 3 -> removeCue(level, entity);
        }
    }

    private void removeCue(Level level, LivingEntity entity){
        if(level.isClientSide)
            return;
        if (!(entity instanceof ServerPlayer player)) return;

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this) +
                (BeyonderData.getPathway(entity).equals("visionary")? 0 : 2);

        var targetPlayer = AbilityUtil.getTargetEntity(entity, baseDistance,
                1f, true, true) == null ?
                entity :  AbilityUtil.getTargetEntity(
                        entity, 40, 1f, true, true);
        if(!(targetPlayer instanceof ServerPlayer)) targetPlayer = entity;

        if(VisionaryHandler.shouldFailAndTrigger(entitySeq, entity, targetPlayer, this)){
            return;
        }

        List<Prophecy> all = new LinkedList<>(prophecies(targetPlayer));

        List<Prophecy> matching = all.stream()
                .filter(obj -> {
                    int seq = casterSequence(obj);
                    return entitySeq <= obj.trigger().getRequiredSeq()
                            && entitySeq <= obj.trigger().getActionRequiredSeq()
                            && entitySeq <= seq;
                })
                .toList();

        if (matching.isEmpty()) {
            AbilityUtil.sendActionBar(entity,
                    Component.translatable("ability.lotmcraft.placate.check_cue.not_detected"));
            return;
        }

        int amount = getCuesRemovedPerSeq(entitySeq);

        for (int i = 0; i < amount && i < matching.size(); i++) {
            if(all.isEmpty()) break;

            Prophecy toRemove = matching.get(i);
            all.remove(toRemove);
        }

        BeyonderData.playerMap.setProphecies(targetPlayer.getUUID(), all);
    }

    private static List<Prophecy> prophecies(LivingEntity entity) {
        return BeyonderData.playerMap.get(entity).<List<Prophecy>>map(data -> data.prophecies()).orElse(List.of());
    }

    // A cue whose caster left the map counts as cast by the weakest possible caster
    private static int casterSequence(Prophecy prophecy) {
        return BeyonderData.playerMap.get(prophecy.casterId()).map(data -> data.sequence()).orElse(LOTMCraft.NON_BEYONDER_SEQ);
    }

    private static int getCuesRemovedPerSeq(int seq){
        return switch (seq){
            case 7 -> 1;
            case 6 -> 2;
            case 5 -> 3;
            case 4,3 -> 10;
            case 2 -> 20;
            case 1 -> 30;
            case 0 -> 60;
            default -> 0;
        };
    }

    private static float getSanityPerSeq(int seq){
        return switch (seq){
            case 7 -> 0.15f;
            case 6, 5 -> 0.17f;
            case 4 -> 0.2f;
            case 3 -> 0.22f;
            case 2 -> 0.25f;
            case 1 -> 0.27f;
            case 0 -> 0.35f;
            default -> 0.0f;
        };
    }

    private void checkCue(Level level, LivingEntity entity){
        if(level.isClientSide)
            return;
        if (!(entity instanceof ServerPlayer player)) return;

        var target = AbilityUtil.getTargetEntity(entity, baseDistance, 1f, true, true);

        int entitySeq = AbilityUtil.getSeqWithArt(entity, this) +
                (BeyonderData.getPathway(entity).equals("visionary")? 0 : 2);

        List<Prophecy> list;
        if(target instanceof ServerPlayer targetPlayer){
            list = prophecies(targetPlayer).stream().filter(obj -> {
                int seq = casterSequence(obj);
                if(entitySeq <= obj.trigger().getRequiredSeq() && entitySeq <= obj.trigger().getActionRequiredSeq()){
                    return entitySeq <= seq;
                }

                return false;
            }).toList();
        }
        else{
            list = prophecies(entity).stream().filter(obj -> {
                int seq = casterSequence(obj);
                if(entitySeq <= obj.trigger().getRequiredSeq() && entitySeq <= obj.trigger().getActionRequiredSeq()){
                    if(entitySeq <= seq)
                        return true;
                }

                return false;
            }).toList();
        }

        if(list.isEmpty()){
            AbilityUtil.sendActionBar(entity, Component.translatable("ability.lotmcraft.placate.check_cue.not_detected"));
            return;
        }

        for (var obj : list){
            player.sendSystemMessage(Component.literal(
                    "Trigger: " + obj.trigger().getType()
                    + ", action: " + obj.trigger().getActionType()
                    + " set by " + BeyonderData.playerMap.get(obj.casterId()).map(data -> data.trueName()).orElse("?")
            ));
        }
    }

    private void placateOthers(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        for(LivingEntity e : AbilityUtil.getNearbyEntities(entity, (ServerLevel) level, entity.position(), 18 , false, true)) {
            placateEntity(entity, e);
        }
    }

    private void placateYourself(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;

        placateEntity(entity, entity);
    }

    private void placateEntity(LivingEntity caster, LivingEntity entity) {
        int entitySeq = AbilityUtil.getSeqWithArt(entity, this) +
                (BeyonderData.getPathway(entity).equals("visionary")? 0 : 2);

        if(VisionaryHandler.shouldFailAndTrigger(entitySeq, caster, entity, this)){
            return;
        }

        entity.getData(ModAttachments.SANITY_COMPONENT).increaseSanityAndSync(getSanityPerSeq(entitySeq), entity);
        entity.removeEffect(ModEffects.LOOSING_CONTROL);

        var mentalPlague = entity.getData(ModAttachments.MENTAL_PLAGUE.get());
        if(mentalPlague.hasMentalPlague()){
            if(!AbilityUtil.isTargetSignificantlyStronger(entitySeq, mentalPlague.getSequence())){
                if(entitySeq > mentalPlague.getSequence()){
                    int stage = mentalPlague.getStage();

                    mentalPlague.setStage(--stage);
                    if(stage <= 0){
                        mentalPlague.reset();
                    }
                }
                else{
                    mentalPlague.reset();
                }
            }
        }

        if(entity instanceof ServerPlayer targetPlayer){
            var personas = targetPlayer.getData(ModAttachments.VIRTUAL_PERSONAS.get());
            personas.damageAffectedBy(VirtualPersonaComponent.getMaxHealthPerSeq(entitySeq),
                    (ServerLevel) targetPlayer.level(), targetPlayer.getName().getString(), entitySeq);
        }
        int casterEffSeq = AbilityUtil.getSeqWithArt(caster, this) + ("visionary".equals(BeyonderData.getPathway(caster)) ? 0 : 2);
        MaliceSeedAbility.purify(caster, entity, casterEffSeq, 2);

//        if(caster instanceof ServerPlayer player)
//            RingEffectManager.createRingForPlayer(entity.getEyePosition().subtract(0, .4, 0), 2, 60, 255 / 255f, 211 / 255f, 92 / 255f, 1, .5f, .75f, (ServerLevel) caster.level(), player);
    }

    @Override
    public boolean shouldUseAbility(LivingEntity entity) {
        return entity.hasEffect(ModEffects.LOOSING_CONTROL);
    }
}
