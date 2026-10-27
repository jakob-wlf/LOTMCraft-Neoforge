package de.jakob.lotm.beyonders.abilities.sun.notarization;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.sun.notarization.contract.Contract;
import de.jakob.lotm.gui.custom.notarization.ConfirmMenu;
import de.jakob.lotm.beyonders.abilities.sun.notarization.net.ConfirmData;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public final class ConfirmPrompter {
    private ConfirmPrompter() {}
    private static final Set<String> PROMPTED = new HashSet<>();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        long now = s.overworld().getGameTime();
        if (now % 5 != 0) return;
        Map<UUID, Contract> pending = PendingAccess.pending();
        if (pending.isEmpty()) { PROMPTED.clear(); return; }
        Set<String> live = new HashSet<>();
        for (Contract c : new ArrayList<>(pending.values())) {
            for (UUID u : new UUID[]{c.partyA, c.partyB}) {
                String key = c.id + ":" + u;
                live.add(key);
                if (PROMPTED.contains(key) || !PendingAccess.needsAnswer(c, u)) continue;
                ServerPlayer p = s.getPlayerList().getPlayer(u);
                if (p == null) continue;
                PROMPTED.add(key);
                open(p, c, now);
            }
        }
        PROMPTED.retainAll(live);
    }

    public static void open(ServerPlayer p, Contract c, long now) {
        ConfirmData data = ConfirmData.from(c, now);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ConfirmMenu(id, data, c.id),
                Component.literal("Contract Offer")), data::write);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) { PROMPTED.clear(); }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("contractmenu").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            long now = p.server.overworld().getGameTime();
            for (Contract c : new ArrayList<>(PendingAccess.pending().values()))
                if (PendingAccess.needsAnswer(c, p.getUUID())) { open(p, c, now); return 1; }
            ctx.getSource().sendFailure(Component.literal("No contract is waiting for your answer."));
            return 0;
        }));
    }
}