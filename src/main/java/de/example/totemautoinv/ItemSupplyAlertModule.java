package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;

/** Shared implementation for single-item supply warnings. */
final class ItemSupplyAlertModule extends Module {
    private final Item item;
    private final String itemLabel;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> threshold;
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send a message when the item count falls to the threshold.")
        .defaultValue(true)
        .build()
    );

    private boolean warned;
    private int count;

    ItemSupplyAlertModule(String name, String description, Item item, String itemLabel, int defaultThreshold) {
        super(TotemAutoInvAddon.CHALK_UTILITY, name, description);
        this.item = item;
        this.itemLabel = itemLabel;
        threshold = sgGeneral.add(new IntSetting.Builder()
            .name("threshold")
            .description("Alert when you have this many or fewer items in your inventory and hotbar.")
            .defaultValue(defaultThreshold)
            .min(0)
            .max(2304)
            .sliderRange(0, Math.max(64, defaultThreshold * 4))
            .build()
        );
    }

    @Override
    public void onActivate() {
        warned = false;
        count = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        count = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) count += mc.player.getInventory().getItem(slot).getCount();
        }

        if (count <= threshold.get()) {
            if (!warned && chatAlerts.get()) info("%s supply is low: %d remaining.", itemLabel, count);
            warned = true;
        } else {
            warned = false;
        }
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }
}
