package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/** Shared one-time warning logic for player health, hunger and inventory space. */
final class StatusAlertModule extends Module {
    enum Type { Health, Hunger, InventorySpace }

    private final Type type;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> threshold;
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send a chat message when the warning becomes active.")
        .defaultValue(true)
        .build()
    );

    private boolean warned;
    private int value;

    StatusAlertModule(String name, String description, Type type, int defaultThreshold) {
        super(TotemAutoInvAddon.CHALK_UTILITY, name, description);
        this.type = type;
        threshold = sgGeneral.add(new IntSetting.Builder()
            .name("threshold")
            .description(type == Type.InventorySpace ? "Alert when this many or fewer inventory slots are free." : "Alert when the value reaches this amount or lower.")
            .defaultValue(defaultThreshold)
            .min(0)
            .max(type == Type.Health ? 40 : type == Type.Hunger ? 20 : 36)
            .sliderRange(0, type == Type.Health ? 20 : type == Type.Hunger ? 20 : 18)
            .build()
        );
    }

    @Override
    public void onActivate() {
        warned = false;
        value = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        value = switch (type) {
            case Health -> Math.round(mc.player.getHealth() + mc.player.getAbsorptionAmount());
            case Hunger -> mc.player.getFoodData().getFoodLevel();
            case InventorySpace -> countFreeInventorySlots();
        };

        if (value <= threshold.get()) {
            if (!warned && chatAlerts.get()) info(message());
            warned = true;
        } else {
            warned = false;
        }
    }

    @Override
    public String getInfoString() {
        return Integer.toString(value);
    }

    private int countFreeInventorySlots() {
        int free = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) free++;
        }
        return free;
    }

    private String message() {
        return switch (type) {
            case Health -> "Health is low: " + value + ".";
            case Hunger -> "Hunger is low: " + value + ".";
            case InventorySpace -> "Inventory is almost full: " + value + " free slot(s).";
        };
    }
}
