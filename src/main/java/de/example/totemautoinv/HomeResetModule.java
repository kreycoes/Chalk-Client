package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

/** Resets one numbered server home to the player's current location. */
public final class HomeResetModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> homeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("home-slot")
        .description("Number of the server home to reset.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("set-delay")
        .description("Delay in ticks between deleting and setting the home.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Show a local confirmation after the home was reset.")
        .defaultValue(true)
        .build()
    );

    private int selectedHome;
    private int ticksRemaining;
    private boolean waitingToSet;

    public HomeResetModule() {
        super(TotemAutoInvAddon.CHALK_UTILITY, "home-reset", "Resets a numbered server home at your current position.");
    }

    @Override
    public void onActivate() {
        if (!isInWorld()) {
            error("Join a world before resetting a home.");
            toggle();
            return;
        }

        selectedHome = homeSlot.get();
        ticksRemaining = delayTicks.get();
        waitingToSet = true;

        ChatUtils.sendPlayerMsg("/delhome " + selectedHome);
    }

    @Override
    public void onDeactivate() {
        waitingToSet = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!waitingToSet) return;

        if (!isInWorld()) {
            error("Home reset cancelled because you left the world.");
            toggle();
            return;
        }

        if (--ticksRemaining > 0) return;

        waitingToSet = false;
        ChatUtils.sendPlayerMsg("/sethome " + selectedHome);

        if (chatFeedback.get()) info("Home %d reset at your current position.", selectedHome);
        toggle();
    }

    private boolean isInWorld() {
        return mc.player != null && mc.level != null;
    }
}
