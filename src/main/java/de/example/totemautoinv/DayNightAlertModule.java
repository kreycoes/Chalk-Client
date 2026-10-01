package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/** Announces day/night changes based on the world time already sent to the client. */
public final class DayNightAlertModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder().name("chat-alerts").description("Send a chat message when day or night begins.").defaultValue(true).build());
    private int phase = -1;

    public DayNightAlertModule() { super(TotemAutoInvAddon.CHALK_UTILITY, "day-night-alert", "Notifies you when the local world changes between day and night."); }
    @Override public void onActivate() { phase = -1; }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null) { phase = -1; return; }
        long time = Math.floorMod(mc.level.getDayTime(), 24000L);
        int current = time >= 12000L && time < 23000L ? 1 : 0;
        if (phase != -1 && current != phase && chatAlerts.get()) info(current == 1 ? "Night started." : "Day started.");
        phase = current;
    }

    @Override public String getInfoString() { return phase == 1 ? "Night" : "Day"; }
}
