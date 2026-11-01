package de.jakob.lotm.beyonders.abilities.abyss.DevilWorship;

import de.jakob.lotm.network.packets.toClient.DevilWorshipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DevilWorshipScreen extends Screen {
    private enum Page { LIST, SEEK_TYPE, SEEK_CONFIG, FACTIONS, FACTION_OPTIONS, TARGET, DETAIL }
    private static final int MAX_EYES = 32;

    private static final float EYES_LOST_PER_CLICK = 0.3f;

    private static final float EYES_REGAINED_PER_BACK = 0.5f;
    private static final int EYE_OPEN_TICKS = 20;
    private static final int EYE_CLOSE_TICKS = 14;


    private static final int STROKES = 9;

    private static final int POINTS = 90;
    private static final int BRISTLES = 1;

    private static final float STROKE_ALPHA = 0.28f;
    private static final float STROKE_SATURATION = 0.7f;
    private static final float STROKE_VALUE = 0.75f;

    private static final float EAT_FADE = 0.12f;
    private static final float TIP_FADE = 0.06f;
    // ---- the foul text behind the massive eye
    private static final int FOUL_TEXT_COLOR = 0xFFa3070c;
    private static final ResourceLocation FOUL_FONT = ResourceLocation.parse("minecraft:alt");
    private static final String[] FOUL_LINES = {
            "the abyss hungers and I fed it",
            "blood and filth are the keys it wants",
            "speak the name and the devils come",
            "what is given cannot be undone"
    };
    private static final float FOUL_TEXT_SCALE = 1.0f;
    // ---- pressing confirm
    private static final int EXIT_TICKS = 45;
    private static final float CONFIRM_SOUND_VOLUME = 0.6f;

    private static final class Eye {
        int x, y, w, h;
        float phase;
        long appearAt;
        boolean closing, gone;
        long closeAt;
        long blinkAt, blinkUntil;
        float gazeX, gazeY;
    }
    private static final class Bristle {
        float offset;// sideways position inside the stroke, -1 to 1
        float hueShift;
        float lag;// starts and fades a little after the others (fraction of the stroke's time)
        int size;// thickness in pixels
    }
    private static final class Stroke {
        float startX, startY;// as fractions of the screen
        float angle, drift, curveAmp, curveFreq, curvePhase;
        float length;// fraction of the screen's smaller side
        float width;// pixels across the whole bundle
        float hue, hueSpan, hueSpeed;
        int drawTicks, holdTicks, eatTicks;
        long born;
        final Bristle[] bristles = new Bristle[BRISTLES];
    }

    private final List<Eye> eyes = new ArrayList<>();
    private final Stroke[] strokes = new Stroke[STROKES];
    private final float[] pathX = new float[POINTS], pathY = new float[POINTS];
    private final float[] normalX = new float[POINTS], normalY = new float[POINTS];
    private final Random random = new Random();
    private long ticks = 0;
    private int eyeLayoutWidth = -1, eyeLayoutHeight = -1;

    // The massive eye opens while any of these buttons is hovered: Begin, or a player's name.
    private final List<Button> eyeButtons = new ArrayList<>();
    private float giantOpen = 0f;
    private float giantEase = 0f;
    private float giantGazeX = 0f, giantGazeY = 0f;

    private boolean exiting = false;
    private long exitStart = 0;

    private final int pleasure;
    private final String tribute;
    private final List<DevilWorshipNetwork.PlayerEntry> players;
    private final List<String> pathways;
    private Page page = Page.LIST;
    private Page detailBack = Page.FACTIONS;
    private int selectedIndex = -1;                       // index into DevilWorshipAbility.MISSIONS
    private DevilWorshipAbility.Faction selectedFaction;
    private DevilWorshipAbility.Reward seekReward = DevilWorshipAbility.Reward.CHARACTERISTIC;
    private int seekPathwayIndex = -1;                    // -1 = any pathway
    private int seekSequence = DevilWorshipAbility.SEEK_MAX_SEQ;
    public static void open(int pleasure, String tribute, List<DevilWorshipNetwork.PlayerEntry> players, List<String> pathways) {Minecraft.getInstance().setScreen(new DevilWorshipScreen(pleasure, tribute, players, pathways));}
    private DevilWorshipScreen(int pleasure, String tribute, List<DevilWorshipNetwork.PlayerEntry> players, List<String> pathways) {
        super(Component.literal("Summon Devil"));
        this.pleasure = pleasure;
        this.tribute = tribute;
        this.players = players;
        this.pathways = pathways;
        createInk();
        createEyes();
    }
    private DevilWorshipAbility.MissionDef selected() {return selectedIndex < 0 ? null : DevilWorshipAbility.MISSIONS.get(selectedIndex);}
    @Override
    protected void init() {
        rebuild();
        layoutEyes();
    }
    @Override
    public void tick() {
        ticks++;
        if (exiting && ticks - exitStart >= EXIT_TICKS) {
            onClose();
            return;
        }
        for (Stroke s : strokes) if (ticks > s.born + s.drawTicks + s.holdTicks + s.eatTicks + 25) resetStroke(s, ticks + random.nextInt(40));
        for (Eye e : eyes) {
            if (e.closing && ticks > e.closeAt + EYE_CLOSE_TICKS) e.gone = true;
            if (ticks >= e.blinkAt) { // a quick blink every few seconds
                e.blinkUntil = ticks + 5;
                e.blinkAt = ticks + 60 + random.nextInt(160);
            }
        }
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (exiting) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    private void createInk() {
        for (int i = 0; i < STROKES; i++) {
            strokes[i] = new Stroke();
            for (int b = 0; b < BRISTLES; b++) strokes[i].bristles[b] = new Bristle();
            resetStroke(strokes[i], -random.nextInt(170) + (i % 3) * 25L);
        }
    }
    private void resetStroke(Stroke s, long born) {
        s.startX = random.nextFloat();
        s.startY = random.nextFloat();
        s.angle = random.nextFloat() * 6.2832f;
        s.drift = (random.nextFloat() - 0.5f) * 2.4f;
        s.curveAmp = 0.4f + random.nextFloat() * 0.7f;
        s.curveFreq = 0.6f + random.nextFloat() * 1.4f;
        s.curvePhase = random.nextFloat() * 6.2832f;
        s.length = 0.6f + random.nextFloat() * 0.7f;
        s.width = 4f + random.nextFloat() * 8f;
        s.hue = random.nextFloat();
        s.hueSpan = 0.25f + random.nextFloat() * 0.35f;
        s.hueSpeed = 0.002f + random.nextFloat() * 0.004f;
        s.drawTicks = 50 + random.nextInt(60);
        s.holdTicks = 20 + random.nextInt(50);
        s.eatTicks = 90 + random.nextInt(80);
        s.born = born;
        for (int b = 0; b < BRISTLES; b++) {
            Bristle br = s.bristles[b];
            br.offset = 0f;
            br.hueShift = 0f;
            br.lag = 0f;
            br.size = Math.max(2, Math.round(s.width));
        }
    }
    private void createEyes() {
        int count = pleasure >= 100 ? MAX_EYES : (int) (Math.max(0, pleasure) / 100f * MAX_EYES);
        for (int i = 0; i < count; i++) {
            Eye e = new Eye();
            e.w = 9 + random.nextInt(8);
            e.h = Math.max(3, Math.round(e.w * 0.45f));
            e.phase = random.nextFloat() * 6.28f;
            e.appearAt = 10 + i * 3L + random.nextInt(40); // they open one after another
            e.blinkAt = 60 + random.nextInt(160);
            eyes.add(e);
        }
    }
    private void layoutEyes() {
        if (width == eyeLayoutWidth && height == eyeLayoutHeight) return;
        eyeLayoutWidth = width;
        eyeLayoutHeight = height;
        List<Eye> placed = new ArrayList<>();
        for (Eye e : eyes) {
            placeEye(e, placed);
            placed.add(e);
        }
    }
    private void placeEye(Eye e, List<Eye> others) {
        final int PAD_X = 8;
        final int PAD_Y = 6;
        final int MAX_TRIES = 800;
        for (int tries = 0; tries < MAX_TRIES; tries++) {
            int x = 12 + random.nextInt(Math.max(1, width - 24));
            int y = 12 + random.nextInt(Math.max(1, height - 24));
            int eyeHalfW = e.w + 5 + PAD_X;
            int eyeHalfH = e.h + 3 + PAD_Y;
            boolean overlapsButton = false;
            for (var widget : children()) {
                if (!(widget instanceof Button button)) continue;
                int left = button.getX() - eyeHalfW;
                int right = button.getX() + button.getWidth() + eyeHalfW;
                int top = button.getY() - eyeHalfH;
                int bottom = button.getY() + button.getHeight() + eyeHalfH;
                if (x >= left && x <= right && y >= top && y <= bottom) {
                    overlapsButton = true;
                    break;
                }
            }
            if (overlapsButton) continue;
            // Keep eyes from overlapping each other, including their glow.
            boolean overlapsEye = false;
            for (Eye other : others) {
                if (other == e || other.gone) continue;
                int otherHalfW = other.w + 5 + PAD_X;
                int otherHalfH = other.h + 3 + PAD_Y;
                if (Math.abs(x - other.x) < eyeHalfW + otherHalfW && Math.abs(y - other.y) < eyeHalfH + otherHalfH) {
                    overlapsEye = true;
                    break;
                }
            }
            if (overlapsEye) continue;

            e.x = x;
            e.y = y;
            return;
        }
        e.x = -100;
        e.y = -100;
    }
    private void loseInterest() {
        List<Eye> watching = new ArrayList<>();
        for (Eye e : eyes) if (!e.gone && !e.closing && ticks >= e.appearAt) watching.add(e);
        if (watching.isEmpty()) return;
        Collections.shuffle(watching, random);
        int count = Math.max(1, Math.round(watching.size() * EYES_LOST_PER_CLICK));
        for (int i = 0; i < count && i < watching.size(); i++) {
            Eye e = watching.get(i);
            e.closing = true;
            e.closeAt = ticks + random.nextInt(10);
        }
    }
    private void regainInterest() {
        List<Eye> left = new ArrayList<>();
        List<Eye> watching = new ArrayList<>();
        for (Eye e : eyes) {
            if (e.gone) left.add(e);
            else watching.add(e);
        }
        if (left.isEmpty()) return;
        Collections.shuffle(left, random);
        int count = Math.max(1, Math.round(left.size() * EYES_REGAINED_PER_BACK));
        for (int i = 0; i < count && i < left.size(); i++) {
            Eye e = left.get(i);
            e.gone = false;
            e.closing = false;
            e.appearAt = ticks + random.nextInt(14);
            e.blinkAt = ticks + 60 + random.nextInt(160);
            e.blinkUntil = 0;
            e.gazeX = 0f;
            e.gazeY = 0f;
            placeEye(e, watching);
            watching.add(e);
        }
    }
    private Button add(int x, int y, int w, String text, String tooltip, Runnable action) {
        Button.Builder builder = Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20);
        if (tooltip != null && !tooltip.isEmpty()) builder.tooltip(Tooltip.create(Component.literal(tooltip)));
        return addRenderableWidget(builder.build());
    }
    private void goTo(Page newPage) {
        loseInterest();
        page = newPage;
        rebuild();
    }
    private void goBack(Page newPage) {
        regainInterest();
        page = newPage;
        rebuild();
    }
    private void missionButton(int cx, int y, DevilWorshipAbility.MissionDef def, Runnable action) {add(cx - 180, y, 360, def.title() + "  |  " + def.timerLabel(), def.description(), action);}
    private void rebuild() {
        clearWidgets();
        eyeButtons.clear();
        int cx = width / 2;
        int y = 92;
        switch (page) {
            case LIST -> {
                if (pleasure >= 100) {
                    add(cx - 180, y, 360, "Seek a Characteristic, Recipe or Ingredient", "The Devil searches the world for what you ask for. Stronger sequences and chosen pathways take longer.", () -> goTo(Page.SEEK_TYPE));
                    y += 22;
                    DevilWorshipAbility.MissionDef kill = DevilWorshipAbility.KILL_PLAYER_MISSION;
                    missionButton(cx, y, kill, () -> {
                        selectedIndex = DevilWorshipAbility.MISSIONS.indexOf(kill);
                        goTo(Page.TARGET);
                    });
                    y += 22;
                    DevilWorshipAbility.MissionDef protection = DevilWorshipAbility.PROTECTION_MISSION;
                    missionButton(cx, y, protection, () -> {
                        selectedIndex = DevilWorshipAbility.MISSIONS.indexOf(protection);
                        sendFixed("");
                        onClose(); // no confirm screen, so no ceremony
                    });
                    y += 22;

                    add(cx - 180, y, 360, "Raid a Faction", "Send the Devil against a faction's beyonder team, church or cathedral.", () -> goTo(Page.FACTIONS));
                    y += 22;
                }
                add(cx - 60, Math.max(y + 8, 130), 120, "Close", null, this::onClose);
            }
            case SEEK_TYPE -> {
                add(cx - 120, y, 240, "Characteristic", null, () -> startSeek(DevilWorshipAbility.Reward.CHARACTERISTIC));
                y += 24;
                add(cx - 120, y, 240, "Recipe", null, () -> startSeek(DevilWorshipAbility.Reward.RECIPE));
                y += 24;
                add(cx - 120, y, 240, "Ingredient", "A sub-ingredient. Main ingredients are never found this way.",
                        () -> startSeek(DevilWorshipAbility.Reward.SUB_INGREDIENT));
                y += 24;
                add(cx - 120, y + 8, 240, "Back", null, () -> goBack(Page.LIST));
            }
            case SEEK_CONFIG -> {
                // Pathway row and sequence row. The labels are drawn in render() so the arrows don't need a rebuild.
                add(cx - 150, 96, 20, "<", null, () -> cyclePathway(-1));
                add(cx + 130, 96, 20, ">", null, () -> cyclePathway(1));
                add(cx - 150, 124, 20, "<", "Weaker (takes less time)", () -> changeSequence(1));
                add(cx + 130, 124, 20, ">", "Stronger (takes longer)", () -> changeSequence(-1));
                eyeButtons.add(add(cx - 120, 186, 240, "Begin", null, this::confirmSeek));
                add(cx - 120, 210, 240, "Back", null, () -> goBack(Page.SEEK_TYPE));
            }
            case FACTIONS -> {
                for (DevilWorshipAbility.Faction faction : DevilWorshipAbility.FACTIONS) {
                    add(cx - 120, y, 240, faction.name(), null, () -> {
                        selectedFaction = faction;
                        goTo(Page.FACTION_OPTIONS);
                    });
                    y += 24;
                }
                add(cx - 120, y, 240, "None", "An abandoned cathedral with no faction.", () -> {
                    selectedIndex = DevilWorshipAbility.MISSIONS.indexOf(DevilWorshipAbility.ABANDONED_CATHEDRAL);
                    detailBack = Page.FACTIONS;
                    goTo(Page.DETAIL);
                });
                y += 24;
                add(cx - 120, y + 8, 240, "Back", null, () -> goBack(Page.LIST));
            }
            case FACTION_OPTIONS -> {
                if (selectedFaction != null) {
                    for (DevilWorshipAbility.MissionDef def : List.of(selectedFaction.team(), selectedFaction.church(), selectedFaction.cathedral())) {
                        missionButton(cx, y, def, () -> {
                            selectedIndex = DevilWorshipAbility.MISSIONS.indexOf(def);
                            detailBack = Page.FACTION_OPTIONS;
                            goTo(Page.DETAIL);
                        });
                        y += 24;
                    }
                }
                add(cx - 120, y + 8, 240, "Back", null, () -> goBack(Page.FACTIONS));
            }
            case TARGET -> {
                for (DevilWorshipNetwork.PlayerEntry entry : players) {
                    eyeButtons.add(add(cx - 120, y, 240, entry.name(), null, () -> confirmFixed(entry.uuid())));
                    y += 24;
                }
                add(cx - 120, y + 8, 240, "Back", null, () -> goBack(Page.LIST));
            }
            case DETAIL -> {
                DevilWorshipAbility.MissionDef def = selected();
                int lootLines = def == null ? 1 : Math.max(1, def.loot().size());
                int buttonsY = 108 + lootLines * 12 + 12;
                eyeButtons.add(add(cx - 120, buttonsY, 240, "Begin", null, () -> confirmFixed("")));
                add(cx - 120, buttonsY + 24, 240, "Back", null, () -> goBack(detailBack));
            }
        }
    }
    private void startSeek(DevilWorshipAbility.Reward reward) {
        seekReward = reward;
        goTo(Page.SEEK_CONFIG);
    }
    private void cyclePathway(int direction) {
        if (pathways.isEmpty()) return;
        seekPathwayIndex += direction;
        if (seekPathwayIndex < -1) seekPathwayIndex = pathways.size() - 1;
        if (seekPathwayIndex > pathways.size() - 1) seekPathwayIndex = -1;
    }
    private void changeSequence(int direction) {seekSequence = Math.clamp(seekSequence + direction, DevilWorshipAbility.SEEK_MIN_SEQ, DevilWorshipAbility.SEEK_MAX_SEQ);}
    private void sendFixed(String targetUuid) {PacketDistributor.sendToServer(new DevilWorshipNetwork.ChooseMission(selectedIndex, targetUuid, -1, "", 0));}
    private void confirmFixed(String targetUuid) {
        if (exiting) return;
        sendFixed(targetUuid);
        beginExit();
    }
    private void confirmSeek() {
        if (exiting) return;
        String pathway = seekPathwayIndex < 0 ? "" : pathways.get(seekPathwayIndex);
        PacketDistributor.sendToServer(new DevilWorshipNetwork.ChooseMission(DevilWorshipAbility.SEEK_MISSION_INDEX, "", seekReward.ordinal(), pathway, seekSequence));
        beginExit();
    }
    private void beginExit() {
        exiting = true;
        exitStart = ticks;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WITHER_SPAWN, 1.0f, CONFIRM_SOUND_VOLUME));
    }
    private float exitProgress(float partialTick) {
        if (!exiting) return 0f;
        return clamp01((ticks + partialTick - exitStart) / EXIT_TICKS);
    }
    private float exitGlow(float partialTick) {
        float p = exitProgress(partialTick);
        if (!exiting || p >= 0.75f) return 0f;
        return (float) Math.sin(Math.PI * p / 0.75f);
    }
    private String rewardName() {
        return switch (seekReward) {
            case CHARACTERISTIC -> "Characteristic";
            case RECIPE -> "Recipe";
            case SUB_INGREDIENT -> "Ingredient";
        };
    }
    private void drawStrokes(GuiGraphics g, float t) {
        for (Stroke s : strokes) drawStroke(g, s, t);
    }
    private void computePath(Stroke s) {
        float step = Math.min(width, height) * s.length / (POINTS - 1);
        float x = s.startX * width, y = s.startY * height;
        for (int i = 0; i < POINTS; i++) {
            float u = i / (float) (POINTS - 1);
            float theta = s.angle + s.drift * u + s.curveAmp * (float) Math.sin(6.2832f * s.curveFreq * u + s.curvePhase);
            pathX[i] = x;
            pathY[i] = y;
            normalX[i] = -(float) Math.sin(theta);
            normalY[i] = (float) Math.cos(theta);
            x += (float) Math.cos(theta) * step;
            y += (float) Math.sin(theta) * step;
        }
    }
    private void drawStroke(GuiGraphics g, Stroke s, float t) {
        float age = t - s.born;
        if (age < 0f) return;
        if (age > s.drawTicks + s.holdTicks + s.eatTicks + 25) return;
        computePath(s);
        float headProgress = clamp01(age / s.drawTicks);
        float head = 1f - (float) Math.pow(1f - headProgress, 3);
        float tailAge = age - s.drawTicks - s.holdTicks;
        float tailProgress = clamp01(tailAge / s.eatTicks);
        float tail = tailProgress * tailProgress * (3f - 2f * tailProgress);
        if (head <= tail) return;
        int from = Math.max(0, (int) Math.floor(tail * (POINTS - 1)));
        int to = Math.min(POINTS - 1, (int) Math.ceil(head * (POINTS - 1)));
        int thickness = Math.max(2, Math.round(s.width));
        for (int i = from; i <= to; i++) {
            float u = i / (float) (POINTS - 1);
            float eaten = clamp01((u - tail) / EAT_FADE);
            float tip = clamp01((head - u) / TIP_FADE);
            float taper = (float) Math.sin(Math.PI * u);
            float alpha = STROKE_ALPHA * eaten * tip * (0.4f + 0.6f * taper) * (1f - 0.5f * giantEase);
            if (alpha <= 0.01f) continue;
            int x = Math.round(pathX[i]);
            int y = Math.round(pathY[i]);
            int rgb = hsvToRgb(s.hue + s.hueSpan * u + t * s.hueSpeed);
            int color = (Math.round(alpha * 255f) << 24) | rgb;
            g.fill(x - thickness / 2, y - thickness / 2, x + (thickness + 1) / 2, y + (thickness + 1) / 2, color);
        }
    }
    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
    private static int      lerpColor(float f) {
        int r = Math.round(((DevilWorshipScreen.FOUL_TEXT_COLOR >> 16) & 0xFF) * (1f - f) + ((16777215 >> 16) & 0xFF) * f);
        int g = Math.round(((DevilWorshipScreen.FOUL_TEXT_COLOR >> 8) & 0xFF) * (1f - f) + ((16777215 >> 8) & 0xFF) * f);
        int b = Math.round((DevilWorshipScreen.FOUL_TEXT_COLOR & 0xFF) * (1f - f) + (16777215 & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
    private static int hsvToRgb(float hue) {
        float h = hue - (float) Math.floor(hue);
        float c = DevilWorshipScreen.STROKE_VALUE * DevilWorshipScreen.STROKE_SATURATION;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = DevilWorshipScreen.STROKE_VALUE - c;
        float r, gr, b;
        switch ((int) (h * 6f)) {
            case 0 -> { r = c; gr = x; b = 0; }
            case 1 -> { r = x; gr = c; b = 0; }
            case 2 -> { r = 0; gr = c; b = x; }
            case 3 -> { r = 0; gr = x; b = c; }
            case 4 -> { r = x; gr = 0; b = c; }
            default -> { r = c; gr = 0; b = x; }
        }
        return (Math.round((r + m) * 255f) << 16) | (Math.round((gr + m) * 255f) << 8) | Math.round((b + m) * 255f);
    }
    private void drawEyes(GuiGraphics g, float t, int mouseX, int mouseY) {
        boolean follow = pleasure >= 100;
        for (Eye e : eyes) {
            if (e.gone) continue;
            float open = Math.min(1f, (t - e.appearAt) / EYE_OPEN_TICKS);
            if (open <= 0f) continue;
            if (e.closing) {
                float closed = (t - e.closeAt) / EYE_CLOSE_TICKS;
                if (closed > 0f) open *= Math.max(0f, 1f - closed);
            }
            if (ticks < e.blinkUntil) open *= 0.12f;
            open *= 1f - giantEase;
            if (open <= 0.03f) continue;
            float tx, ty;
            if (follow) {
                float dx = mouseX - e.x, dy = mouseY - e.y;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float strength = Math.min(1f, len / 70f);
                tx = len < 1f ? 0f : dx / len * strength;
                ty = len < 1f ? 0f : dy / len * strength;
                if (e.closing) { tx = -tx; ty = -ty; }
            } else {
                tx = (float) Math.sin(t * 0.02f + e.phase);
                ty = (float) Math.cos(t * 0.017f + e.phase * 1.3f) * 0.6f;
            }
            e.gazeX += (tx - e.gazeX) * 0.2f;
            e.gazeY += (ty - e.gazeY) * 0.2f;
            drawEye(g, e, open);
        }
    }
    private void drawEye(GuiGraphics g, Eye e, float open) {
        int w = e.w;
        float hMax = e.h * open;
        drawLens(g, e.x, e.y, w + 5, hMax + 3f, 0x30FF1010);// glow
        drawLens(g, e.x, e.y, w, hMax, 0xFFE01818);// the eye
        drawLens(g, e.x, e.y, Math.round(w * 0.65f), hMax * 0.6f, 0xFFFF4A3A);// hot center
        int px = Math.round(e.gazeX * w * 0.5f);
        int oy = Math.round(e.gazeY * hMax * 0.35f);
        for (int xr = px - 1; xr <= px; xr++) {
            float f = xr / (float) w;
            int hh = Math.round(hMax * (1f - f * f));
            if (hh <= 0) continue;
            int y1 = e.y - hh + Math.max(0, oy);
            int y2 = e.y + hh + 1 + Math.min(0, oy);
            if (y1 < y2) g.fill(e.x + xr, y1, e.x + xr + 1, y2, 0xFF000000);
        }
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
    private int giantHalfWidth() {
        return Math.min(Math.round(width * 0.4f), 240);
    }
    private void updateGiant(int mouseX, int mouseY) {
        boolean hovering = exiting;
        if (!hovering) for (Button button : eyeButtons) if (button.isMouseOver(mouseX, mouseY)) { hovering = true; break; }
        giantOpen += ((hovering ? 1f : 0f) - giantOpen) * 0.12f;
        giantEase = giantOpen * giantOpen * (3f - 2f * giantOpen);
    }
    private void drawFoulLines(GuiGraphics g, float partialTick) {
        if (giantEase < 0.05f) return;
        float glow = exitGlow(partialTick);
        int rgb = lerpColor(glow);
        int color = (Math.round(255f * giantEase) << 24) | rgb;
        int haloColor = (Math.round(255f * giantEase * glow * 0.5f) << 24) | 0xFFFFFF;
        float bandHeight = height / 4f;
        float time = ticks + partialTick;
        float scrollSpeed = 3.0f;
        for (int i = 0; i < FOUL_LINES.length; i++) {
            Component line = Component.literal(FOUL_LINES[i]).withStyle(Style.EMPTY.withFont(FOUL_FONT));
            int textWidth = font.width(line);
            int textHeight = font.lineHeight;
            if (textWidth <= 0) continue;
            float scale = (float) width / textWidth * FOUL_TEXT_SCALE;
            float scaledWidth = textWidth * scale;
            float scaledHeight = textHeight * scale;
            float bandTop = i * bandHeight;
            float y = bandTop + (bandHeight - scaledHeight) / 2f;
            float spacing = scaledWidth + 40f;
            float lineOffset = spacing * i / FOUL_LINES.length;
            float offset = ((time * scrollSpeed) + lineOffset) % spacing;
            g.enableScissor(0, Math.round(bandTop), width, Math.round(bandTop + bandHeight));
            for (float x = -spacing + offset;
                 x < width + spacing;
                 x += spacing) {
                g.pose().pushPose();
                g.pose().translate(x, y, 0f);
                g.pose().scale(scale, scale, 1f);
                if (glow > 0.05f) {
                    g.drawString(font, line, -1, 0, haloColor, false);
                    g.drawString(font, line, 1, 0, haloColor, false);
                    g.drawString(font, line, 0, -1, haloColor, false);
                    g.drawString(font, line, 0, 1, haloColor, false);
                }
                g.drawString(font, line, 0, 0, color, false);
                g.pose().popPose();
            }
            g.disableScissor();
        }
    }


    private void drawGiantEye(GuiGraphics g, float t, int mouseX, int mouseY) {
        if (giantOpen < 0.02f) return;
        int w = giantHalfWidth();
        int cx = width / 2;
        int cy = height / 2 - 10;
        float open = giantEase;
        float hMax = w * 0.42f * open;
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.08f);
        // Look at the mouse.
        float dx = mouseX - cx, dy = mouseY - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float strength = Math.min(1f, len / 150f);
        float tx = len < 1f ? 0f : dx / len * strength;
        float ty = len < 1f ? 0f : dy / len * strength;
        giantGazeX += (tx - giantGazeX) * 0.15f;
        giantGazeY += (ty - giantGazeY) * 0.15f;
        int glowOuter = (Math.round(0x30 * pulse * open) << 24) | 0xFF1010;
        int glowInner = (Math.round(0x40 * pulse * open) << 24) | 0xFF2020;
        drawLens(g, cx, cy, w + 18, hMax + 14f, glowOuter);
        drawLens(g, cx, cy, w + 8, hMax + 6f, glowInner);
        drawLens(g, cx, cy, w, hMax, 0xFFC81010);                                   // the eye
        drawLens(g, cx, cy, Math.round(w * 0.75f), hMax * 0.75f, 0xFFE02A22);
        drawLens(g, cx, cy, Math.round(w * 0.45f), hMax * 0.45f, 0xFFFF5A44);       // hot center
        int pupilWidth = Math.max(2, w / 9);
        int px = Math.round(giantGazeX * w * 0.45f);
        int oy = Math.round(giantGazeY * hMax * 0.3f);
        for (int xr = px - pupilWidth / 2; xr < px - pupilWidth / 2 + pupilWidth; xr++) {
            float f = xr / (float) w;
            int hh = Math.round(hMax * (1f - f * f));
            if (hh <= 0) continue;
            int y1 = cy - hh + Math.max(0, oy);
            int y2 = cy + hh + 1 + Math.min(0, oy);
            if (y1 < y2) g.fill(cx + xr, y1, cx + xr + 1, y2, 0xFF000000);
        }
    }
    //render
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = ticks + partialTick;
        updateGiant(mouseX, mouseY);
        g.pose().pushPose();
        if (exiting) { // the whole screen shakes, hardest while the text glows
            float amplitude = 1.5f + 4f * exitGlow(partialTick);
            g.pose().translate((random.nextFloat() - 0.5f) * 2f * amplitude, (random.nextFloat() - 0.5f) * 2f * amplitude, 0f);
        }
        g.fill(-24, -24, width + 24, height + 24, 0xFF000000); // all black, a little oversized so shaking never shows the edge
        drawStrokes(g, t);
        drawEyes(g, t, mouseX, mouseY);
        drawFoulLines(g, partialTick);
        drawGiantEye(g, t, mouseX, mouseY);
        int cx = width / 2;
        DevilWorshipAbility.MissionDef def = selected();
        switch (page) {
            case TARGET -> {
                g.drawCenteredString(font, "Choose the player the Devil will hunt", cx, 40, 0xFFD700);
                g.drawCenteredString(font, DevilWorshipAbility.KILL_PLAYER_MISSION.title(), cx, 56, 0xCCCCCC);
            }
            case SEEK_TYPE -> g.drawCenteredString(font, "What should the Devil seek?", cx, 40, 0xFFD700);
            case SEEK_CONFIG -> {
                g.drawCenteredString(font, "Seek a " + rewardName(), cx, 40, 0xFFD700);
                g.drawCenteredString(font, "Choose a pathway and a sequence", cx, 56, 0xCCCCCC);
                boolean pathwayChosen = seekPathwayIndex >= 0;
                String pathwayText = pathwayChosen ? DevilWorshipAbility.prettyPathway(pathways.get(seekPathwayIndex)) : "Any pathway";
                g.drawCenteredString(font, "Pathway: " + pathwayText, cx, 102, 0xFFFFFF);
                g.drawCenteredString(font, "Sequence: " + seekSequence, cx, 130, 0xFFFFFF);
                int ticksNeeded = DevilWorshipAbility.seekTimerTicks(seekSequence, pathwayChosen);
                g.drawCenteredString(font, "Time: " + DevilWorshipAbility.timerLabel(ticksNeeded), cx, 156, 0xCCCCCC);
                g.drawCenteredString(font, "Stronger sequences and chosen pathways take longer.", cx, 170, 0x9932CC);
            }
            case FACTIONS -> g.drawCenteredString(font, "Choose a faction to raid", cx, 40, 0xFFD700);
            case FACTION_OPTIONS -> {
                g.drawCenteredString(font, selectedFaction == null ? "" : selectedFaction.name(), cx, 40, 0xFFD700);
                g.drawCenteredString(font, "Choose what to raid", cx, 56, 0xCCCCCC);
            }
            case DETAIL -> {
                if (def != null) {
                    g.drawCenteredString(font, def.title(), cx, 30, 0xFFD700);
                    g.drawCenteredString(font, "Time: " + def.timerLabel(), cx, 50, 0xCCCCCC);
                    g.drawCenteredString(font, "Possible loot", cx, 76, 0x9932CC);
                    int y = 96;
                    if (def.loot().isEmpty()) {
                        g.drawCenteredString(font, "No loot has been set up for this yet.", cx, y, 0x888888);
                    }
                    for (DevilWorshipAbility.Loot loot : def.loot()) {
                        g.drawCenteredString(font, loot.label() + "  -  " + loot.percent() + "%", cx, y, 0xCCCCCC);
                        y += 12;
                    }
                }
            }
            default -> {
                g.drawCenteredString(font, "Summon Devil", cx, 20, 0xFFD700);
                g.drawCenteredString(font, tribute.isEmpty() ? "No tribute chosen" : "Tribute: " + tribute, cx, 36, 0xCCCCCC);
                int barW = 200, x = cx - barW / 2, y = 52;
                g.fill(x - 1, y - 1, x + barW + 1, y + 13, 0xFF555555);
                g.fill(x, y, x + barW, y + 12, 0xFF222222);
                g.fill(x, y, x + barW * Math.min(100, pleasure) / 100, y + 12, 0xFFB22222);
                g.drawCenteredString(font, pleasure + "%", cx, y + 2, 0xFFFFFF);
                if (pleasure < 100) g.drawCenteredString(font, "The Devils are not pleased enough to answer yet.", cx, 76, 0xFF6666);
                else g.drawCenteredString(font, "Choose what the Devil will be summoned for", cx, 76, 0x9932CC);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: disable Minecraft's default background blur and dimming.
    }
}