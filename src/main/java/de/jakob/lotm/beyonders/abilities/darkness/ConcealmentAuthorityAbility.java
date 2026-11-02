package de.jakob.lotm.beyonders.abilities.darkness;

import com.mojang.datafixers.util.Pair;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.events.HonorificNamesEventHandler;
import de.jakob.lotm.util.playerMap.PendingPrayer;
import de.jakob.lotm.beyonders.abilities.common.SpiritVisionAbility;
import de.jakob.lotm.beyonders.abilities.visionary.passives.MetaAwarenessAbility;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.effect.ModEffects;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toClient.OpenDivinationMisleadScreenPacket;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID)
public class ConcealmentAuthorityAbility extends SelectableAbility {
    private static final int NEAR = 64;
    private static final Set<UUID> hiddenNames = new HashSet<>();
    private static final String BLOCKS = "█████";
    private static final List<Rule> rules = new ArrayList<>();
    private static final Map<UUID, Long> told = new HashMap<>();
    private static final Map<UUID, Long> prayed = new HashMap<>();

    public ConcealmentAuthorityAbility(String id) {
        super(id, 5);
        canBeUsedByNPC = false;
        canBeShared = false;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of("darkness", 0));
    }

    @Override
    protected float getSpiritualityCost() {
        return 8000;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.concealment.surroundings",
                "ability.lotmcraft.concealment.enter_concealed_area",
                "ability.lotmcraft.concealment.conceal_thoughts",
                "ability.lotmcraft.concealment_authority.divination_mislead",
                "ability.lotmcraft.concealment_authority.conceal_name"
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (!(entity instanceof Player)) abilityIndex = 0;
        switch (abilityIndex) {
            case 0, 1, 2 -> {
                if (LOTMCraft.abilityHandler.getById("concealment_ability") instanceof ConcealmentAbility concealability) {
                    if (abilityIndex == 0) concealability.concealSurroundings(level, entity);
                    else if (abilityIndex == 1) concealability.enterConcealedArea(level, entity);
                    else concealability.concealThoughts(level, entity);
                }
            }
            case 3 -> openMislead(entity);
            case 4 -> concealName(entity);
        }
    }

    public static boolean undivinable(LivingEntity entity) {
        return entity != null && "darkness".equals(BeyonderData.getPathway(entity)) && BeyonderData.getSequence(entity) == 0;
    }

    public static boolean nearAuthority(ServerLevel level, BlockPos pos, ServerPlayer diviner) {
        for (ServerPlayer player : level.players()) {
            if (player == diviner) continue;
            if (undivinable(player) && player.blockPosition().distSqr(pos) <= (long) NEAR * NEAR) return true;
        }
        return false;
    }

    public static String misleadPlayer(ServerPlayer diviner, ServerPlayer target) {
        if (target == null || diviner.getServer() == null) return null;
        String name = target.getGameProfile().getName();
        for (Rule rule : rules) {
            if (rule.place || !rule.label.equalsIgnoreCase(name)) continue;
            if (!undivinable(diviner.getServer().getPlayerList().getPlayer(rule.caster))) continue;
            if (rule.spirit) {
                forceSpiritVision(diviner);
                return "§5Spirit vision swallows the divination.";
            }
            return "§5You sense §d" + rule.outcome + "§5";
        }
        return null;
    }

    public static String misleadPlace(ServerPlayer diviner, BlockPos pos) {
        if (diviner.getServer() == null) return null;
        ResourceKey<Level> dimension = diviner.serverLevel().dimension();
        for (Rule rule : rules) {
            if (!rule.place || rule.dimension == null || !rule.dimension.equals(dimension)) continue;
            if (!undivinable(diviner.getServer().getPlayerList().getPlayer(rule.caster))) continue;
            double dx = pos.getX() - rule.x;
            double dz = pos.getZ() - rule.z;
            if (dx * dx + dz * dz > (long) NEAR * NEAR) continue;
            if (rule.spirit) {
                forceSpiritVision(diviner);
                return "§5Spirit vision swallows the divination.";
            }
            return "§5You sense §d" + rule.outcome + "§5";
        }
        return null;
    }

    public static boolean dreamMislead(ServerLevel level, BlockPos pos) {
        for (Rule rule : rules) {
            if (!rule.spirit || !rule.place || rule.dimension == null || !rule.dimension.equals(level.dimension())) continue;
            if (level.getServer().getPlayerList().getPlayer(rule.caster) == null) continue;
            if (!undivinable(level.getServer().getPlayerList().getPlayer(rule.caster))) continue;
            double dx = pos.getX() - rule.x;
            double dz = pos.getZ() - rule.z;
            if (dx * dx + dz * dz <= (long) NEAR * NEAR) return true;
        }
        return false;
    }

    public static void forceSpiritVision(ServerPlayer player) {
        Ability ability = LOTMCraft.abilityHandler.getById("spirit_vision_ability");
        if (ability instanceof SpiritVisionAbility vision && !vision.isActiveForEntity(player)) {
            vision.onAbilityUse(player.serverLevel(), player);
        }
        var sanity = player.getData(ModAttachments.SANITY_COMPONENT);
        if (BeyonderData.getSequence(player) == 0) sanity.setSanityAndSync(sanity.getSanity() * 0.4f, player);
        else sanity.setSanityAndSync(0f, player);
        player.addEffect(new MobEffectInstance(ModEffects.LOOSING_CONTROL, 20 * 15, 1, false, true));
    }

    public static void submitRule(ServerPlayer player, String target, String outcome) {
        if (!undivinable(player) || target == null || outcome == null) return;
        String trimmed = target.trim();
        String result = outcome.trim();
        if (trimmed.isEmpty() || result.isEmpty()) return;
        boolean spirit = result.equalsIgnoreCase("spirit");
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 2) {
            try {
                double x = Double.parseDouble(parts[0]);
                double z = Double.parseDouble(parts[1]);
                rules.add(new Rule(player.getUUID(), trimmed, true, player.serverLevel().dimension(), x, z, result, spirit));
                bar(player, "The divination is bent.");
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        rules.add(new Rule(player.getUUID(), trimmed, false, null, 0, 0, result, spirit));
        bar(player, "The divination is bent.");
    }

    public static void releaseRule(ServerPlayer player, int index) {
        int seen = 0;
        for (int i = 0; i < rules.size(); i++) {
            if (!rules.get(i).caster.equals(player.getUUID())) continue;
            if (seen == index) {
                rules.remove(i);
                bar(player, "That mislead fades.");
                return;
            }
            seen++;
        }
    }

    public static SignText censorSign(SignBlockEntity sign, SignText text) {
        if (sign.getLevel() == null || sign.getLevel().isClientSide() || !(sign.getLevel() instanceof ServerLevel level)) return text;
        if (hiddenNames.isEmpty()) return text;
        SignText result = text;
        ServerPlayer writer = nearest(level, sign.getBlockPos());
        for (ServerPlayer owner : level.getServer().getPlayerList().getPlayers()) {
            if (!hiddenNames.contains(owner.getUUID()) || owner.equals(writer)) continue;
            String name = owner.getName().getString();
            boolean hit = false;
            for (int line = 0; line < 4; line++) {
                String raw = result.getMessage(line, false).getString();
                String hidden = hide(raw, name);
                if (hidden.equals(raw)) continue;
                result = result.setMessage(line, Component.literal(hidden));
                hit = true;
            }
            if (hit) notify(owner, writer);
        }
        return result;
    }

    private void openMislead(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        List<String> lines = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.caster.equals(player.getUUID())) lines.add(rule.label + " -> " + rule.outcome);
        }
        PacketHandler.sendToPlayer(player, new OpenDivinationMisleadScreenPacket(lines));
    }

    private void concealName(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (hiddenNames.remove(player.getUUID())) bar(player, "The name can be written again.");
        else {
            hiddenNames.add(player.getUUID());
            bar(player, "The name will not be written.");
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (hiddenNames.isEmpty()) return;
        String raw = event.getMessage().getString();
        String next = raw;
        ServerPlayer sender = event.getPlayer();
        for (ServerPlayer owner : sender.server.getPlayerList().getPlayers()) {
            if (!hiddenNames.contains(owner.getUUID()) || owner.getUUID().equals(sender.getUUID())) continue;
            String hidden = hide(next, owner.getName().getString());
            if (hidden.equals(next)) continue;
            next = hidden;
            notify(owner, sender);
        }
        if (!next.equals(raw)) event.setMessage(Component.literal(next));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (hiddenNames.isEmpty() || !(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            censorStack(player.getInventory().getItem(i), player);
        }
    }

    private static void censorStack(ItemStack stack, ServerPlayer writer) {
        if (stack.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (content == null) return;
            List<Filterable<String>> pages = new ArrayList<>();
            boolean changed = false;
            for (Filterable<String> page : content.pages()) {
                String stripped = censor(page.raw(), writer);
                if (!stripped.equals(page.raw())) changed = true;
                pages.add(Filterable.passThrough(stripped));
            }
            if (changed) stack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
        } else if (stack.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content == null) return;
            List<Filterable<Component>> pages = new ArrayList<>();
            boolean changed = false;
            for (Filterable<Component> page : content.pages()) {
                String raw = page.raw().getString();
                String stripped = censor(raw, writer);
                if (!stripped.equals(raw)) changed = true;
                pages.add(Filterable.passThrough(Component.literal(stripped)));
            }
            if (changed) {
                stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                        content.title(), content.author(), content.generation(), pages, content.resolved()));
            }
        }
    }

    private static String censor(String text, ServerPlayer writer) {
        String result = text;
        for (ServerPlayer owner : writer.server.getPlayerList().getPlayers()) {
            if (!hiddenNames.contains(owner.getUUID()) || owner.getUUID().equals(writer.getUUID())) continue;
            String hidden = hide(result, owner.getName().getString());
            if (hidden.equals(result)) continue;
            result = hidden;
            notify(owner, writer);
        }
        return result;
    }

    private static String hide(String text, String username) {
        if (text == null || username == null || username.isEmpty()) return text;
        boolean match = false;
        for (int i = 0; i < username.length(); i++) {
            for (int j = i + 2; j <= username.length(); j++) {
                String part = username.substring(i, j);
                if (MetaAwarenessAbility.COMMON_WORDS.contains(part)) continue;
                if (text.contains(part)) {
                    match = true;
                    break;
                }
            }
            if (match) break;
        }
        boolean honorific = BeyonderData.playerMap != null && BeyonderData.playerMap.containsHonorificNameWithLine(text);
        if (!match && !text.toLowerCase().contains(username.toLowerCase()) && !honorific) return text;
        if (honorific) return BLOCKS;
        String result = text.replaceAll("(?i)" + Pattern.quote(username), BLOCKS);
        for (int length = username.length(); length >= 2; length--) {
            for (int start = 0; start + length <= username.length(); start++) {
                String part = username.substring(start, start + length);
                if (MetaAwarenessAbility.COMMON_WORDS.contains(part)) continue;
                result = result.replace(part, BLOCKS);
            }
        }
        return result;
    }

    private static ServerPlayer nearest(ServerLevel level, BlockPos pos) {
        ServerPlayer closest = null;
        double best = 64;
        for (ServerPlayer player : level.players()) {
            double distance = player.blockPosition().distSqr(pos);
            if (distance < best) {
                best = distance;
                closest = player;
            }
        }
        return closest;
    }

    private static void notify(ServerPlayer owner, ServerPlayer writer) {
        pray(owner, writer);
        long now = owner.serverLevel().getGameTime();
        if (now - told.getOrDefault(owner.getUUID(), 0L) < 60) return;
        told.put(owner.getUUID(), now);
        String who = writer == null ? "Someone" : writer.getGameProfile().getName();
        owner.sendSystemMessage(Component.literal(who + " tried to write your name.").withColor(0xFF3300b5));
    }

    private static void pray(ServerPlayer owner, ServerPlayer writer) {
        if (writer == null || owner.getUUID().equals(writer.getUUID())) return;
        if ("visionary".equals(BeyonderData.getPathway(writer)) && BeyonderData.getSequence(writer) < BeyonderData.getSequence(owner)) return;
        long now = System.currentTimeMillis();
        Long last = prayed.get(owner.getUUID());
        if (last != null && now - last < 10000) return;
        prayed.put(owner.getUUID(), now);
        HonorificNamesEventHandler.addPendingPrayer(owner.getUUID(), new PendingPrayer(
                writer.getUUID(),
                writer.getName().getString(),
                BeyonderData.getPathway(writer),
                BeyonderData.getSequence(writer),
                writer.getX(), writer.getY(), writer.getZ()
        ));
        HonorificNamesEventHandler.answerState.add(new Pair<>(owner.getUUID(), writer.getUUID()));
        owner.sendSystemMessage(HonorificNamesEventHandler.formNotification(writer));
    }

    private static void bar(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withColor(0xFF3300b5)));
    }

    private record Rule(UUID caster, String label, boolean place, ResourceKey<Level> dimension, double x, double z, String outcome, boolean spirit) {}
}
