package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;

/** Warns about a high number of already rendered nearby entities. */
public final class EntityCrowdAlertModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder().name("range").description("Distance used to count already rendered entities.").defaultValue(32).min(4).max(128).sliderRange(8, 64).build());
    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder().name("threshold").description("Alert when this many rendered entities are nearby.").defaultValue(80).min(1).max(1000).sliderRange(10, 250).build());
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder().name("chat-alerts").description("Send a chat message when entity density becomes high.").defaultValue(true).build());
    private boolean warned;
    private int entities;

    public EntityCrowdAlertModule() { super(TotemAutoInvAddon.CHALK_UTILITY, "entity-crowd-alert", "Alerts when many entities already rendered by your client are nearby."); }
    @Override public void onActivate() { warned = false; entities = 0; }
    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) { warned = false; entities = 0; return; }
        int rangeSquared = range.get() * range.get();
        entities = 0;
        for (Entity entity : mc.level.entitiesForRendering()) if (entity != mc.player && entity.distanceToSqr(mc.player) <= rangeSquared) entities++;
        if (entities >= threshold.get()) { if (!warned && chatAlerts.get()) info("High nearby entity count: %d.", entities); warned = true; } else warned = false;
    }
    @Override public String getInfoString() { return Integer.toString(entities); }
}
