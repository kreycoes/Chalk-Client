package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Displays configured staff accounts that are present in the server-visible player list. */
public final class OnlineAdminsModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");

    private final Setting<List<String>> adminNames = sgGeneral.add(new StringListSetting.Builder()
        .name("admin-names")
        .description("Exact player names to check in the visible online-player list.")
        .defaultValue(
            "0Gsummer",
            "archivePedro",
            "BobIsFound",
            "frenk_btw",
            "napooo_",
            "Owen1212055",
            "W1zoX_",
            "DrDonutt"
        )
        .build()
    );

    private final Setting<Integer> maxEntries = sgGeneral.add(new IntSetting.Builder()
        .name("max-entries")
        .description("Maximum number of matching players displayed.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Boolean> showWhenEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("show-when-empty")
        .description("Keep the panel visible when no matching visible ranks are online.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> offsetX = sgAppearance.add(new IntSetting.Builder()
        .name("offset-x")
        .description("Distance from the right screen edge.")
        .defaultValue(8)
        .min(0)
        .max(500)
        .build()
    );

    private final Setting<Integer> offsetY = sgAppearance.add(new IntSetting.Builder()
        .name("offset-y")
        .description("Distance from the top screen edge.")
        .defaultValue(8)
        .min(0)
        .max(500)
        .build()
    );

    private final Setting<Double> scale = sgAppearance.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the online-admins panel.")
        .defaultValue(1.0)
        .min(0.75)
        .max(2.0)
        .sliderRange(0.75, 1.5)
        .build()
    );

    private final Setting<SettingColor> accentColor = sgAppearance.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("Panel outline and title color.")
        .defaultValue(new SettingColor(171, 82, 255, 255))
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgAppearance.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Panel background color.")
        .defaultValue(new SettingColor(16, 13, 27, 175))
        .build()
    );

    private final Setting<SettingColor> textColor = sgAppearance.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Player-name color.")
        .defaultValue(new SettingColor(245, 242, 255, 255))
        .build()
    );

    private final List<String> onlineAdmins = new ArrayList<>();

    public OnlineAdminsModule() {
        super(TotemAutoInvAddon.CHALK_UTILITY, "online-admins", "Shows visible tab-list admins or staff in the top-right corner.");
    }

    @Override
    public void onActivate() {
        refreshOnlineAdmins();
    }

    @Override
    public void onDeactivate() {
        onlineAdmins.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        refreshOnlineAdmins();
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (onlineAdmins.isEmpty() && !showWhenEmpty.get()) return;

        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(event.drawContext);
        renderPanel(renderer, event.screenWidth);
        renderer.end();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(onlineAdmins.size());
    }

    private void refreshOnlineAdmins() {
        onlineAdmins.clear();
        if (mc.getConnection() == null) return;

        for (PlayerInfo playerInfo : mc.getConnection().getOnlinePlayers()) {
            String accountName = playerInfo.getProfile().name();
            if (isConfiguredAdmin(accountName)) onlineAdmins.add(accountName);
        }

        onlineAdmins.sort(Comparator.naturalOrder());
        if (onlineAdmins.size() > maxEntries.get()) {
            onlineAdmins.subList(maxEntries.get(), onlineAdmins.size()).clear();
        }
    }

    private boolean isConfiguredAdmin(String accountName) {
        for (String configuredName : adminNames.get()) {
            if (!configuredName.isBlank() && configuredName.trim().equalsIgnoreCase(accountName)) return true;
        }

        return false;
    }

    private void renderPanel(HudRenderer renderer, int screenWidth) {
        double panelScale = scale.get();
        double padding = 5.0 * panelScale;
        double lineHeight = renderer.textHeight() * panelScale;
        String title = "Online Admins (" + onlineAdmins.size() + ")";
        List<String> lines = onlineAdmins.isEmpty() ? List.of("No visible admins") : onlineAdmins;

        double width = renderer.textWidth(title, false, panelScale);
        for (String line : lines) width = Math.max(width, renderer.textWidth(line, false, panelScale));
        width += padding * 2.0;

        double height = padding * 2.0 + lineHeight * (lines.size() + 1) + 3.0 * panelScale;
        double x = screenWidth - offsetX.get() - width;
        double y = offsetY.get();

        renderer.quad(x, y, width, height, backgroundColor.get());
        renderer.quad(x, y, width, panelScale, accentColor.get());
        renderer.quad(x, y + height - panelScale, width, panelScale, accentColor.get());
        renderer.quad(x, y, panelScale, height, accentColor.get());
        renderer.quad(x + width - panelScale, y, panelScale, height, accentColor.get());

        double textX = x + padding;
        double textY = y + padding;
        renderer.text(title, textX, textY, accentColor.get(), false, panelScale);
        textY += lineHeight + 3.0 * panelScale;
        for (String line : lines) {
            renderer.text(line, textX, textY, textColor.get(), false, panelScale);
            textY += lineHeight;
        }
    }
}
