package de.jakob.lotm.gui.custom.guide;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GuideBookClient {
    private GuideBookClient() {}

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideBookScreen(GuideBook.load(Minecraft.getInstance().getResourceManager())));
    }
}
