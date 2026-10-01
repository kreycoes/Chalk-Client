package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Warns once per low-durability state without modifying inventory items. */
public final class DurabilityAlertModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold-percent")
        .description("Alert when an equipped armor piece or held item has this durability percentage or less.")
        .defaultValue(15)
        .min(1)
        .max(100)
        .sliderRange(1, 50)
        .build()
    );
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send a chat message when an item becomes low on durability.")
        .defaultValue(true)
        .build()
    );

    private boolean warned;
    private int lowestPercent = 100;

    public DurabilityAlertModule() {
        super(TotemAutoInvAddon.CHALK_UTILITY, "durability-alert", "Alerts when equipped armor or held items are low on durability.");
    }

    @Override
    public void onActivate() {
        warned = false;
        lowestPercent = 100;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        lowestPercent = 100;
        check(mc.player.getMainHandItem());
        check(mc.player.getOffhandItem());
        check(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        check(mc.player.getItemBySlot(EquipmentSlot.CHEST));
        check(mc.player.getItemBySlot(EquipmentSlot.LEGS));
        check(mc.player.getItemBySlot(EquipmentSlot.FEET));

        if (lowestPercent <= threshold.get()) {
            if (!warned && chatAlerts.get()) info("An equipped item is low on durability: %d%% remaining.", lowestPercent);
            warned = true;
        } else {
            warned = false;
        }
    }

    @Override
    public String getInfoString() {
        return lowestPercent == 100 ? null : lowestPercent + "%";
    }

    private void check(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return;
        int percent = Math.round((stack.getMaxDamage() - stack.getDamageValue()) * 100.0f / stack.getMaxDamage());
        lowestPercent = Math.min(lowestPercent, percent);
    }
}
