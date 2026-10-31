package de.jakob.lotm;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = LOTMCraft.MOD_ID, dist = Dist.CLIENT)
public class LOTMCraftClient {
    public LOTMCraftClient(ModContainer container) {
        // Enables the "Config" button for this mod in the mods list
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
