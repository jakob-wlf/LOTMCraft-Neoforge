package de.jakob.lotm.gui.custom.error;

import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.network.packets.error.ErrorActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class DeceitPrepareScreen extends Screen {

    private static final float[] PERCENTAGES = {0f, 0.05f, 0.1f, 0.25f, 0.5f, 0.75f, 1f};

    private final CompoundTag config;
    private int selected = 2;
    private EditBox itemBox, nameBox, xBox, yBox, zBox, redirectBox;

    public DeceitPrepareScreen(CompoundTag config) {
        super(Component.translatable("gui.lotmcraft.deceit_prepare.title"));
        this.config = config.copy();
    }

    private boolean inAlloy(int index) {
        return (config.getInt("alloy") & (1 << index)) != 0;
    }

    private void toggleAlloy(int index) {
        config.putInt("alloy", config.getInt("alloy") ^ (1 << index));
        rebuildWidgets();
    }

    private static String percent(float value) {
        return Math.round(value * 100) + "%";
    }

    private static float nextPercentage(float current) {
        for (float value : PERCENTAGES) {
            if (value > current + 0.001f) return value;
        }
        return PERCENTAGES[0];
    }

    @Override
    protected void init() {
        storeTextFields();

        int left = width / 2 - 200;
        int top = height / 2 - 110;

        for (int i = 0; i < DeceitWheelScreen.DECEITS.length; i++) {
            final int index = i;
            int y = top + 20 + i * 18;
            addRenderableWidget(Button.builder(Component.literal(inAlloy(i) ? "✔" : "·"), b -> toggleAlloy(index))
                    .bounds(left, y, 16, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.lotmcraft.deceit_prepare.alloy_toggle")))
                    .build());
            Button nameButton = addRenderableWidget(Button.builder(DeceitWheelScreen.name(i), b -> {
                storeTextFields();
                selected = index;
                rebuildWidgets();
            }).bounds(left + 18, y, 130, 16).build());
            nameButton.active = selected != i;
        }

        buildConfigPanel(width / 2 - 30, top + 20);

        addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.save"), b -> save())
                .bounds(width / 2 - 30, top + 20 + 11 * 18 - 16, 230, 18).build());
    }

    private void storeTextFields() {
        if (itemBox != null) config.putString("itemDisguise", itemBox.getValue());
        if (nameBox != null) config.putString("itemName", nameBox.getValue());
        if (xBox != null) config.putInt("fakeX", parse(xBox.getValue(), config.getInt("fakeX")));
        if (yBox != null) config.putInt("fakeY", parse(yBox.getValue(), config.getInt("fakeY")));
        if (zBox != null) config.putInt("fakeZ", parse(zBox.getValue(), config.getInt("fakeZ")));
        if (redirectBox != null) config.putString("redirect", redirectBox.getValue());
        itemBox = nameBox = xBox = yBox = zBox = redirectBox = null;
    }

    private static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void buildConfigPanel(int x, int y) {
        String key = DeceitWheelScreen.DECEITS[selected];
        switch (key) {
            case "sanity" -> addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.shown_sanity", percent(config.getFloat("sanity"))), b -> {
                config.putFloat("sanity", nextPercentage(config.getFloat("sanity")));
                rebuildWidgets();
            }).bounds(x, y + 30, 230, 18).build());
            case "health" -> addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.shown_health", percent(config.getFloat("health"))), b -> {
                config.putFloat("health", nextPercentage(config.getFloat("health")));
                rebuildWidgets();
            }).bounds(x, y + 30, 230, 18).build());
            case "spirituality" -> {
                addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.spirit_mode." + config.getInt("spiritMode")), b -> {
                    config.putInt("spiritMode", (config.getInt("spiritMode") + 1) % 4);
                    rebuildWidgets();
                }).bounds(x, y + 30, 230, 18).build());
                if (config.getInt("spiritMode") == 1) {
                    addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.spirit_value", percent(config.getFloat("spiritValue"))), b -> {
                        config.putFloat("spiritValue", nextPercentage(config.getFloat("spiritValue")));
                        rebuildWidgets();
                    }).bounds(x, y + 52, 230, 18).build());
                }
            }
            case "hunger" -> addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.hunger_mode." + config.getInt("hungerMode")), b -> {
                config.putInt("hungerMode", (config.getInt("hungerMode") + 1) % 4);
                rebuildWidgets();
            }).bounds(x, y + 30, 230, 18).build());
            case "item" -> {
                itemBox = addRenderableWidget(new EditBox(font, x, y + 40, 230, 16, Component.translatable("gui.lotmcraft.deceit_prepare.item_look")));
                itemBox.setMaxLength(80);
                itemBox.setValue(config.contains("itemDisguise") ? config.getString("itemDisguise") : "minecraft:stick");
                nameBox = addRenderableWidget(new EditBox(font, x, y + 72, 230, 16, Component.translatable("gui.lotmcraft.deceit_prepare.item_name")));
                nameBox.setMaxLength(48);
                nameBox.setValue(config.getString("itemName"));
                addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.use_held"), b -> {
                    if (minecraft != null && minecraft.player != null && !minecraft.player.getOffhandItem().isEmpty()) {
                        itemBox.setValue(BuiltInRegistries.ITEM.getKey(minecraft.player.getOffhandItem().getItem()).toString());
                    }
                }).bounds(x, y + 94, 230, 18).build());
            }
            case "inventory" -> addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.edit_inventory"), b -> {
                storeTextFields();
                if (minecraft != null) minecraft.setScreen(new DeceitInventoryEditorScreen(this, config));
            }).bounds(x, y + 30, 230, 18).build());
            case "divination" -> {
                addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.div_mode." + config.getInt("divMode")), b -> {
                    storeTextFields();
                    config.putInt("divMode", (config.getInt("divMode") + 1) % 3);
                    rebuildWidgets();
                }).bounds(x, y + 30, 230, 18).build());
                int mode = config.getInt("divMode");
                if (mode == 0) {
                    xBox = addRenderableWidget(new EditBox(font, x, y + 64, 72, 16, Component.literal("X")));
                    yBox = addRenderableWidget(new EditBox(font, x + 79, y + 64, 72, 16, Component.literal("Y")));
                    zBox = addRenderableWidget(new EditBox(font, x + 158, y + 64, 72, 16, Component.literal("Z")));
                    xBox.setValue(String.valueOf(config.getInt("fakeX")));
                    yBox.setValue(String.valueOf(config.contains("fakeY") ? config.getInt("fakeY") : 64));
                    zBox.setValue(String.valueOf(config.getInt("fakeZ")));
                    addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.fake_seq", config.contains("fakeSeq") ? config.getInt("fakeSeq") : 9), b -> {
                        storeTextFields();
                        int seq = config.contains("fakeSeq") ? config.getInt("fakeSeq") : 9;
                        config.putInt("fakeSeq", seq <= 0 ? 9 : seq - 1);
                        rebuildWidgets();
                    }).bounds(x, y + 88, 230, 18).build());
                } else if (mode == 1) {
                    redirectBox = addRenderableWidget(new EditBox(font, x, y + 64, 230, 16, Component.translatable("gui.lotmcraft.deceit_prepare.redirect")));
                    redirectBox.setMaxLength(16);
                    redirectBox.setValue(config.getString("redirect"));
                }
            }
            case "ability" -> addRenderableWidget(Button.builder(Component.translatable("gui.lotmcraft.deceit_prepare.ability_mode." + config.getInt("abilityMode")), b -> {
                config.putInt("abilityMode", (config.getInt("abilityMode") + 1) % 2);
                rebuildWidgets();
            }).bounds(x, y + 30, 230, 18).build());
            default -> {
            }
        }
    }

    private void save() {
        storeTextFields();
        ResourceLocation item = ResourceLocation.tryParse(config.getString("itemDisguise"));
        if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) config.putString("itemDisguise", "minecraft:stick");
        CompoundTag data = new CompoundTag();
        data.put("config", config);
        PacketHandler.sendToServer(new ErrorActionPacket("deceit_config", data));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 200;
        int top = height / 2 - 110;
        int panelX = width / 2 - 30;

        graphics.drawString(font, title, left, top + 4, 0xFFC9B6FF, false);
        graphics.drawString(font, DeceitWheelScreen.name(selected), panelX, top + 4, 0xFFFFFFFF, false);
        graphics.drawWordWrap(font, Component.translatable("gui.lotmcraft.deceit." + DeceitWheelScreen.DECEITS[selected] + ".hint"), panelX, top + 20, 230, 0xFF9A8CC0);

        String key = DeceitWheelScreen.DECEITS[selected];
        if ("item".equals(key)) {
            graphics.drawString(font, Component.translatable("gui.lotmcraft.deceit_prepare.item_look"), panelX, top + 50, 0xFFB0A0D0, false);
            graphics.drawString(font, Component.translatable("gui.lotmcraft.deceit_prepare.item_name"), panelX, top + 82, 0xFFB0A0D0, false);
            ResourceLocation id = itemBox == null ? null : ResourceLocation.tryParse(itemBox.getValue());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)), panelX + 214, top + 140);
            }
        } else if ("divination".equals(key) && config.getInt("divMode") == 1) {
            graphics.drawString(font, Component.translatable("gui.lotmcraft.deceit_prepare.redirect"), panelX, top + 74, 0xFFB0A0D0, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
