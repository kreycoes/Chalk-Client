package de.example.totemautoinv;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Self-contained mouse display drawn by ChalkHudModule through Meteor's normal
 * HUD renderer. It observes input only; it never registers callbacks or
 * consumes Minecraft mouse input.
 */
public final class ChalkMouseHud {
    private static final double BASE_WIDTH = 70.0;
    private static final double MOUSE_HEIGHT = 108.0;
    private static final double INDICATOR_GAP = 7.0;
    private static final double INDICATOR_RADIUS = 13.0;

    private boolean hasLastMousePosition;
    private double lastMouseX;
    private double lastMouseY;
    private double directionX;
    private double directionY = -1.0;
    private double leftGlow;
    private double rightGlow;
    private double middleGlow;
    private double sideBackGlow;
    private double sideForwardGlow;

    public static double width(double scale) {
        return BASE_WIDTH * scale;
    }

    public static double height(double scale, boolean withDirectionIndicator) {
        double extra = withDirectionIndicator ? INDICATOR_GAP + INDICATOR_RADIUS * 2.0 : 0.0;
        return (MOUSE_HEIGHT + extra) * scale;
    }

    public void render(
        HudRenderer renderer, double x, double y, double scale, boolean showHighlights, boolean showDirection,
        SettingColor accent, SettingColor background, SettingColor surface, SettingColor text
    ) {
        updateInput(renderer, showHighlights);

        double width = width(scale);
        double height = MOUSE_HEIGHT * scale;
        double center = x + width / 2.0;

        // Reference-inspired silhouette: a tall, softly rounded black gaming
        // mouse with separated primary buttons, a recessed wheel and side keys.
        drawMouseShell(renderer, center, y, scale, background, surface);
        drawPrimaryButton(renderer, center, y, scale, true, leftGlow, accent);
        drawPrimaryButton(renderer, center, y, scale, false, rightGlow, accent);

        double seamX = center - 0.5 * scale;
        renderer.quad(seamX, y + 3.0 * scale, scale, 9.0 * scale, new Color(5, 5, 7, 245));
        renderer.quad(seamX, y + 39.0 * scale, scale, 10.0 * scale, new Color(5, 5, 7, 245));

        double wheelX = center - 6.0 * scale;
        double wheelY = y + 12.0 * scale;
        drawRoundedRect(renderer, wheelX - 2.0 * scale, wheelY - 2.0 * scale, 16.0 * scale, 29.0 * scale, 5.0 * scale, new Color(5, 5, 7, 245));
        drawRoundedRect(renderer, wheelX, wheelY, 12.0 * scale, 25.0 * scale, 4.0 * scale, new Color(30, 30, 34, 255));
        if (middleGlow > 0.02) {
            drawRoundedRect(renderer, wheelX, wheelY, 12.0 * scale, 25.0 * scale, 4.0 * scale, glowColor(accent, middleGlow, 165));
        }
        for (int index = 0; index < 7; index++) {
            renderer.quad(wheelX + 2.0 * scale, wheelY + (2.0 + index * 3.0) * scale, 8.0 * scale, scale, new Color(7, 7, 9, 235));
        }

        renderer.quad(center - 24.0 * scale, y + 51.0 * scale, 48.0 * scale, scale, new Color(67, 64, 75, 155));
        drawStatusLight(renderer, center, y + 65.0 * scale, scale);

        double sideX = center - mouseHalfWidthAt(0.56) * scale - 4.0 * scale;
        drawSideButton(renderer, sideX, y + 48.0 * scale, scale, sideBackGlow, accent);
        drawSideButton(renderer, sideX, y + 66.0 * scale, scale, sideForwardGlow, accent);

        if (showDirection) drawDirectionIndicator(renderer, center, y + height + INDICATOR_GAP * scale, scale, accent, background, text);
    }

    private void updateInput(HudRenderer renderer, boolean showHighlights) {
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        if (hasLastMousePosition) {
            double deltaX = mouseX - lastMouseX;
            double deltaY = mouseY - lastMouseY;
            double length = Math.hypot(deltaX, deltaY);
            if (length > 0.01) {
                directionX = deltaX / length;
                directionY = deltaY / length;
            }
        } else {
            hasLastMousePosition = true;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        double step = Math.min(1.0, Math.max(0.18, renderer.delta * 22.0));
        leftGlow = approach(leftGlow, showHighlights && minecraft.mouseHandler.isLeftPressed(), step);
        rightGlow = approach(rightGlow, showHighlights && minecraft.mouseHandler.isRightPressed(), step);
        middleGlow = approach(middleGlow, showHighlights && minecraft.mouseHandler.isMiddlePressed(), step);

        long window = minecraft.getWindow().handle();
        sideBackGlow = approach(sideBackGlow, showHighlights && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_4) == GLFW.GLFW_PRESS, step);
        sideForwardGlow = approach(sideForwardGlow, showHighlights && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_5) == GLFW.GLFW_PRESS, step);
    }

    private void drawDirectionIndicator(HudRenderer renderer, double centerX, double topY, double scale, SettingColor accent, SettingColor background, SettingColor text) {
        double radius = INDICATOR_RADIUS * scale;
        double centerY = topY + radius;
        drawCircle(renderer, centerX, centerY, radius + scale, new Color(11, 10, 17, 235), 14);
        drawCircle(renderer, centerX, centerY, radius, background, 14);
        drawCircle(renderer, centerX, centerY, radius - 2.0 * scale, new Color(26, 23, 38, 235), 14);

        double arrowLength = radius * 0.72;
        double tipX = centerX + directionX * arrowLength;
        double tipY = centerY + directionY * arrowLength;
        double perpendicularX = -directionY * radius * 0.24;
        double perpendicularY = directionX * radius * 0.24;
        double baseX = centerX - directionX * radius * 0.20;
        double baseY = centerY - directionY * radius * 0.20;
        renderer.triangle(tipX, tipY, baseX + perpendicularX, baseY + perpendicularY, baseX - perpendicularX, baseY - perpendicularY, accent);
        drawCircle(renderer, centerX, centerY, 2.0 * scale, text, 10);
    }

    private void drawCircle(HudRenderer renderer, double centerX, double centerY, double radius, Color color, int segments) {
        for (int index = 0; index < segments; index++) {
            double first = Math.PI * 2.0 * index / segments;
            double second = Math.PI * 2.0 * (index + 1) / segments;
            renderer.triangle(
                centerX, centerY,
                centerX + Math.cos(first) * radius, centerY + Math.sin(first) * radius,
                centerX + Math.cos(second) * radius, centerY + Math.sin(second) * radius,
                color
            );
        }
    }

    private void drawMouseShell(HudRenderer renderer, double center, double top, double scale, SettingColor background, SettingColor surface) {
        int rows = 72;
        double rowHeight = MOUSE_HEIGHT * scale / rows;
        for (int index = 0; index < rows; index++) {
            double progress = (index + 0.5) / rows;
            double shellHalf = mouseHalfWidthAt(progress) * scale;
            double y = top + index * rowHeight;
            renderer.quad(center - shellHalf - scale, y, (shellHalf + scale) * 2.0, rowHeight + 0.6, surface);

            Color fill = progress < 0.48
                ? new Color(25, 25, 28, background.a)
                : new Color(18, 18, 20, background.a);
            renderer.quad(center - shellHalf + scale, y + 0.35, Math.max(0.0, (shellHalf - scale) * 2.0), rowHeight, fill);
        }
    }

    private void drawPrimaryButton(HudRenderer renderer, double center, double top, double scale, boolean left, double glow, SettingColor accent) {
        int rows = 30;
        double topOffset = 3.0 * scale;
        double buttonHeight = 45.0 * scale;
        double rowHeight = buttonHeight / rows;
        for (int index = 0; index < rows; index++) {
            double local = (index + 0.5) / rows;
            double global = (3.0 + local * 45.0) / MOUSE_HEIGHT;
            double outer = mouseHalfWidthAt(global) * scale - 2.5 * scale;
            double inset = local > 0.88 ? 4.0 * scale : 0.0;
            double leftX = left ? center - outer + inset : center + scale;
            double width = outer - scale - inset;
            renderer.quad(leftX, top + topOffset + index * rowHeight, width, rowHeight + 0.5, new Color(33, 33, 36, 255));
            if (glow > 0.02) {
                renderer.quad(leftX, top + topOffset + index * rowHeight, width, rowHeight + 0.5, glowColor(accent, glow, 170));
            }
        }
    }

    private void drawSideButton(HudRenderer renderer, double x, double y, double scale, double glow, SettingColor accent) {
        drawRoundedRect(renderer, x, y, 6.0 * scale, 14.0 * scale, 2.0 * scale, new Color(9, 9, 11, 245));
        drawRoundedRect(renderer, x + scale, y + scale, 4.0 * scale, 12.0 * scale, 1.0 * scale, new Color(35, 35, 39, 255));
        if (glow > 0.02) {
            drawRoundedRect(renderer, x + scale, y + scale, 4.0 * scale, 12.0 * scale, scale, glowColor(accent, glow, 185));
        }
    }

    private void drawStatusLight(HudRenderer renderer, double centerX, double centerY, double scale) {
        drawCircle(renderer, centerX, centerY, 5.0 * scale, new Color(15, 65, 31, 75), 12);
        drawCircle(renderer, centerX, centerY, 2.75 * scale, new Color(25, 220, 76, 190), 12);
        drawCircle(renderer, centerX, centerY, 1.25 * scale, new Color(130, 255, 155, 255), 10);
    }

    private void drawRoundedRect(HudRenderer renderer, double x, double y, double width, double height, double radius, Color color) {
        int rows = Math.max(2, (int) Math.ceil(height / Math.max(1.0, radius / 2.0)));
        double rowHeight = height / rows;
        for (int index = 0; index < rows; index++) {
            double midY = (index + 0.5) * rowHeight;
            double edge = Math.min(midY, height - midY);
            double inset = edge >= radius ? 0.0 : radius - Math.sqrt(Math.max(0.0, radius * radius - (radius - edge) * (radius - edge)));
            renderer.quad(x + inset, y + index * rowHeight, Math.max(0.0, width - inset * 2.0), rowHeight + 0.5, color);
        }
    }

    private static double mouseHalfWidthAt(double progress) {
        if (progress < 0.12) return 17.0 + progress / 0.12 * 15.0;
        if (progress > 0.84) return 32.0 - (progress - 0.84) / 0.16 * 9.0;
        return 32.0;
    }

    private static double approach(double current, boolean pressed, double step) {
        double target = pressed ? 1.0 : 0.0;
        return current + (target - current) * step;
    }

    private static Color glowColor(SettingColor color, double strength, int maximumAlpha) {
        int alpha = (int) Math.round(Math.min(1.0, Math.max(0.0, strength)) * maximumAlpha);
        return new Color(color.r, color.g, color.b, alpha);
    }
}
