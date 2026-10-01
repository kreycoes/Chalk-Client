package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.BlockHitResult;

/** Applies Meteor's existing timer override only while the player is mining a block. */
public final class SpeedMineModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("multiplier")
        .description("Client timer multiplier used while mining.")
        .defaultValue(1.15)
        .min(1.0)
        .max(3.0)
        .sliderRange(1.0, 2.0)
        .build()
    );

    private final Setting<Boolean> onlyWhileMining = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-mining")
        .description("Apply the timer only while attacking a non-air block.")
        .defaultValue(true)
        .build()
    );

    private Timer timer;
    private boolean timerApplied;

    public SpeedMineModule() {
        super(TotemAutoInvAddon.CHALK_UTILITY, "chalk-speed-mine", "Uses a client timer multiplier while mining blocks.");
    }

    @Override
    public void onActivate() {
        timer = Modules.get().get(Timer.class);
        updateTimer();
    }

    @Override
    public void onDeactivate() {
        clearTimerOverride();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        updateTimer();
    }

    @Override
    public String getInfoString() {
        return timerApplied ? String.format("%.2fx", multiplier.get()) : "Idle";
    }

    private void updateTimer() {
        if (timer == null) timer = Modules.get().get(Timer.class);

        boolean shouldApply = !onlyWhileMining.get() || isMiningBlock();
        timer.setOverride(shouldApply ? multiplier.get() : Timer.OFF);
        timerApplied = shouldApply;
    }

    private void clearTimerOverride() {
        if (timer != null) timer.setOverride(Timer.OFF);
        timerApplied = false;
    }

    private boolean isMiningBlock() {
        if (mc.player == null || mc.level == null || !mc.options.keyAttack.isDown()) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return false;

        return !mc.level.getBlockState(hit.getBlockPos()).isAir();
    }
}
