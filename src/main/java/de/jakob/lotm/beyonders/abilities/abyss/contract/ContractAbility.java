package de.jakob.lotm.beyonders.abilities.abyss.contract;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.contract.handler.ContractScreenHandler;
import de.jakob.lotm.beyonders.abilities.abyss.contract.handler.ContractSoulHandler;
import de.jakob.lotm.beyonders.abilities.abyss.contract.handler.NpcDesire;
import de.jakob.lotm.beyonders.abilities.abyss.contract.registry.PendingReleaseRegistry;
import de.jakob.lotm.beyonders.abilities.abyss.contract.registry.SoulOwnershipRegistry;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ContractAbility extends SelectableAbility {

    public static final Map<UUID, PendingProposal> pendingProposals = new HashMap<>();

    public record PendingProposal(UUID casterUUID, ContractData data) {}
    public ContractAbility(String id) {
        super(id,3);
        canBeUsedByNPC = false;
        canBeCopied = false;
        canBeReplicated = false;
        cannotBeStolen = true;
        canBeUsedInArtifact = true;
        canBeShared = false;
    }
    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("abyss", 6));
    }
    @Override
    protected float getSpiritualityCost() {
        return 100;
    }
    @Override
    protected String[] getAbilityNames() {
        return new String[]{
                "ability.lotmcraft.contract.make_contract",
                "ability.lotmcraft.contract.destroy_unsigned_contract",
                "ability.lotmcraft.contract.check_desire"
        };
    }
    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (abilityIndex == 0) {
            if (level.isClientSide) {ContractScreenHandler.open(entity.isShiftKeyDown());}
            return;
        }
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {return;}
        switch (abilityIndex) {
            case 1 -> destroyUnsignedContract(player);
            case 2 -> checkDesire(player);
        }
    }
    public static void proposeDirect(ServerPlayer caster, ServerPlayer partyA, ServerPlayer partyB, ContractData data) {
        data.setPartyA(partyA.getUUID());
        data.setPartyB(partyB.getUUID());
        data.setPartyAName(partyA.getName().getString());
        data.setPartyBName(partyB.getName().getString());
        pendingProposals.put(partyB.getUUID(), new PendingProposal(partyA.getUUID(), data));
        partyB.sendSystemMessage(Component.literal("══════════════════════════════").withColor(0x9932cc));
        partyB.sendSystemMessage(Component.literal("       CONTRACT REQUEST").withColor(0xFFa3070c));
        partyB.sendSystemMessage(Component.literal(partyA.getName().getString() + " wants you to accept:").withColor(0xFFa3070c));
        partyB.sendSystemMessage(Component.literal(data.describe()).withColor(0xFFa3070c));
        partyB.sendSystemMessage(Component.literal("Type 'accept' or 'decline'").withColor(0xFFa3070c));
        partyB.sendSystemMessage(Component.literal("══════════════════════════════").withColor(0x9932cc));
        if (!caster.getUUID().equals(partyB.getUUID())) {AbilityUtil.sendActionBar(caster, Component.literal("Proposal sent to " + partyB.getName().getString() + ".").withColor(0x9932cc));
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onChatMessage(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!pendingProposals.containsKey(player.getUUID())) {return;}
        String msg = event.getRawText().trim().toLowerCase();
        if (msg.equals("accept")) {
            event.setCanceled(true);
            acceptProposal(player);
        } else if (msg.equals("decline")) {
            event.setCanceled(true);
            declineProposal(player);
        }
    }
    private static void acceptProposal(ServerPlayer target) {
        PendingProposal proposal = pendingProposals.remove(target.getUUID());
        if (proposal == null) {return;}
        ServerPlayer caster = target.server.getPlayerList().getPlayer(proposal.casterUUID());
        if (caster == null) {
            AbilityUtil.sendActionBar(target, Component.literal("The caster is no longer online.").withColor(0xFFff124d));
            return;
        }
        ContractData data = proposal.data();
        data.setPartyA(caster.getUUID());
        data.setPartyB(target.getUUID());
        data.setPartyAName(caster.getName().getString());
        data.setPartyBName(target.getName().getString());
        data.setActive(true);
        data.setCasterSeqAtCreation(BeyonderData.getSequence(caster));
        if (data.isKillContract()) {
            data.setActive(true);
            data.setComplete(false);
            data.setKillPaidOut(false);
            // Do not mark the payment as fulfilled when accepting.
            data.setTermAFulfilled(false);
            data.setTermBFulfilled(false);
            if (data.isKillerPartyA()) {data.setKillerName(data.partyAName());}
            else {data.setKillerName(data.partyBName());}
            var contractItem = ContractItem.create(data);
            if (!caster.getInventory().add(contractItem)) {caster.drop(contractItem, false);}
            AbilityUtil.sendActionBar(caster, Component.literal("Contract formed.").withColor(0xFFa3070c));
            AbilityUtil.sendActionBar(target, Component.literal("Contract formed.").withColor(0xFFa3070c));
            return;
        }
        boolean soulForSoul = data.termAType() == ContractData.TermType.SOUL && data.termBType() == ContractData.TermType.SOUL;
        if (data.hasSoulTermA()) {
            UUID soulOwner = data.getSoulOwner(true);
            if (soulOwner == null) {return;}
            if (SoulOwnershipRegistry.isSoulBound(soulOwner)) {
                ServerPlayer owner = target.server.getPlayerList().getPlayer(soulOwner);
                if (owner != null) {AbilityUtil.sendActionBar(owner, Component.literal("Your Soul Belongs to the Abyss").withColor(0xFFa3070c));}
                return;
            }
            if (!data.isLifeDeathContract()){SoulOwnershipRegistry.bind(soulOwner);}
            if (soulForSoul) {
                data.setTermAFulfilled(true);
                UUID recipient = data.getTermRecipient(false);
                if (recipient != null) {ContractSoulHandler.startDrain(soulOwner, recipient);}
            }
        }
        if (data.hasSoulTermB()) {
            UUID soulOwner = data.getSoulOwner(false);
            if (soulOwner == null) {return;}
            if (SoulOwnershipRegistry.isSoulBound(soulOwner)) {
                ServerPlayer owner = target.server.getPlayerList().getPlayer(soulOwner);
                if (owner != null) {AbilityUtil.sendActionBar(owner, Component.literal("Your Soul Belongs to the Abyss").withColor(0xFFa3070c));}
                return;
            }
            if (!data.isLifeDeathContract())SoulOwnershipRegistry.bind(soulOwner);
            if (soulForSoul) {
                data.setTermBFulfilled(true);
                UUID recipient = data.getTermRecipient(false);
                if (recipient != null) {ContractSoulHandler.startDrain(soulOwner, recipient);}
            }
        }
        if (data.isIou()) {
            UUID soulOwner = caster.getUUID();
            if (SoulOwnershipRegistry.isSoulBound(soulOwner)) {
                AbilityUtil.sendActionBar(caster, Component.literal("Your Soul Belongs to the Abyss").withColor(0xFFa3070c));
                return;
            }
            SoulOwnershipRegistry.bind(soulOwner);
        }
        var contractItem = ContractItem.create(data);
        ServerPlayer recipient = data.isIou() ? target : caster;
        if (!recipient.getInventory().add(contractItem)) {recipient.drop(contractItem, false);}
        AbilityUtil.sendActionBar(caster, Component.literal("Contract formed.").withColor(0x9932cc));
        AbilityUtil.sendActionBar(target, Component.literal("Contract formed.").withColor(0x9932cc));
        if (data.termAType() == ContractData.TermType.RELEASE_SOUL || data.termBType() == ContractData.TermType.RELEASE_SOUL) {
            String releaseTargetName = data.releaseTargetName();
            if (releaseTargetName != null && !releaseTargetName.isBlank()) {
                ServerPlayer releaseTarget = caster.server.getPlayerList().getPlayerByName(releaseTargetName);
                if (releaseTarget != null) {PendingReleaseRegistry.register(releaseTarget.getUUID(), caster.getUUID(), caster.level().getGameTime());
                }
            }
        }
    }
    private static void declineProposal(ServerPlayer target) {
        PendingProposal proposal = pendingProposals.remove(target.getUUID());
        if (proposal == null) {
            return;
        }
        ServerPlayer caster = target.server.getPlayerList().getPlayer(proposal.casterUUID());
        if (caster != null) {
            AbilityUtil.sendActionBar(caster, Component.literal(target.getName().getString() + " declined.").withColor(0xFFa3070c));
        }
    }
    private void destroyUnsignedContract(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ContractItem)) {
            AbilityUtil.sendActionBar(player, Component.literal("You must hold a contract in your main hand.").withColor(0xFFa3070c));
            return;
        }
        ContractData data = ContractItem.readData(stack);
        // Both parties must not have signed yet.
        if (data.hasBothParties()) {
            AbilityUtil.sendActionBar(player, Component.literal("A signed contract cannot be destroyed this way.").withColor(0xFFa3070c));
            return;
        }
        stack.shrink(1);
        AbilityUtil.sendActionBar(player, Component.literal("The unsigned contract dissolves.").withColor(0xFFa3070c));
    }
    private void checkDesire(ServerPlayer player) {
        LivingEntity target = getTargetLivingEntity(player);
        if (target == null || target instanceof Player) {
            AbilityUtil.sendActionBar(player, Component.literal("You must look at a non-player creature.").withColor(0xFFa3070c));
            return;
        }
        NpcDesire.check(player, target);
    }

    private static LivingEntity getTargetLivingEntity(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(8.0));
        AABB searchBox = player.getBoundingBox().expandTowards(player.getLookAngle().scale(8.0)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end, searchBox, target -> target instanceof LivingEntity && target != player && target.isPickable(), 8.0 * 8.0);
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {return living;}
        return null;
    }
}