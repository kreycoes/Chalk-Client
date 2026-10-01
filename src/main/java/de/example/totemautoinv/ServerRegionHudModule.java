package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shows a server-provided region label such as "Europe 109" from visible client data. */
public final class ServerRegionHudModule extends Module {
    private static final Pattern REGION_PATTERN = Pattern.compile(
        "\\b(?:Europe|EU|North America|NA|USA|Asia|AS|Oceania|OC|Australia|AU|Lobby|Hub)\\s*(?:[-#:]\\s*)?\\d+\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");

    private final Setting<String> manualLabel = sgGeneral.add(new StringSetting.Builder()
        .name("manual-label")
        .description("Optional label shown instead of automatic server-region detection.")
        .defaultValue("")
        .placeholder("Europe 109")
        .wide()
        .build()
    );

    private final Setting<Boolean> showAddressFallback = sgGeneral.add(new BoolSetting.Builder()
        .name("show-address-fallback")
        .description("Show the current server name or address when no region is visible to the client.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> offsetY = sgAppearance.add(new IntSetting.Builder()
        .name("offset-y")
        .description("Distance from the top screen edge.")
        .defaultValue(39)
        .min(0)
        .max(500)
        .build()
    );

    private final Setting<Double> scale = sgAppearance.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the server-region label.")
        .defaultValue(1.0)
        .min(0.75)
        .max(2.0)
        .sliderRange(0.75, 1.5)
        .build()
    );

    private final Setting<SettingColor> accentColor = sgAppearance.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("Panel outline and label color.")
        .defaultValue(new SettingColor(171, 82, 255, 255))
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgAppearance.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Panel background color.")
        .defaultValue(new SettingColor(16, 13, 27, 175))
        .build()
    );

    private String regionLabel = "No server";

    public ServerRegionHudModule() {
        super(TotemAutoInvAddon.CHALK_UTILITY, "server-region", "Shows the current visible server region, such as Europe 109.");
    }

    @Override
    public void onActivate() {
        refreshLabel();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        refreshLabel();
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(event.drawContext);

        double panelScale = scale.get();
        String text = "Server: " + regionLabel;
        double padding = 5.0 * panelScale;
        double width = renderer.textWidth(text, false, panelScale) + padding * 2.0;
        double height = renderer.textHeight(false, panelScale) + padding * 2.0;
        double x = (event.screenWidth - width) / 2.0;
        double y = Math.min(offsetY.get(), Math.max(0.0, event.screenHeight - height - 4.0));

        renderer.quad(x, y, width, height, backgroundColor.get());
        renderer.quad(x, y, width, panelScale, accentColor.get());
        renderer.quad(x, y + height - panelScale, width, panelScale, accentColor.get());
        renderer.quad(x, y, panelScale, height, accentColor.get());
        renderer.quad(x + width - panelScale, y, panelScale, height, accentColor.get());
        renderer.text(text, x + padding, y + padding, accentColor.get(), false, panelScale);

        renderer.end();
    }

    @Override
    public String getInfoString() {
        return regionLabel;
    }

    private void refreshLabel() {
        String configuredLabel = manualLabel.get().trim();
        if (!configuredLabel.isEmpty()) {
            regionLabel = configuredLabel;
            return;
        }

        if (mc.level == null) {
            regionLabel = "No server";
            return;
        }

        String detectedRegion = findVisibleRegion();
        if (detectedRegion != null) {
            regionLabel = detectedRegion;
            return;
        }

        if (mc.isLocalServer()) {
            regionLabel = "Singleplayer";
            return;
        }

        ServerData server = mc.getCurrentServer();
        if (showAddressFallback.get() && server != null) {
            regionLabel = !server.name.isBlank() ? server.name : server.ip;
        } else {
            regionLabel = "Unknown";
        }
    }

    private String findVisibleRegion() {
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return null;

        List<String> candidates = new ArrayList<>();
        candidates.add(sidebar.getDisplayName().getString());
        for (PlayerScoreEntry score : scoreboard.listPlayerScores(sidebar)) candidates.add(score.owner());

        for (String candidate : candidates) {
            Matcher matcher = REGION_PATTERN.matcher(candidate);
            if (matcher.find()) return matcher.group().replaceAll("\\s+", " ").trim();
        }

        return null;
    }
}
