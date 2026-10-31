package de.jakob.lotm.rendering.error;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ErrorDeceitDisplay {

    private ErrorDeceitDisplay() {
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static float deceiveSanity(float real) {
        return ErrorClientState.fakeSanityUntil > now() ? Math.max(0, Math.min(1, ErrorClientState.fakeSanity)) : real;
    }

    public static float deceiveSpirituality(float real, float time) {
        if (ErrorClientState.spiritUntil <= now()) return real;
        return switch (ErrorClientState.spiritMode) {
            case 0 -> Math.max(0, Math.min(1, 0.5f + 0.45f * (float) Math.sin(time * 0.05f) * (float) Math.cos(time * 0.013f)));
            case 1 -> ErrorClientState.spiritValue;
            case 2 -> real * 0.3f;
            default -> Math.min(1, real + (1 - real) * 0.7f);
        };
    }
}
