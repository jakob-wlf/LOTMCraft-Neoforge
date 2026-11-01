package de.jakob.lotm.events;

import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.block.ModBlocks;
import de.jakob.lotm.network.packets.toServer.StruggleInFilthPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = LOTMCraft.MOD_ID, value = Dist.CLIENT)
public class FilthStruggleClientHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        if (!mc.options.keyJump.isDown()) return;
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.05, player.getZ());
        if (!mc.level.getBlockState(feet).is(ModBlocks.BLOCK_OF_FILTH.get())) return;
        PacketDistributor.sendToServer(new StruggleInFilthPacket());
    }
}