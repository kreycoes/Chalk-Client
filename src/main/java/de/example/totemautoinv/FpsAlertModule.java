package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;

/** Warns once when the local frame rate falls below a configurable value. */
public final class FpsAlertModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> minimumFps = sgGeneral.add(new IntSetting.Builder().name("minimum-fps").description("Alert when the local FPS drops to this value or lower.").defaultValue(30).min(1).max(240).sliderRange(10, 120).build());
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder().name("chat-alerts").description("Send a chat message when FPS becomes low.").defaultValue(true).build());
    private int fps;
    private boolean warned;

    public FpsAlertModule() { super(TotemAutoInvAddon.CHALK_UTILITY, "fps-alert", "Alerts when your local frame rate is low."); }
    @Override public void onActivate() { fps = 0; warned = false; }
    @EventHandler private void onTick(TickEvent.Post event) {
        fps = Minecraft.getInstance().getFps();
        if (fps <= minimumFps.get()) { if (!warned && chatAlerts.get()) info("Low FPS: %d.", fps); warned = true; } else warned = false;
    }
    @Override public String getInfoString() { return Integer.toString(fps); }
}
