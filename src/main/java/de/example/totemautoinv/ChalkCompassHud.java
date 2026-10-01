package de.example.totemautoinv;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;

/** Compact, screen-space compass rendered by ChalkHudModule. */
public final class ChalkCompassHud {
    private static final double BASE_WIDTH = 152.0;
    private static final double BASE_HEIGHT = 26.0;
    private static final Marker[] MARKERS = {
        new Marker("N", 180.0), new Marker("NE", -135.0), new Marker("E", -90.0), new Marker("SE", -45.0),
        new Marker("S", 0.0), new Marker("SW", 45.0), new Marker("W", 90.0), new Marker("NW", 135.0)
    };

    public static double height(double scale) {
        return BASE_HEIGHT * scale;
    }

    public void render(HudRenderer renderer, int screenWidth, double y, double scale, SettingColor accent, SettingColor background, SettingColor text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        double width = BASE_WIDTH * scale;
        double height = BASE_HEIGHT * scale;
        double x = (screenWidth - width) / 2.0;
        double centerX = x + width / 2.0;

        renderer.quad(x, y, width, height, background);
        renderer.quad(x, y, width, scale, accent);
        renderer.quad(x, y + height - scale, width, scale, accent);
        renderer.quad(x, y, scale, height, accent);
        renderer.quad(x + width - scale, y, scale, height, accent);

        double lineY = y + height - 5.0 * scale;
        renderer.quad(x + 5.0 * scale, lineY, width - 10.0 * scale, scale, new Color(text.r, text.g, text.b, 100));

        double yaw = minecraft.player.getYRot();
        double displayRange = 95.0;
        double usableHalfWidth = width / 2.0 - 10.0 * scale;

        for (Marker marker : MARKERS) {
            double offset = wrapDegrees(marker.angle - yaw);
            if (Math.abs(offset) > displayRange) continue;

            double markerX = centerX + offset / displayRange * usableHalfWidth;
            double markerScale = marker.label.length() == 1 ? scale : scale * 0.82;
            SettingColor color = marker.label.equals("N") ? accent : text;
            double textWidth = renderer.textWidth(marker.label, false, markerScale);
            renderer.text(marker.label, markerX - textWidth / 2.0, y + 5.0 * scale, color, false, markerScale);
        }

        renderer.triangle(
            centerX, y + 2.0 * scale,
            centerX - 3.5 * scale, y + 8.0 * scale,
            centerX + 3.5 * scale, y + 8.0 * scale,
            accent
        );
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private record Marker(String label, double angle) { }
}
