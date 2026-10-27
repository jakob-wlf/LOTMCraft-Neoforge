package de.jakob.lotm.beyonders.abilities.abyss.contract.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class TooltipCentering {
    private TooltipCentering() {}

    public static void center(List<Component> lines) {
        Font font = Minecraft.getInstance().font;
        int space = font.width(" ");
        int max = 0;
        for (Component c : lines) max = Math.max(max, font.width(c));
        for (int i = 0; i < lines.size(); i++) {
            int pad = (max - font.width(lines.get(i))) / 2 / space;
            if (pad > 0) {lines.set(i, Component.literal(" ".repeat(pad)).append(lines.get(i)));}
        }
    }
}