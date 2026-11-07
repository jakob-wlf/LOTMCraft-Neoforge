package de.jakob.lotm.beyonders.abilities.justiciar;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.abyss.TheCorroderAbility;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityUsedEvent;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.core.ToggleAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.DeprivationHelper;
import de.jakob.lotm.util.helper.DeprivationVisuals;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Map;

public class DeprivationAbility extends Ability {
    public DeprivationAbility(String id) {
        super(id, 15f, "deprivation");
        interactionRadius = 48;
        hasOptimalDistance = false;
        postsUsedAbilityEventManually = true;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() { return Map.of("justiciar", 4); }

    @Override
    protected float getSpiritualityCost() { return 800; }

    @Override
    public void onAbilityUse(Level level, LivingEntity caster) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        LivingEntity target = AbilityUtil.getTargetEntity(caster, 48, 1.5f, true);
        if (target == null || !target.isAlive() || !BeyonderData.isBeyonder(target)) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.deprivation.no_target")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (TheCorroderAbility.isCorroding(target.getUUID())) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.deprivation.resisted")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        var disabled = target.getData(ModAttachments.DISABLED_ABILITIES_COMPONENT);
        var candidates = new ArrayList<Power>();
        for (Ability ability : LOTMCraft.abilityHandler.getAllAbilitiesForEntity(target)) {
            if (ability.canAlwaysBeUsed || DeprivationHelper.isProtected(ability) || LOTMCraft.abilityHandler.isDisabled(ability)
                    || disabled.isSpecificAbilityDisabled(ability.getId())) continue;
            if (ability instanceof SelectableAbility selectable) {
                for (String mode : selectable.getSequenceDependantAbilityNames(BeyonderData.getSequence(target))) {
                    if (!DeprivationHelper.isDisabled(target, ability, mode)) {
                        Power power = new Power(ability, mode);
                        if (!candidates.contains(power)) candidates.add(power);
                    }
                }
            } else if (!DeprivationHelper.isDisabled(target, ability, "")) {
                candidates.add(new Power(ability, ""));
            }
        }
        if (candidates.isEmpty()) {
            AbilityUtil.sendActionBar(caster, Component.translatable("ability.lotmcraft.deprivation.no_power")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Power power = candidates.get(random.nextInt(candidates.size()));
        int sequence = AbilityUtil.getSeqWithArt(caster, this);
        double resistance = AbilityUtil.getSequenceResistanceFactor(sequence, BeyonderData.getSequence(target));
        int baseSeconds = sequence <= 3 ? 60 : 30;
        int duration = Math.max(1, (int) Math.round(baseSeconds * 20 * (1 - resistance)));
        DeprivationHelper.disable(target, power.ability, power.mode, duration);
        if (power.ability instanceof ToggleAbility toggle) toggle.stopForEntity(target);

        Component name = power.mode.isEmpty() ? power.ability.getName() : Component.translatable(power.mode);
        String seconds = String.format(java.util.Locale.ROOT, "%.1f", duration / 20.0);
        Component casterNotice = Component.translatable("ability.lotmcraft.deprivation.applied",
                name, target.getDisplayName(), seconds).withStyle(ChatFormatting.GOLD);
        Component targetNotice = Component.translatable("ability.lotmcraft.deprivation.received",
                name, seconds).withStyle(ChatFormatting.RED);
        caster.sendSystemMessage(casterNotice);
        target.sendSystemMessage(targetNotice);
        AbilityUtil.sendActionBar(caster, casterNotice);
        AbilityUtil.sendActionBar(target, targetNotice);
        DeprivationVisuals.activate(serverLevel, caster, target);
        NeoForge.EVENT_BUS.post(new AbilityUsedEvent(serverLevel, target.position(), caster, this,
                interactionFlags, 3, 20 * 2));
    }

    private record Power(Ability ability, String mode) {}
}
