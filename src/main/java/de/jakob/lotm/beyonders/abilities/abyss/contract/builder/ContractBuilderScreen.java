package de.jakob.lotm.beyonders.abilities.abyss.contract.builder;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.toServer.SubmitContractBuilderPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ContractBuilderScreen extends Screen {
    private static final int MARGIN = 14;
    private static final int PAGE_Y = 4;
    private static final int MAX_PAGE_W = 560;

    private static final int TERM_H = 16;
    private static final int GAP = 3;
    private static final int GROUP_GAP = 4;
    private static final int ACTION_H = 16;
    private static final int VALUE_H = 18;

    private static final int INK = 0xFF1A1020;
    private static final int BLOOD = 0xFF4B2378;   // title colour (now purple)

    //=branding effect
    private static final int BRAND_TICKS = 50;
    private static final int BRAND_HOT = 0xFF3318;
    private static final int BRAND_HALO = 0xFF2200;
    private static final SoundEvent BRAND_CRACKLE = SoundEvents.FIRE_AMBIENT;// flesh crackling
    private static final SoundEvent BRAND_SIZZLE  = SoundEvents.GENERIC_BURN;// the "tsss" of a burn
    private static final SoundEvent BRAND_HISS    = SoundEvents.FIRE_EXTINGUISH;// low, wet hiss underneath
    private static final float CRACKLE_VOLUME = 0.6f;
    private static final float SIZZLE_VOLUME  = 0.5f;
    private static final float HISS_VOLUME    = 0.2f;

    private record Group(String title, List<ContractTermKind> kinds) {}
    private record Heading(String text, int x, int y) {}
    private static final class Word {
        String text;
        final BuilderToken token;
        Word(String text, BuilderToken token) { this.text = text; this.token = token; }
    }
    private record Placed(Word word, int x) {}
    private record Slot(ContractTermKind kind, int x, int y, int w) {}
    private final Random soundRandom = new Random();

    private final List<BuilderToken> tokens = new ArrayList<>();
    private final Map<BuilderToken, Long> brands = new IdentityHashMap<>();
    private final List<Heading> headings = new ArrayList<>();

    private BuilderToken activeInputToken = null;
    private EditBox valueBox;
    private long ticks = 0;
    private final Random eyeRandom = new Random();
    private long blinkAt = 60, blinkUntil = 0;
    private float eyeOpen = 1f, eyeGazeX = 0f, eyeGazeY = 0f;
    private static final int PAIN_TICKS = 24;
    private long painStart = -1000;

    // Layout
    private int pageX, pageY, pageW, pageH;

    private int contractScrollOffset = 0;
    private int maxContractScrollOffset = 0;

    private final List<int[]> skin = new ArrayList<>();

    public ContractBuilderScreen() {super(Component.literal("Contract Builder"));}

    @Override
    public void tick() {
        ticks++;
        if (ticks >= blinkAt) {
            blinkUntil = ticks + 5;
            blinkAt = ticks + 60 + eyeRandom.nextInt(160);
        }
    }
    // GROUPS
    private List<Group> buildGroups() {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("Parties", List.of(
                ContractTermKind.PARTY_A, ContractTermKind.PARTY_B)));
        groups.add(new Group("Actions", List.of(
                ContractTermKind.GIVES, ContractTermKind.GETS, ContractTermKind.RETURNS,
                ContractTermKind.KILLS, ContractTermKind.BIND_LIFE_DEATH)));
        groups.add(new Group("Connectors", List.of(
                ContractTermKind.FOR, ContractTermKind.FROM, ContractTermKind.TO,
                ContractTermKind.UNTIL, ContractTermKind.PASSES)));
        groups.add(new Group("Things", List.of(
                ContractTermKind.SOUL, ContractTermKind.RESOURCE, ContractTermKind.DIGEST,
                ContractTermKind.AMOUNT, ContractTermKind.VICTIM, ContractTermKind.ENTITY_TYPE,
                ContractTermKind.TIME_UNIT)));
        // Anything not listed above still gets a button.
        List<ContractTermKind> other = new ArrayList<>();
        for (ContractTermKind k : ContractTermKind.values()) {
            boolean found = false;
            for (Group g : groups) if (g.kinds().contains(k)) { found = true; break; }
            if (!found) other.add(k);
        }
        if (!other.isEmpty()) groups.add(new Group("Other", other));
        return groups;
    }
    // INIT / LAYOUT
    @Override
    protected void init() {
        headings.clear();
        pageW = Math.min(this.width - MARGIN * 2, MAX_PAGE_W);
        pageX = (this.width - pageW) / 2;
        pageY = PAGE_Y;
        List<Group> groups = buildGroups();

        // Left column for the group names
        int labelW = 0;
        for (Group g : groups) labelW = Math.max(labelW, this.font.width(g.title()));
        labelW += 10;
        int areaW = pageW - labelW;
        // Flow each group's buttons left to right (relative y, wraps only if needed)
        List<Slot> slots = new ArrayList<>();
        List<Integer> groupTops = new ArrayList<>();
        int ry = 0;
        for (Group g : groups) {
            groupTops.add(ry);
            int x = 0;
            int rowY = ry;
            for (ContractTermKind kind : g.kinds()) {
                int w = Math.min(areaW, Math.max(40, this.font.width(kind.label) + 12));
                if (x > 0 && x + w > areaW) {
                    x = 0;
                    rowY += TERM_H + GAP;
                }
                slots.add(new Slot(kind, x, rowY, w));
                x += w + GAP;
            }
            ry = rowY + TERM_H + GROUP_GAP;
        }
        int totalH = ry - GROUP_GAP;
        int actionY = this.height - ACTION_H - 6;
        int gridBottom = actionY - 5;
        int gridTop = gridBottom - totalH;
        pageH = Math.max(60, gridTop - 6 - pageY);
        int valueY = pageY + pageH - VALUE_H - 12;
        buildSkin(pageW, pageH);
        // Group names
        for (int i = 0; i < groups.size(); i++) {headings.add(new Heading(groups.get(i).title(), pageX, gridTop + groupTops.get(i) + (TERM_H - 8) / 2 - 1));}
        // TERM BUTTONS
        for (Slot s : slots) {
            ContractTermKind kind = s.kind();
            this.addRenderableWidget(new BoneButton(pageX + labelW + s.x(), gridTop + s.y(), s.w(), TERM_H, kind.label, b -> onTermClicked(kind)));
        }
        // ACTION BUTTONS (small, one centered row)
        String[] labels = {"Sign as A", "Sign as B", "Leave Blank", "Undo", "Clear"};
        Runnable[] actions = {() -> submit(1), () -> submit(2), () -> submit(0), this::undo, this::clearAll};
        int[] widths = new int[labels.length];
        int rowW = 0;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = Math.max(48, this.font.width(labels[i]) + 14);
            rowW += widths[i] + (i > 0 ? GAP : 0);
        }
        int ax = pageX + (pageW - rowW) / 2;
        for (int i = 0; i < labels.length; i++) {
            Runnable action = actions[i];
            this.addRenderableWidget(new BoneButton(ax, actionY, widths[i], ACTION_H,
                    labels[i], b -> action.run()));
            ax += widths[i] + GAP;
        }
        // INPUT BOX
        valueBox = new EditBox(this.font, pageX + 90, valueY, pageW - 104, VALUE_H, Component.literal("value"));
        valueBox.setResponder(s -> {if (activeInputToken != null) activeInputToken.value = s;});
        valueBox.setVisible(activeInputToken != null);
        if (activeInputToken != null) valueBox.setValue(activeInputToken.value);
        this.addRenderableWidget(valueBox);
    }
    private static class BoneButton extends Button {
        private final String label;
        BoneButton(int x, int y, int w, int h, String label, OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.label = label;
        }
        @Override
        public void renderWidget(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, hover ? 0xFF4A2A20 : 0xFF2A1A14);
            g.renderOutline(x, y, w, h, hover ? 0xFFB07A5A : 0xFF6B4A3A);
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hover ? 0xFFFFE9CF : 0xFFE0C9AE);
        }
    }
    // SKIN PAPER
    private void addRect(int x0, int y0, int x1, int y1, int color, int w, int h) {
        x0 = Math.max(x0, 2); y0 = Math.max(y0, 2);
        x1 = Math.min(x1, w - 2); y1 = Math.min(y1, h - 2);
        if (x0 >= x1 || y0 >= y1) return;
        skin.add(new int[]{x0, y0, x1, y1, color});
    }
    private void addColumn(int x, int ya, int yb, int color, int w, int h) {addRect(x, Math.min(ya, yb), x + 1, Math.max(ya, yb) + 1, color, w, h);}
    private void buildSkin(int w, int h) {
        skin.clear();
        Random r = new Random(1337L);
        // Faint paper creases
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(Math.max(1, w / 2));
            double yy = 14 + r.nextInt(Math.max(1, h - 28));
            int len = w / 4 + r.nextInt(Math.max(1, w / 3));
            double phase = r.nextDouble() * Math.PI * 2;
            double freq = 0.05 + r.nextDouble() * 0.08;
            double amp = 1.0 + r.nextDouble() * 1.8;
            int prev = (int) yy;
            for (int k = 0; k < len; k++) {
                yy += (r.nextDouble() - 0.5) * 0.4;
                int cy = (int) (yy + Math.sin(k * freq + phase) * amp);
                addColumn(x + k, prev, cy, 0x28403050, w, h);
                addColumn(x + k, prev + 1, cy + 1, 0x30FFFFFF, w, h);
                prev = cy;
            }
        }
    }
    // BRANDING
    private void brand(BuilderToken token) {
        painStart = ticks;
        brands.put(token, ticks);
        contractScrollOffset = Integer.MAX_VALUE; // clamped to the bottom on the next render
        var sounds = Minecraft.getInstance().getSoundManager();
        sounds.play(SimpleSoundInstance.forUI(BRAND_CRACKLE, randomPitch(0.7f, 1.0f), CRACKLE_VOLUME));
        sounds.play(SimpleSoundInstance.forUI(BRAND_SIZZLE,  randomPitch(0.6f, 0.9f), SIZZLE_VOLUME));
        sounds.play(SimpleSoundInstance.forUI(BRAND_HISS,    randomPitch(0.5f, 0.8f), HISS_VOLUME));
    }

    private float randomPitch(float min, float max) {return min + soundRandom.nextFloat() * (max - min);}
    private static int lerpRgb(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 0xFF) * (1f - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1f - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1f - f) + (b & 0xFF) * f);
        return (r << 16) | (g << 8) | bl;
    }
    private float glowOf(Word w, float t) {
        if (w.token == null) return 0f;
        Long start = brands.get(w.token);
        if (start == null) return 0f;
        float age = t - start;
        if (age < 0f || age >= BRAND_TICKS) return 0f;
        float g = 1f - age / BRAND_TICKS;
        g = g * g * (3f - 2f * g);
        g *= 0.88f + 0.12f * (float) Math.sin(t * 0.9f); // flicker
        return Math.clamp(g, 0f, 1f);
    }
    private void drawWord(GuiGraphics g, Word w, int x, int y, float t) {
        float glow = glowOf(w, t);
        if (glow <= 0.01f) {
            g.drawString(this.font, w.text, x, y, INK, false);
            return;
        }
        int halo = (Math.round(255f * glow * 0.55f) << 24) | BRAND_HALO;
        g.drawString(this.font, w.text, x - 1, y, halo, false);
        g.drawString(this.font, w.text, x + 1, y, halo, false);
        g.drawString(this.font, w.text, x, y - 1, halo, false);
        g.drawString(this.font, w.text, x, y + 1, halo, false);
        int core = 0xFF000000 | lerpRgb(INK & 0xFFFFFF, BRAND_HOT, glow);
        g.drawString(this.font, w.text, x, y, core, false);
    }
    // TERM / ACTION HANDLING
    private void onTermClicked(ContractTermKind kind) {
        commitActiveInput();
        if (kind == ContractTermKind.TIME_UNIT) {
            if (!tokens.isEmpty() && tokens.getLast().kind == ContractTermKind.TIME_UNIT) {
                BuilderToken last = tokens.getLast();
                last.value = switch (last.value) {
                    case "days" -> "minutes";
                    case "minutes" -> "seconds";
                    default -> "days";
                };
                brand(last);
                return;
            }
            BuilderToken t = new BuilderToken(kind, "days");
            tokens.add(t);
            brand(t);
            return;
        }
        if (kind == ContractTermKind.ENTITY_TYPE) {
            if (!tokens.isEmpty() && tokens.getLast().kind == ContractTermKind.ENTITY_TYPE) {
                BuilderToken last = tokens.getLast();
                last.value = "MOB".equalsIgnoreCase(last.value) ? "PLAYER" : "MOB";
                brand(last);
                return;
            }
            BuilderToken t = new BuilderToken(kind, "MOB");
            tokens.add(t);
            brand(t);
            return;
        }
        BuilderToken token = new BuilderToken(kind, "");
        tokens.add(token);
        brand(token);
        if (kind.isInput) {
            activeInputToken = token;
            valueBox.setVisible(true);
            valueBox.setValue("");
            valueBox.setFocused(true);
        }
    }
    private void commitActiveInput() {
        activeInputToken = null;
        if (valueBox != null) valueBox.setVisible(false);
    }
    private void undo() {
        commitActiveInput();
        if (!tokens.isEmpty()) brands.remove(tokens.removeLast());
    }
    private void clearAll() {
        tokens.clear();
        brands.clear();
        contractScrollOffset = 0;
        commitActiveInput();
    }
    private void submit(int signMode) {
        commitActiveInput();
        List<SubmitContractBuilderPacket.TokenDTO> dto = new ArrayList<>();
        for (BuilderToken t : tokens) {
            dto.add(new SubmitContractBuilderPacket.TokenDTO(t.kind.ordinal(), t.value));
        }
        PacketHandler.sendToServer(new SubmitContractBuilderPacket(dto, signMode));
        this.onClose();
    }
    // SENTENCE
    private void addWords(List<Word> out, String text, BuilderToken token) {
        for (String part : text.split(" ")) {
            if (!part.isEmpty()) out.add(new Word(part, token));
        }
    }
    private List<Word> buildWords() {
        List<Word> words = new ArrayList<>();
        for (BuilderToken t : tokens) {
            if (t.kind == ContractTermKind.SOUL) {
                if (!words.isEmpty()) {
                    Word last = words.getLast();
                    last.text = last.text + "'s";
                    words.add(new Word("soul", t));
                } else {
                    addWords(words, "[invalid: bare soul]", t);
                }
                continue;
            }
            String word = switch (t.kind) {
                case PARTY_A -> "Party A";
                case PARTY_B -> "Party B";
                case GIVES -> "gives";
                case GETS -> "gets";
                case FOR -> "for";
                case FROM -> "from";
                case RETURNS -> "returns";
                case TO -> "to";
                case KILLS -> "kills";
                case VICTIM -> t.value.isEmpty() ? "[victim]" : t.value;
                case BIND_LIFE_DEATH -> "binds life and death with";
                case SOUL -> "";
                case RESOURCE -> t.value.isEmpty() ? "[resource]" : t.value;
                case AMOUNT -> t.value.isEmpty() ? "[amount]" : t.value;
                case TIME_UNIT -> t.value;
                case UNTIL -> "until";
                case PASSES -> "passes";
                case DIGEST -> "digest";
                case ENTITY_TYPE -> t.value.isEmpty() ? "[entity type]" : t.value;
            };
            addWords(words, word, t);
        }
        return words;
    }
    private List<List<Placed>> layout(List<Word> words, int maxWidth) {
        List<List<Placed>> lines = new ArrayList<>();
        List<Placed> line = new ArrayList<>();
        int space = this.font.width(" ");
        int x = 0;
        for (Word w : words) {
            int ww = this.font.width(w.text);
            if (x > 0 && x + ww > maxWidth) {
                lines.add(line);
                line = new ArrayList<>();
                x = 0;
            }
            line.add(new Placed(w, x));
            x += ww + space;
        }
        lines.add(line);
        return lines;
    }
    // SCROLLING
    private void updateContractScroll(int lineCount) {
        int textTop = pageY + 36;
        int textBottom = pageY + pageH - 10;
        int visibleHeight = textBottom - textTop;
        maxContractScrollOffset = Math.max(0, lineCount * 10 - visibleHeight);
        contractScrollOffset = Math.clamp(contractScrollOffset, 0, maxContractScrollOffset);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= pageX && mouseX <= pageX + pageW && mouseY >= pageY && mouseY <= pageY + pageH) {
            contractScrollOffset -= (int) (scrollY * 10);
            contractScrollOffset = Math.clamp(contractScrollOffset, 0, maxContractScrollOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    // RENDER
    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        float t = ticks + partialTick;
        // Shadow + paper
        g.fill(pageX + 3, pageY + 3, pageX + pageW + 3, pageY + pageH + 3, 0x66000000);
        g.fillGradient(pageX, pageY, pageX + pageW, pageY + pageH, 0xFFEDE6F0, 0xFFD3C8DC);
        for (int[] s : skin) {g.fill(pageX + s[0], pageY + s[1], pageX + s[2], pageY + s[3], s[4]);}
        // Rolled scroll ends
        g.fillGradient(pageX, pageY, pageX + pageW, pageY + 8, 0xFFB5A6C4, 0xFFE9E1EE);
        g.fillGradient(pageX, pageY + pageH - 8, pageX + pageW, pageY + pageH, 0xFFE9E1EE, 0xFFB5A6C4);
        g.fill(pageX, pageY + 8, pageX + pageW, pageY + 9, 0x553A2A48);
        g.fill(pageX, pageY + pageH - 9, pageX + pageW, pageY + pageH - 8, 0x33FFFFFF);
        // Aged edges
        for (int i = 0; i < 6; i++) {
            int alpha = (0x40 - i * 7) << 24;
            g.renderOutline(pageX + i, pageY + i, pageW - i * 2, pageH - i * 2, alpha | 0x3A2A48);
        }
        g.renderOutline(pageX, pageY, pageW, pageH, 0xFF4A3A5A);
        // The watching eye
        drawEye(g, pageX + 46, pageY + pageH - 30, t, mouseX, mouseY);
        g.drawCenteredString(this.font, "~ CONTRACT ~", pageX + pageW / 2, pageY + 13, BLOOD);
        // Contract text
        int textTop = pageY + 36;
        int textBottom = pageY + pageH - 10;
        List<Word> words = buildWords();
        if (words.isEmpty()) {
            g.drawString(this.font, "Your contract will appear here...", pageX + 14, textTop, 0x88301A10, false);
            maxContractScrollOffset = 0;
        } else {
            List<List<Placed>> lines = layout(words, pageW - 28);
            updateContractScroll(lines.size());
            g.enableScissor(pageX + 4, textTop, pageX + pageW - 4, textBottom);
            int ly = textTop - contractScrollOffset;
            for (List<Placed> line : lines) {
                for (Placed p : line) {drawWord(g, p.word(), pageX + 14 + p.x(), ly, t);}
                ly += 10;
            }
            g.disableScissor();
            if (maxContractScrollOffset > 0) {
                int sx = pageX + pageW - 9;
                int sh = textBottom - textTop;
                g.fill(sx, textTop, sx + 3, textTop + sh, 0x553A2014);
                int total = lines.size() * 10;
                int thumbH = Math.max(15, (int) ((float) sh * sh / total));
                int travel = sh - thumbH;
                int thumbY = textTop + (int) ((float) contractScrollOffset / maxContractScrollOffset * travel);
                g.fill(sx, thumbY, sx + 3, thumbY + thumbH, 0xCC6B2F3F);
            }
        }
        for (Heading h : headings) {g.drawString(this.font, h.text(), h.x(), h.y() + 1, 0xFFB07A5A, false);}
        super.render(g, mouseX, mouseY, partialTick);
    }
    private static void drawLens(GuiGraphics g, int cx, int cy, int w, float hMax, int color) {
        if (w < 1 || hMax < 0.5f) return;
        for (int x = -w; x <= w; x++) {
            float f = x / (float) w;
            int hh = Math.round(hMax * (1f - f * f));
            if (hh <= 0) continue;
            g.fill(cx + x, cy - hh, cx + x + 1, cy + hh + 1, color);
        }
    }
    private void drawEye(GuiGraphics g, int cx, int cy, float t, int mouseX, int mouseY) {
        int w = 30;
        float pain = painAmount(t);
        // Flinch: the eye shakes while it hurts
        if (pain > 0f) {
            cx += Math.round((eyeRandom.nextFloat() - 0.5f) * 4f * pain);
            cy += Math.round((eyeRandom.nextFloat() - 0.5f) * 3f * pain);
        }
        // Fully closed on blink; squinted while in pain
        float target = ticks < blinkUntil ? 0f : 1f - 0.35f * pain;
        eyeOpen += (target - eyeOpen) * 0.4f;
        float hMax = 13f * eyeOpen;
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.08f);
        float boost = 1f + pain * 0.9f;
        g.enableScissor(pageX + 2, pageY + 2, pageX + pageW - 2, pageY + pageH - 2);
        // Black veins radiating from the eye
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + 0.2;
            int len = 30 + (i % 3) * 12;
            for (int k = w + 6; k < w + 6 + len; k++) {
                double wob = Math.sin(k * 0.35 + i) * 1.3;
                int x = cx + (int) (Math.cos(a) * k - Math.sin(a) * wob);
                int y = cy + (int) ((Math.sin(a) * k + Math.cos(a) * wob) * 0.6);
                int alpha = Math.min(0xA0, Math.round(Math.max(0x10, 0x55 - (k - w) * 2) * boost));
                g.fill(x, y, x + 1, y + 1, (alpha << 24) | 000000);
            }
        }
        if (hMax >= 0.5f) {
            // Glow
            int g1 = Math.min(0xFF, Math.round(0x22 * pulse * boost));
            int g2 = Math.min(0xFF, Math.round(0x38 * pulse * boost));
            drawLens(g, cx, cy, w + 12, hMax + 9f, (g1 << 24) | 0xFF1010);
            drawLens(g, cx, cy, w + 6, hMax + 5f, (g2 << 24) | 0xFF2020);
            // Outline, then the lens in layers (hotter and paler in pain)
            drawLens(g, cx, cy, w + 2, hMax + 2f, 0xFF2A0A12);
            drawLens(g, cx, cy, w, hMax, 0xFF000000 | lerpRgb(0xC81010, 0xFF1A1A, pain));
            drawLens(g, cx, cy, Math.round(w * 0.75f), hMax * 0.75f, 0xFF000000 | lerpRgb(0xE02A22, 0xFF5A4A, pain));
            drawLens(g, cx, cy, Math.round(w * 0.45f), hMax * 0.45f, 0xFF000000 | lerpRgb(0xFF5A44, 0xFFB0A0, pain));
            // Gaze follows the mouse
            float dx = mouseX - cx, dy = mouseY - cy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float strength = Math.min(1f, len / 120f);
            float tx = len < 1f ? 0f : dx / len * strength;
            float ty = len < 1f ? 0f : dy / len * strength;
            eyeGazeX += (tx - eyeGazeX) * 0.15f;
            eyeGazeY += (ty - eyeGazeY) * 0.15f;
            // Slit pupil: flails wildly and shortens in pain
            int px = Math.round(eyeGazeX * w * 0.4f);
            int oy = Math.round(eyeGazeY * hMax * 0.3f);
            if (pain > 0f) {
                float swing = (float) Math.sin(t * 0.35f) * 0.9f;
                px = Math.round(swing * w * 0.8f * pain);
                oy = Math.round((float) Math.sin(t * 0.5f + 1.3f) * hMax * 0.6f * pain);
            }
            float shrink = 1f - 0.65f * pain;
            for (int xr = px - 1; xr <= px + 1; xr++) {
                float f = xr / (float) w;
                if (Math.abs(f) >= 1f) continue;
                int hh = Math.round(hMax * (1f - f * f));
                int ph = Math.round(hh * shrink);
                if (ph <= 0) continue;
                int y1 = Math.max(cy - hh, cy - ph + oy);
                int y2 = Math.min(cy + hh + 1, cy + ph + 1 + oy);
                if (y1 < y2) g.fill(cx + xr, y1, cx + xr + 1, y2, 0xFF000000);
            }
            if (eyeOpen > 0.5f && pain <= 0.05f) g.fill(cx + px + 2, cy - 4 + oy, cx + px + 4, cy - 2 + oy, 0xCCFFFFFF);
            // Eyelid creases
            for (int x = -w; x <= w; x++) {
                float f = x / (float) w;
                int hh = Math.round(hMax * (1f - f * f));
                if (hh <= 0) continue;
                g.fill(cx + x, cy - hh - 3, cx + x + 1, cy - hh - 2, 0xAA2A0A12);
                g.fill(cx + x, cy + hh + 3, cx + x + 1, cy + hh + 4, 0x552A0A12);
            }
        }
        // Closed lid: a dark seam where the eye was
        if (eyeOpen < 0.15f) {
            for (int x = -w; x <= w; x++) {
                float f = x / (float) w;
                int sy = cy + Math.round(3f * (1f - f * f));
                g.fill(cx + x, sy, cx + x + 1, sy + 2, 0xFF2A0A12);
            }
        }
        g.disableScissor();
    }
    private float painAmount(float t) {
        float age = t - painStart;
        if (age < 0f || age >= PAIN_TICKS) return 0f;
        return 1f - age / PAIN_TICKS;   // starts at 1 on the click, fades to 0
    }
    @Override
    public boolean isPauseScreen() {return false;}
    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty.
    }
}