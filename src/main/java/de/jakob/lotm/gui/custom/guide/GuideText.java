package de.jakob.lotm.gui.custom.guide;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

public final class GuideText {
    public static final int ACCENT = 0x8A1C1C;
    public static final int KEY = 0x24407A;
    public static final int ITEM = 0x6A3A86;
    public static final int ENTITY = 0x1F5E58;
    public static final int REDACTED = 0x15100A;

    private GuideText() {}

    public static String resolve(String raw) {
        Language lang = Language.getInstance();
        return lang.has(raw) ? lang.getOrDefault(raw) : raw;
    }

    public static Component parse(String raw) {
        String s = resolve(raw);
        MutableComponent out = Component.empty();
        StringBuilder buf = new StringBuilder();
        boolean bold = false, italic = false, accent = false, redacted = false;
        Integer color = null;

        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (s.startsWith("**", i)) {
                flush(out, buf, style(bold, italic, accent, redacted, color));
                bold = !bold;
                i += 2;
            } else if (s.startsWith("~~", i)) {
                flush(out, buf, style(bold, italic, accent, redacted, color));
                redacted = !redacted;
                i += 2;
            } else if (s.startsWith("[[", i) || (accent && s.startsWith("]]", i))) {
                flush(out, buf, style(bold, italic, accent, redacted, color));
                accent = s.charAt(i) == '[';
                i += 2;
            } else if (c == '*') {
                flush(out, buf, style(bold, italic, accent, redacted, color));
                italic = !italic;
                i++;
            } else if (c == '{' && s.indexOf('}', i) > i) {
                int end = s.indexOf('}', i);
                String token = s.substring(i + 1, end);
                Style base = style(bold, italic, accent, redacted, color);
                if (token.equals("/c")) {
                    flush(out, buf, base);
                    color = null;
                } else if (token.startsWith("c:")) {
                    flush(out, buf, base);
                    try {
                        color = Integer.parseInt(token.substring(2).replace("#", ""), 16);
                    } catch (NumberFormatException ignored) {
                        color = null;
                    }
                } else {
                    Component inline = inline(token, base);
                    if (inline == null) {
                        buf.append('{').append(token).append('}');
                    } else {
                        flush(out, buf, base);
                        out.append(inline);
                    }
                }
                i = end + 1;
            } else {
                buf.append(c);
                i++;
            }
        }
        flush(out, buf, style(bold, italic, accent, redacted, color));
        return out;
    }

    private static Component inline(String token, Style base) {
        int colon = token.indexOf(':');
        if (colon < 0) return null;
        String kind = token.substring(0, colon);
        String arg = token.substring(colon + 1);
        return switch (kind) {
            case "key" -> {
                KeyMapping mapping = findKey(arg);
                Component name = mapping == null ? Component.literal(arg) : mapping.getTranslatedKeyMessage();
                yield Component.literal("[").append(name).append("]")
                        .withStyle(base.withBold(true).withColor(KEY));
            }
            case "item", "block" -> {
                ResourceLocation id = ResourceLocation.tryParse(arg);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) yield Component.literal(arg);
                yield BuiltInRegistries.ITEM.get(id).getDescription().copy().withStyle(base.withColor(ITEM));
            }
            case "entity" -> {
                ResourceLocation id = ResourceLocation.tryParse(arg);
                if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) yield Component.literal(arg);
                yield BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().copy().withStyle(base.withColor(ENTITY));
            }
            default -> null;
        };
    }

    public static KeyMapping findKey(String name) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.getName().equals(name)) return mapping;
        }
        return null;
    }

    private static Style style(boolean bold, boolean italic, boolean accent, boolean redacted, Integer color) {
        Style s = Style.EMPTY.withBold(bold).withItalic(italic);
        if (redacted) return s.withObfuscated(true).withColor(REDACTED);
        if (color != null) return s.withColor(TextColor.fromRgb(color));
        if (accent) return s.withColor(ACCENT);
        return s;
    }

    private static void flush(MutableComponent out, StringBuilder buf, Style style) {
        if (buf.isEmpty()) return;
        out.append(Component.literal(buf.toString()).withStyle(style));
        buf.setLength(0);
    }
}
