package de.jakob.lotm.beyonders.abilities.abyss.contract.handler;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.abyss.contract.ContractData;
import de.jakob.lotm.beyonders.abilities.abyss.contract.ContractItem;
import de.jakob.lotm.beyonders.abilities.abyss.contract.registry.SoulOwnershipRegistry;
import de.jakob.lotm.util.helper.AbilityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.UUID;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class NpcContractHandler {
    public static LivingEntity find(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }
    private static void msg(ServerPlayer player, String message, int color) {AbilityUtil.sendActionBar(player, Component.literal(message).withColor(color));}
    public static boolean canAccept(ServerPlayer player, LivingEntity npc, ContractData data) {
        if (NpcDesire.shouldAggro(npc)) {
            NpcDesire.aggro(npc, player);
            msg(player, "They will not bargain. They attack!", 0xFF124D);
            return false;
        }
        if (!player.getUUID().equals(data.getPartyA()) || data.getPartyB() != null) {
            msg(player, "Only the contract's Party A can offer it to a creature.", 0xFF124D);
            return false;
        }
        boolean isKill = data.isKillContract();
        String kind = data.contractKind();
        if (kind != null && !kind.isEmpty() && !kind.equals("STANDARD") && !isKill) {
            msg(player, "They cannot understand this kind of contract.", 0xFF124D);
            return false;
        }
        boolean soulA = data.termAType() == ContractData.TermType.SOUL && !data.termASoulOwnerIsPartyA();
        boolean soulB = data.termBType() == ContractData.TermType.SOUL && !data.termBSoulOwnerIsPartyA();
        if (soulA == soulB) {
            msg(player, "The contract must claim exactly their soul.", 0xFF124D);
            return false;
        }
        boolean paymentIsA = !soulA;
        ContractData.TermType paymentType = paymentIsA ? data.termAType() : data.termBType();
        if (paymentType != ContractData.TermType.NONE && paymentType != ContractData.TermType.ITEM) {
            msg(player, "They will only accept items for their soul.", 0xFF124D);
            return false;
        }
        if (SoulOwnershipRegistry.isSoulBound(npc.getUUID())) {
            msg(player, "Their soul already belongs to the Abyss.", 0xFF124D);
            return false;
        }
        NpcDesire.Desire desire = NpcDesire.get(npc);
        boolean killDesire = desire != null && desire.kind() == NpcDesire.Kind.KILL_VILLAGER;
        if (isKill && !killDesire) {
            msg(player, "They have no blood to ask for.", 0xFF124D);
            return false;
        }

        if (killDesire) {
            String want = desire.targetName();
            if (!isKill) {
                msg(player, "They want blood: " + desire.demand() + ".", 0xFF124D);
                player.sendSystemMessage(Component.literal("Reason: " + desire.reason()).withColor(0xFFa3070c));
                return false;
            }
            if (!data.isKillerPartyA() || "PLAYER".equals(data.victimEntityType())) {
                msg(player, "You must be the killer, and the victim cannot be a player.", 0xFFa3070c);
                return false;
            }
            if (!data.victimName().equalsIgnoreCase(want)) {
                msg(player, "They want " + want + " dead, not " + data.victimName() + ".", 0xFFa3070c);
                return false;
            }
            if (paymentType != ContractData.TermType.NONE) {
                msg(player, "They want blood, not trinkets.", 0xFFa3070c);
                return false;
            }
        } else if (desire != null) {
            String paymentItem = paymentIsA ? data.termAItem() : data.termBItem();
            int paymentCount = paymentIsA ? data.termACount() : data.termBCount();
            boolean correct = paymentType == ContractData.TermType.ITEM && desire.itemId().equals(paymentItem) && paymentCount >= desire.count();
            if (!correct) {
                msg(player, "They want " + desire.demand() + ".", 0xFF124D);
                player.sendSystemMessage(Component.literal("Reason: " + desire.reason()).withColor(0xFFa3070c));
                return false;
            }
        }
        return true;
    }
    public static void sign(ServerPlayer player, LivingEntity npc, ItemStack stack, ContractData data) {
        NpcDesire.Desire desire = NpcDesire.get(npc);
        Villager killTarget = null;
        if (desire != null && desire.kind() == NpcDesire.Kind.KILL_VILLAGER) {
            killTarget = spawnTarget(player.serverLevel(), player.blockPosition(), desire.profession(), desire.level(), desire.targetName());
            if (killTarget == null) {
                msg(player, "The Abyss cannot find their victim right now. Try elsewhere.", 0xFFa3070c);
                return;
            }
            data.setNpcKillTarget(killTarget.getUUID());
            data.setNpcKillProfession(desire.profession());
            data.setNpcKillLevel(desire.level());
            data.setNpcKillDone(false);
        }
        if (!npc.hasCustomName()) {
            npc.setCustomName(Component.literal(NpcNames.random(npc.getRandom())));
            npc.setCustomNameVisible(true);
        }
        if (data.isKillContract()) data.setKillerName(data.partyAName());
        data.setPartyB(npc.getUUID());
        data.setPartyBName(npc.getName().getString());
        data.setNpcContract(true);
        data.setSoulPathway(NpcDesire.pathwayOf(npc));
        data.setSoulSequence(NpcDesire.seqOf(npc));
        ContractItem.writeData(stack, data);
        msg(player, "They accept.", 0x9932cc);
        if (killTarget != null) player.sendSystemMessage(Component.literal("Kill " + desire.targetName() + ", a level " + desire.level() + " " + NpcDesire.professionName(desire.profession()) + ", somewhere Nearby.").withColor(0xFFa3070c));
        if (process(player, data)) ContractItem.writeData(stack, data);
    }
    public static boolean process(ServerPlayer player, ContractData data) {
        if (!data.isNpcContract() || !data.isActive() || data.areAllTermsFulfilled()) return false;
        if (!player.getUUID().equals(data.getPartyA())) return false;
        boolean soulIsA = data.termAType() == ContractData.TermType.SOUL
                && !data.termASoulOwnerIsPartyA();
        boolean paymentIsA = !soulIsA;
        boolean changed = false;
        // 1. Item payment: the only place items are consumed.
        ContractData.TermType payType = paymentIsA ? data.termAType() : data.termBType();
        boolean payDone = paymentIsA ? data.isTermAFulfilled() : data.isTermBFulfilled();
        if (payType == ContractData.TermType.ITEM && !payDone) {
            Item item;
            try {
                item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(
                        paymentIsA ? data.termAItem() : data.termBItem()));
            } catch (Exception e) {
                return false;
            }
            if (item == Items.AIR) return false;
            int count = paymentIsA ? data.termACount() : data.termBCount();
            if (!ContractFulfillmentHandler.consumeItems(player, item, count)) return false;
            if (paymentIsA) data.setTermAFulfilled(true); else data.setTermBFulfilled(true);
            changed = true;
            payDone = true;
        }
        if (!payDone) return false;
        if (data.npcKillTarget() != null && !data.npcKillDone()) return changed;
        boolean soulDone = soulIsA ? data.termAFulfilled() : data.termBFulfilled();
        if (!soulDone) {
            UUID owner = data.getSoulOwner(soulIsA);
            if (owner == null || SoulOwnershipRegistry.isSoulBound(owner)) return changed;
            SoulOwnershipRegistry.bind(owner);
            if (soulIsA) data.setTermAFulfilled(true); else data.setTermBFulfilled(true);
            msg(player, "The bargain is sealed. Right-click the contract to claim their soul.", 0xFFa3070c);
            changed = true;
        }
        return changed;
    }
    public static Villager spawnTarget(ServerLevel level, BlockPos origin, String profKey, int lvl, String name) {
        VillagerProfession prof = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(profKey));
        BlockState jobState = jobBlock(prof);
        RandomSource r = level.getRandom();
        for (int attempt = 0; attempt < 40; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int dist = 30 + r.nextInt(71);
            BlockPos col = new BlockPos(origin.getX() + (int) (Math.cos(ang) * dist), 0, origin.getZ() + (int) (Math.sin(ang) * dist));
            if (!level.hasChunkAt(col)) continue;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) continue;
            Villager v = EntityType.VILLAGER.create(level);
            if (v == null) return null;
            v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360f, 0f);
            v.setVillagerData(v.getVillagerData().setProfession(prof).setLevel(lvl));
            v.setVillagerXp(VillagerData.getMinXpPerLevel(lvl));
            v.setCustomName(Component.literal(name));
            v.setCustomNameVisible(true);
            v.setPersistenceRequired();
            if (jobState != null) placeJobBlock(level, pos, jobState);
            level.addFreshEntity(v);
            return v;
        }
        return null;
    }
    private static BlockState jobBlock(VillagerProfession prof) {
        for (var holder : BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders().toList()) {
            if (prof.heldJobSite().test(holder)) {
                var states = holder.value().matchingStates();
                if (!states.isEmpty()) return states.iterator().next();
            }
        }
        return null;
    }
    private static void placeJobBlock(ServerLevel level, BlockPos villagerPos, BlockState state) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = villagerPos.relative(d);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                level.setBlock(p, state, 3);
                return;
            }
        }
    }
    @SubscribeEvent
    public static void onNpcDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player || dead.level().isClientSide()) return;
        MinecraftServer server = dead.level().getServer();
        if (server == null) return;
        for (ServerPlayer holder : server.getPlayerList().getPlayers()) {
            var inventory = holder.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!(stack.getItem() instanceof ContractItem)) continue;
                ContractData data = ContractItem.readData(stack);
                if (!data.isNpcContract()) continue;
                // The contracted victim died.
                if (dead.getUUID().equals(data.npcKillTarget()) && !data.npcKillDone()) {
                    boolean byPartyA = event.getSource().getEntity() instanceof ServerPlayer sp && sp.getUUID().equals(data.getPartyA());
                    if (byPartyA) {
                        ServerPlayer sp = (ServerPlayer) event.getSource().getEntity();
                        data.setNpcKillDone(true);
                        data.setKillPaidOut(true);
                        process(sp, data);
                        ContractItem.writeData(stack, data);
                        msg(holder, "The debt of blood is paid.", 0xFFa3070c);
                    } else {
                        Villager repl = spawnTarget(holder.serverLevel(), holder.blockPosition(), data.npcKillProfession(), data.npcKillLevel(),data.victimName());
                        if (repl != null) {
                            data.setNpcKillTarget(repl.getUUID());
                            ContractItem.writeData(stack, data);
                            holder.sendSystemMessage(Component.literal("Their victim is gone. Another waits: " + repl.getName().getString() + ", within 100 blocks.").withColor(0xFFa3070c));
                        }
                    }
                    continue;
                }
                // The NPC party died.
                if (!dead.getUUID().equals(data.getPartyB())) continue;
                // Both terms fulfilled: keep it for sacrifice.
                if (data.areAllTermsFulfilled()) continue;
                SoulOwnershipRegistry.release(dead.getUUID());
                inventory.setItem(i, ItemStack.EMPTY);
                msg(holder, "Your contract dies with its other party.", 0xFF124D);
            }
        }
    }
}