package de.example.totemautoinv;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** A compact Chalk HUD with movement keys, FPS and equipped armor. */
public final class ChalkOverlayHud extends HudElement {
    public static final HudElementInfo<ChalkOverlayHud> INFO = new HudElementInfo<>(
        TotemAutoInvAddon.CHALK_HUD,
        "chalk-hud",
        "Chalk HUD",
        "Displays WASD keys, FPS and equipped armor.",
        ChalkOverlayHud::new
    );

    private static final double PADDING = 6.0;
    private static final double GAP = 3.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");

    private final Setting<Boolean> showKeys = sgGeneral.add(new BoolSetting.Builder()
        .name("show-keys")
        .description("Show the WASD movement keys.")
        .defaultValue(true)
        .onChanged(value -> calculateSize())
        .build()
    );

    private final Setting<Boolean> showFps = sgGeneral.add(new BoolSetting.Builder()
        .name("show-fps")
        .description("Show the current client FPS.")
        .defaultValue(true)
        .onChanged(value -> calculateSize())
        .build()
    );

    private final Setting<Boolean> showArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("show-armor")
        .description("Show the currently equipped armor and its durability bars.")
        .defaultValue(true)
        .onChanged(value -> calculateSize())
        .build()
    );

    private final Setting<Double> keySize = sgAppearance.add(new DoubleSetting.Builder()
        .name("key-size")
        .description("Size of each WASD key button.")
        .defaultValue(24.0)
        .min(16.0)
        .max(48.0)
        .sliderRange(16.0, 36.0)
        .onChanged(value -> calculateSize())
        .build()
    );

    private final Setting<Double> armorScale = sgAppearance.add(new DoubleSetting.Builder()
        .name("armor-scale")
        .description("Scale of armor item icons.")
        .defaultValue(1.25)
        .min(0.75)
        .max(2.0)
        .sliderRange(0.75, 1.5)
        .onChanged(value -> calculateSize())
        .build()
    );

    private final Setting<SettingColor> accentColor = sgAppearance.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("Color used for pressed keys and panel outlines.")
        .defaultValue(new SettingColor(171, 82, 255, 255))
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgAppearance.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Panel background color.")
        .defaultValue(new SettingColor(16, 13, 27, 175))
        .build()
    );

    private final Setting<SettingColor> keyColor = sgAppearance.add(new ColorSetting.Builder()
        .name("key-color")
        .description("Color used for unpressed key buttons.")
        .defaultValue(new SettingColor(48, 40, 70, 220))
        .build()
    );

    private final Setting<SettingColor> textColor = sgAppearance.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color used for labels and the FPS value.")
        .defaultValue(new SettingColor(245, 242, 255, 255))
        .build()
    );

    public ChalkOverlayHud() {
        super(INFO);
        calculateSize();
    }

    @Override
    public void tick(HudRenderer renderer) {
        calculateSize();
    }

    @Override
    public void render(HudRenderer renderer) {
        renderAt(renderer, x, y);
    }

    /** Renders Chalk HUD directly, without registering or enabling Meteor's global HUD. */
    public void renderStandalone(HudRenderer renderer) {
        renderAt(renderer, 8.0, 8.0);
    }

    private void renderAt(HudRenderer renderer, double panelX, double panelY) {
        double width = getWidth();
        double height = getHeight();

        drawPanel(renderer, panelX, panelY, width, height);

        double cursorY = panelY + PADDING;

        if (showFps.get()) {
            renderer.text("FPS: " + Minecraft.getInstance().getFps(), panelX + PADDING, cursorY, textColor.get(), false);
            cursorY += renderer.textHeight() + PADDING;
        }

        if (showKeys.get()) {
            double size = keySize.get();
            double startX = panelX + (width - (size * 3.0 + GAP * 2.0)) / 2.0;

            drawKey(renderer, "W", startX + size + GAP, cursorY, size, isForwardPressed());
            cursorY += size + GAP;
            drawKey(renderer, "A", startX, cursorY, size, isLeftPressed());
            drawKey(renderer, "S", startX + size + GAP, cursorY, size, isBackPressed());
            drawKey(renderer, "D", startX + (size + GAP) * 2.0, cursorY, size, isRightPressed());
            cursorY += size + PADDING;
        }

        if (showArmor.get()) {
            double scale = armorScale.get();
            double iconSize = 16.0 * scale;
            double totalWidth = iconSize * 4.0 + GAP * 3.0;
            double startX = panelX + (width - totalWidth) / 2.0;
            ItemStack[] armor = getArmor();

            for (int index = 0; index < armor.length; index++) {
                double armorX = startX + index * (iconSize + GAP);
                renderer.quad(armorX - 1.0, cursorY - 1.0, iconSize + 2.0, iconSize + 2.0, keyColor.get());
            }

            double armorY = cursorY;
            renderer.post(() -> {
                for (int index = 0; index < armor.length; index++) {
                    double armorX = startX + index * (iconSize + GAP);
                    ItemStack stack = armor[index];
                    renderer.item(stack, (int) armorX, (int) armorY, (float) scale, stack.isDamageableItem());
                }
            });
        }
    }

    private void calculateSize() {
        double width = PADDING * 2.0;
        double height = PADDING * 2.0;

        if (showKeys.get()) {
            width = Math.max(width, PADDING * 2.0 + keySize.get() * 3.0 + GAP * 2.0);
            height += keySize.get() * 2.0 + GAP + PADDING;
        }

        if (showFps.get()) {
            width = Math.max(width, 64.0);
            height += 9.0 + PADDING;
        }

        if (showArmor.get()) {
            width = Math.max(width, PADDING * 2.0 + armorScale.get() * 16.0 * 4.0 + GAP * 3.0);
            height += armorScale.get() * 16.0;
        }

        setSize(width, height);
    }

    private void drawPanel(HudRenderer renderer, double panelX, double panelY, double width, double height) {
        renderer.quad(panelX, panelY, width, height, backgroundColor.get());
        renderer.quad(panelX, panelY, width, 1.0, accentColor.get());
        renderer.quad(panelX, panelY + height - 1.0, width, 1.0, accentColor.get());
        renderer.quad(panelX, panelY, 1.0, height, accentColor.get());
        renderer.quad(panelX + width - 1.0, panelY, 1.0, height, accentColor.get());
    }

    private void drawKey(HudRenderer renderer, String label, double keyX, double keyY, double width, double height, boolean pressed) {
        renderer.quad(keyX, keyY, width, height, pressed ? accentColor.get() : keyColor.get());
        renderer.quad(keyX, keyY, width, 1.0, accentColor.get());
        renderer.quad(keyX, keyY + height - 1.0, width, 1.0, accentColor.get());
        renderer.quad(keyX, keyY, 1.0, height, accentColor.get());
        renderer.quad(keyX + width - 1.0, keyY, 1.0, height, accentColor.get());

        double textWidth = renderer.textWidth(label);
        double textY = keyY + (height - renderer.textHeight()) / 2.0;
        renderer.text(label, keyX + (width - textWidth) / 2.0, textY, textColor.get(), false);
    }

    private void drawKey(HudRenderer renderer, String label, double keyX, double keyY, double size, boolean pressed) {
        drawKey(renderer, label, keyX, keyY, size, size, pressed);
    }

    private boolean isForwardPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && minecraft.options.keyUp.isDown();
    }

    private boolean isBackPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && minecraft.options.keyDown.isDown();
    }

    private boolean isLeftPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && minecraft.options.keyLeft.isDown();
    }

    private boolean isRightPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && minecraft.options.keyRight.isDown();
    }

    private ItemStack[] getArmor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };

        return new ItemStack[] {
            minecraft.player.getItemBySlot(EquipmentSlot.HEAD),
            minecraft.player.getItemBySlot(EquipmentSlot.CHEST),
            minecraft.player.getItemBySlot(EquipmentSlot.LEGS),
            minecraft.player.getItemBySlot(EquipmentSlot.FEET)
        };
    }

    public SettingColor accentColor() {
        return accentColor.get();
    }

    public SettingColor backgroundColor() {
        return backgroundColor.get();
    }

    public SettingColor keyColor() {
        return keyColor.get();
    }

    public SettingColor textColor() {
        return textColor.get();
    }
}
