package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/** Tracks the current chunk and can announce normal player chunk transitions. */
public final class ChunkTransitionModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder().name("chat-alerts").description("Send a chat message when you cross a chunk border.").defaultValue(false).build());
    private int chunkX;
    private int chunkZ;
    private boolean initialized;

    public ChunkTransitionModule() { super(TotemAutoInvAddon.CHALK_UTILITY, "chunk-transition", "Tracks and optionally announces your normal chunk-border crossings."); }
    @Override public void onActivate() { initialized = false; }
    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null) { initialized = false; return; }
        int currentX = ((int) Math.floor(mc.player.getX())) >> 4;
        int currentZ = ((int) Math.floor(mc.player.getZ())) >> 4;
        if (initialized && (currentX != chunkX || currentZ != chunkZ) && chatAlerts.get()) info("Entered chunk %d, %d.", currentX, currentZ);
        chunkX = currentX; chunkZ = currentZ; initialized = true;
    }
    @Override public String getInfoString() { return initialized ? chunkX + ", " + chunkZ : null; }
}
