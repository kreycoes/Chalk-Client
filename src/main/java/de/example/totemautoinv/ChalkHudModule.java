package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

/** Renders Chalk's compact overlay without enabling Meteor's global HUD system. */
public final class ChalkHudModule extends Module {
    private final ChalkOverlayHud overlay = new ChalkOverlayHud();
    private final ChalkMouseHud mouseHud = new ChalkMouseHud();
    private final ChalkCompassHud compassHud = new ChalkCompassHud();
    private final SettingGroup sgMouseHud = settings.createGroup("Mouse HUD");
    private final SettingGroup sgCompassHud = settings.createGroup("Compass HUD");

    private final Setting<Boolean> mouseHudEnabled = sgMouseHud.add(new BoolSetting.Builder()
        .name("mouse-hud")
        .description("Show Chalk's interactive mouse display.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> mouseHudScale = sgMouseHud.add(new DoubleSetting.Builder()
        .name("mouse-scale")
        .description("Scale of the mouse display.")
        .defaultValue(1.0)
        .min(0.5)
        .max(2.5)
        .sliderRange(0.5, 1.75)
        .build()
    );

    private final Setting<Integer> mouseHudX = sgMouseHud.add(new IntSetting.Builder()
        .name("mouse-x")
        .description("Horizontal HUD position in scaled screen pixels.")
        .defaultValue(124)
        .min(0)
        .max(10000)
        .build()
    );

    private final Setting<Integer> mouseHudY = sgMouseHud.add(new IntSetting.Builder()
        .name("mouse-y")
        .description("Vertical HUD position in scaled screen pixels.")
        .defaultValue(8)
        .min(0)
        .max(10000)
        .build()
    );

    private final Setting<Boolean> clickHighlights = sgMouseHud.add(new BoolSetting.Builder()
        .name("click-highlights")
        .description("Highlight mouse buttons while they are pressed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> directionIndicator = sgMouseHud.add(new BoolSetting.Builder()
        .name("direction-indicator")
        .description("Show the circular indicator for real mouse movement direction.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> compassHudEnabled = sgCompassHud.add(new BoolSetting.Builder()
        .name("compass-hud")
        .description("Show Chalk's compact compass at the top center of the screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> compassHudScale = sgCompassHud.add(new DoubleSetting.Builder()
        .name("compass-scale")
        .description("Scale of the compass display.")
        .defaultValue(1.0)
        .min(0.75)
        .max(2.0)
        .sliderRange(0.75, 1.5)
        .build()
    );

    private final Setting<Integer> compassHudY = sgCompassHud.add(new IntSetting.Builder()
        .name("compass-y")
        .description("Vertical compass position in scaled screen pixels.")
        .defaultValue(8)
        .min(0)
        .max(10000)
        .build()
    );

    public ChalkHudModule() {
        super(TotemAutoInvAddon.KREY_ADDON,
            "chalk-hud",
            "Shows a movable HUD with WASD, mouse movement, FPS and armor."
        );
    }

    @Override
    public void onActivate() {
        Hud hud = Hud.get();
        if (hud == null) return;

        // Earlier releases enabled this global switch, which also displayed every
        // configured Meteor HUD element. Chalk now renders independently below.
        hud.active = false;
        ChalkOverlayHud overlay = getOverlay(hud);
        // Prevent an old, saved Chalk HUD element from being drawn a second time
        // if the user later manually enables Meteor's own HUD.
        if (overlay != null && overlay.isActive()) overlay.toggle();
    }

    @Override
    public void onDeactivate() {
        // Meteor's HUD state is intentionally left untouched on deactivation.
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(event.drawContext);
        overlay.tick(renderer);
        overlay.renderStandalone(renderer);
        if (mouseHudEnabled.get()) {
            double scale = mouseHudScale.get();
            double maxX = Math.max(0.0, event.screenWidth - ChalkMouseHud.width(scale) - 4.0);
            double maxY = Math.max(0.0, event.screenHeight - ChalkMouseHud.height(scale, directionIndicator.get()) - 4.0);
            double x = Math.min(mouseHudX.get(), maxX);
            double y = Math.min(mouseHudY.get(), maxY);

            mouseHud.render(
                renderer, x, y, scale, clickHighlights.get(), directionIndicator.get(),
                overlay.accentColor(), overlay.backgroundColor(), overlay.keyColor(), overlay.textColor()
            );
        }
        if (compassHudEnabled.get()) {
            double scale = compassHudScale.get();
            double maxY = Math.max(0.0, event.screenHeight - ChalkCompassHud.height(scale) - 4.0);
            compassHud.render(
                renderer, event.screenWidth, Math.min(compassHudY.get(), maxY), scale,
                overlay.accentColor(), overlay.backgroundColor(), overlay.textColor()
            );
        }
        renderer.end();
    }

    private ChalkOverlayHud getOverlay(Hud hud) {
        for (HudElement element : hud) {
            if (element instanceof ChalkOverlayHud overlay) return overlay;
        }

        return null;
    }
}
