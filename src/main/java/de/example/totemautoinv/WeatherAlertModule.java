package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/** Announces local weather transitions reported by the currently loaded world. */
public final class WeatherAlertModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder().name("chat-alerts").description("Send chat messages when weather changes.").defaultValue(true).build());
    private int weather = -1;

    public WeatherAlertModule() { super(TotemAutoInvAddon.CHALK_UTILITY, "weather-alert", "Notifies you when local rain or thunder starts or stops."); }
    @Override public void onActivate() { weather = -1; }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null) { weather = -1; return; }
        int current = mc.level.isThundering() ? 2 : mc.level.isRaining() ? 1 : 0;
        if (weather != -1 && current != weather && chatAlerts.get()) info(current == 2 ? "Thunder started." : current == 1 ? "Rain started." : "Weather cleared.");
        weather = current;
    }

    @Override public String getInfoString() { return switch (weather) { case 2 -> "Thunder"; case 1 -> "Rain"; default -> "Clear"; }; }
}
