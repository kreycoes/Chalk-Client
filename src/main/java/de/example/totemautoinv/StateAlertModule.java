package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;

/** Reusable one-shot notification for local player states. */
final class StateAlertModule extends Module {
    enum Type { EmptyOffhand, MissingArmor, Burning, LowAir, InWater, InLava, Mounted }

    private final Type type;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder().name("chat-alerts").description("Send a message when this state begins.").defaultValue(true).build());
    private final Setting<Integer> airThreshold;

    private boolean active;

    StateAlertModule(String name, String description, Type type) {
        super(TotemAutoInvAddon.CHALK_UTILITY, name, description);
        this.type = type;
        airThreshold = sgGeneral.add(new IntSetting.Builder()
            .name("air-threshold").description("Alert when remaining air reaches this amount or lower.").defaultValue(60).min(0).max(300).sliderRange(0, 150)
            .visible(() -> this.type == Type.LowAir).build());
    }

    @Override public void onActivate() { active = false; }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) { active = false; return; }
        boolean current = switch (type) {
            case EmptyOffhand -> mc.player.getOffhandItem().isEmpty();
            case MissingArmor -> mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || mc.player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || mc.player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            case Burning -> mc.player.isOnFire();
            case LowAir -> mc.player.getAirSupply() <= airThreshold.get();
            case InWater -> mc.player.isInWater();
            case InLava -> mc.player.isInLava();
            case Mounted -> mc.player.getVehicle() != null;
        };
        if (current && !active && chatAlerts.get()) info(message());
        active = current;
    }

    @Override public String getInfoString() { return active ? "Active" : "Ready"; }

    private String message() {
        return switch (type) {
            case EmptyOffhand -> "Offhand is empty.";
            case MissingArmor -> "At least one armor slot is empty.";
            case Burning -> "You are on fire.";
            case LowAir -> "Air supply is low.";
            case InWater -> "You entered water.";
            case InLava -> "You entered lava.";
            case Mounted -> "You mounted a vehicle.";
        };
    }
}
