package de.jakob.lotm.beyonders.abilities.sun.notarization.contract;

import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.event.ContractBreachedEvent;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.event.ContractFulfilledEvent;
import de.jakob.lotm.beyonders.abilities.sun.notarization.net.Candidate;
import de.jakob.lotm.network.packets.toServer.CreateContractPayload;
import de.jakob.lotm.gui.custom.notarization.ContractMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

public final class ContractManager {
    private static final Map<UUID, Contract> PENDING = new LinkedHashMap<>();
    private ContractManager() {}

    public static void reset() { PENDING.clear(); }

    private static long now(MinecraftServer s) { return s.overworld().getGameTime(); }
    private static ServerPlayer player(MinecraftServer s, UUID id) { return s.getPlayerList().getPlayer(id); }
    private static MutableComponent prefix() { return Component.literal("\u2600 ").withStyle(ChatFormatting.GOLD); }

    private static void fail(ServerPlayer p, String key) {
        p.sendSystemMessage(prefix().append(Component.translatable(key).withStyle(ChatFormatting.RED)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 1f);
    }

    private static void tellAll(MinecraftServer s, Contract c, Component m) {
        for (UUID u : new LinkedHashSet<>(List.of(c.partyA, c.partyB, c.notary))) {
            ServerPlayer p = player(s, u);
            if (p != null) p.sendSystemMessage(m);
        }
    }


    public static boolean isSelectable(ServerPlayer caster, ServerPlayer t) {
        if (t == caster) return true;
        return t.isAlive() && !t.isSpectator() && t.level() == caster.level()
                && t.distanceToSqr(caster) <= ContractRules.SELECT_RANGE * ContractRules.SELECT_RANGE;
    }

    public static List<Candidate> candidates(ServerPlayer caster) {
        List<Candidate> out = new ArrayList<>();
        for (ServerPlayer p : caster.server.getPlayerList().getPlayers())
            if (isSelectable(caster, p))
                out.add(new Candidate(p.getUUID(), p.getGameProfile().getName(), SequenceApi.sequenceOf(p), p.getInventory().items));
        return out;
    }

    private static boolean hasPending(UUID u) {
        for (Contract c : PENDING.values()) if (c.involves(u) || c.notary.equals(u)) return true;
        return false;
    }


    public static void handleCreateRequest(ServerPlayer caster, CreateContractPayload req) {
        MinecraftServer server = caster.server;
        long now = now(server);
        if (!(caster.containerMenu instanceof ContractMenu)) return;

        ContractAction action = ContractAction.byId(req.actionId());
        ServerPlayer a = player(server, req.partyA()), b = player(server, req.partyB());
        if (action == null || a == null || b == null) { fail(caster, "contractnotary.fail.invalid"); return; }
        if (a == b) { fail(caster, "contractnotary.fail.same"); return; }
        if (!isSelectable(caster, a) || !isSelectable(caster, b)) { fail(caster, "contractnotary.fail.range"); return; }
        if (!action.allows(req.seconds())) { fail(caster, "contractnotary.fail.invalid"); return; }

        ResourceLocation itemId = null;
        if (action == ContractAction.DROP_ITEM) {
            itemId = ResourceLocation.tryParse(req.item());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)
                    || BuiltInRegistries.ITEM.get(itemId) == Items.AIR) { fail(caster, "contractnotary.fail.item"); return; }
        }
        if (hasPending(caster.getUUID()) || hasPending(a.getUUID()) || hasPending(b.getUUID())) {
            fail(caster, "contractnotary.fail.busy"); return;
        }
        for (Contract c : ContractSavedData.get(server).active.values())
            if (c.partyA.equals(a.getUUID()) && c.partyB.equals(b.getUUID()) && c.action == action) {
                fail(caster, "contractnotary.fail.duplicate"); return;
            }

        Contract c = new Contract(UUID.randomUUID(), a.getUUID(), a.getGameProfile().getName(), b.getUUID(),
                b.getGameProfile().getName(), caster.getUUID(), caster.getGameProfile().getName(), action, itemId, req.seconds());
        c.seqA = SequenceApi.sequenceOf(a);
        c.seqB = SequenceApi.sequenceOf(b);
        c.confirmDeadline = now + ContractRules.CONFIRM_TICKS;
        PENDING.put(c.id, c);
        caster.closeContainer();

        Component header = prefix().append(Component.translatable("contractnotary.msg.proposed", c.notaryName).withStyle(ChatFormatting.YELLOW));
        Component body = Component.literal("  ").append(c.sentence());
        for (UUID u : new LinkedHashSet<>(List.of(c.partyA, c.partyB, c.notary))) {
            ServerPlayer p = player(server, u);
            if (p == null) continue;
            p.sendSystemMessage(header); p.sendSystemMessage(body);
        }
        ContractFx.proposed(a);
        ContractFx.proposed(b);
    }


    private static Contract pendingFor(UUID u) {
        for (Contract c : PENDING.values()) if (c.involves(u)) return c;
        return null;
    }

    public static void onChat(ServerPlayer p, String raw) {
        String t = raw.trim();
        boolean yes = t.equalsIgnoreCase("yes"), no = t.equalsIgnoreCase("no");
        if (!yes && !no) return;
        Contract c = pendingFor(p.getUUID());
        if (c == null) return;
        MinecraftServer s = p.server;
        if (no) {
            PENDING.remove(c.id);
            tellAll(s, c, prefix().append(Component.translatable("contractnotary.msg.declined", p.getGameProfile().getName()).withStyle(ChatFormatting.RED)));
            ContractFx.cancelled(p);
            return;
        }
        if (c.partyA.equals(p.getUUID())) c.confirmedA = true; else c.confirmedB = true;
        tellAll(s, c, prefix().append(Component.translatable("contractnotary.msg.accepted", p.getGameProfile().getName()).withStyle(ChatFormatting.GREEN)));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.3f);
        if (c.confirmedA && c.confirmedB) activate(s, c);
    }

    private static void activate(MinecraftServer s, Contract c) {
        PENDING.remove(c.id);
        long now = now(s);
        c.endTick = now + c.seconds * 20L;
        c.graceUntil = now + ContractRules.NEAR_GRACE_TICKS;
        ServerPlayer a = player(s, c.partyA), b = player(s, c.partyB);
        if (a != null) c.seqA = SequenceApi.sequenceOf(a);
        if (b != null) c.seqB = SequenceApi.sequenceOf(b);
        ContractSavedData data = ContractSavedData.get(s);
        data.active.put(c.id, c);
        data.setDirty();
        tellAll(s, c, prefix().append(Component.translatable("contractnotary.msg.sealed").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        tellAll(s, c, Component.literal("  ").append(c.sentence()));
        if (c.action == ContractAction.NOT_BE_NEAR)
            tellAll(s, c, Component.translatable("contractnotary.msg.grace").withStyle(ChatFormatting.GRAY));
        ContractFx.sealed(a, b);
    }


    public static void tick(MinecraftServer s) {
        long now = now(s);
        if (!PENDING.isEmpty()) {
            for (Contract c : new ArrayList<>(PENDING.values())) {
                if (now > c.confirmDeadline) {
                    PENDING.remove(c.id);
                    tellAll(s, c, prefix().append(Component.translatable("contractnotary.msg.expired").withStyle(ChatFormatting.RED)));
                } else if (now % 20 == 0) {
                    int left = (int) ((c.confirmDeadline - now) / 20);
                    for (UUID u : List.of(c.partyA, c.partyB)) {
                        boolean done = u.equals(c.partyA) ? c.confirmedA : c.confirmedB;
                        ServerPlayer p = player(s, u);
                        if (p != null && !done)
                            p.displayClientMessage(Component.translatable("contractnotary.msg.confirm_bar", left).withStyle(ChatFormatting.GOLD), true);
                    }
                }
            }
        }
        if (now % 5 != 0) return;

        ContractSavedData data = ContractSavedData.get(s);
        if (data.active.isEmpty()) return;
        double nr = ContractRules.NEAR_RADIUS * ContractRules.NEAR_RADIUS;
        for (Contract c : new ArrayList<>(data.active.values())) {
            ServerPlayer a = player(s, c.partyA), b = player(s, c.partyB);
            if (c.action == ContractAction.NOT_BE_NEAR && now >= c.graceUntil && a != null && b != null
                    && a.level() == b.level() && a.distanceToSqr(b) < nr) {
                breach(s, c, Component.translatable("contractnotary.reason.near")); continue;
            }
            if (now >= c.endTick) {
                if (c.action.isDeadline()) breach(s, c, Component.translatable("contractnotary.reason.timeout"));
                else fulfill(s, c);
                continue;
            }
            if (now % 40 == 0 && a != null) ContractFx.aura(a);
            if (now % 20 == 0 && a != null) {
                long left = (c.endTick - now + 19) / 20;
                if (c.action.isDeadline() || left <= 60 || left % 10 == 0)
                    a.displayClientMessage(Component.translatable("contractnotary.msg.remaining",
                            ContractRules.formatDuration((int) Math.max(1, left))).withStyle(ChatFormatting.GOLD), true);
            }
        }
    }


    public static void onToss(ServerPlayer p, ItemEntity drop) {
        MinecraftServer s = p.server;
        ContractSavedData data = ContractSavedData.get(s);
        if (data.active.isEmpty()) return;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(drop.getItem().getItem());
        long now = now(s);
        double r = ContractRules.DROP_RADIUS * ContractRules.DROP_RADIUS;
        for (Contract c : new ArrayList<>(data.active.values())) {
            if (c.action != ContractAction.DROP_ITEM || !c.partyA.equals(p.getUUID()) || !key.equals(c.item) || now >= c.endTick) continue;
            ServerPlayer b = player(s, c.partyB);
            if (b != null && b.level() == p.level() && b.distanceToSqr(drop) <= r) { fulfill(s, c); return; }
        }
    }

    public static void onHarm(ServerPlayer victim, Entity attacker, boolean lethal, float amount) {
        if (!(attacker instanceof ServerPlayer atk) || atk == victim) return;
        MinecraftServer s = victim.server;
        ContractSavedData data = ContractSavedData.get(s);
        if (data.active.isEmpty()) return;
        for (Contract c : new ArrayList<>(data.active.values())) {
            if (!c.partyA.equals(atk.getUUID()) || !c.partyB.equals(victim.getUUID())) continue;
            if (c.action == ContractAction.NOT_KILL && lethal)
                breach(s, c, Component.translatable("contractnotary.reason.killed"));
            else if (c.action == ContractAction.NOT_DAMAGE && (lethal || amount > 0))
                breach(s, c, Component.translatable("contractnotary.reason.harmed"));
        }
    }

    public static void onLogout(ServerPlayer p) {
        for (Contract c : new ArrayList<>(PENDING.values()))
            if (c.involves(p.getUUID()) || c.notary.equals(p.getUUID())) {
                PENDING.remove(c.id);
                tellAll(p.server, c, prefix().append(Component.translatable("contractnotary.msg.expired").withStyle(ChatFormatting.RED)));
            }
    }

    public static void onLogin(ServerPlayer p) {
        int debt = ContractSavedData.get(p.server).takeDebt(p.getUUID());
        if (debt > 0) BreachPenalty.apply(p, debt, 0);
    }


    private static void fulfill(MinecraftServer s, Contract c) {
        ContractSavedData data = ContractSavedData.get(s);
        if (data.active.remove(c.id) == null) return;
        data.setDirty();
        NeoForge.EVENT_BUS.post(new ContractFulfilledEvent(c));
        tellAll(s, c, prefix().append(Component.translatable("contractnotary.msg.fulfilled", c.nameA).withStyle(ChatFormatting.GREEN)));
        for (UUID u : List.of(c.partyA, c.partyB)) { ServerPlayer p = player(s, u); if (p != null) ContractFx.fulfilled(p); }
    }

    private static void breach(MinecraftServer s, Contract c, Component reason) {
        ContractSavedData data = ContractSavedData.get(s);
        if (data.active.remove(c.id) == null) return;
        data.setDirty();
        ServerPlayer violator = player(s, c.partyA), notary = player(s, c.notary);
        int sa = SequenceApi.sequenceOf(s, c.partyA, c.seqA), sb = SequenceApi.sequenceOf(s, c.partyB, c.seqB);
        int level = BreachPenalty.level(sa, sb);
        boolean empower = !c.notary.equals(c.partyA);
        NeoForge.EVENT_BUS.post(new ContractBreachedEvent(c, level, empower));

        tellAll(s, c, prefix().append(Component.translatable("contractnotary.msg.breached", c.nameA, reason).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        if (violator != null) BreachPenalty.apply(violator, level, sa - sb);
        else data.addDebt(c.partyA, level); // applied on next login
        if (empower && notary != null) BreachPenalty.empower(notary, level);
    }
}
