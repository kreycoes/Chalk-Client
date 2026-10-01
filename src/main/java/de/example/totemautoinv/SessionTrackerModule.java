package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/** Session-only trackers for elapsed time, movement distance, jumps and activity. */
final class SessionTrackerModule extends Module {
    enum Type { SessionTime, Distance, Jumps, SneakTime, RideTime }
    private final Type type;
    private long startedAt;
    private double total;
    private double previousX, previousY, previousZ;
    private boolean hasPosition;
    private boolean wasOnGround;

    SessionTrackerModule(String name, String description, Type type) { super(TotemAutoInvAddon.CHALK_UTILITY, name, description); this.type = type; }
    @Override public void onActivate() { startedAt = System.currentTimeMillis(); total = 0; hasPosition = false; wasOnGround = false; }
    @EventHandler private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        switch (type) {
            case SessionTime -> { }
            case Distance -> trackDistance();
            case Jumps -> { if (wasOnGround && !mc.player.onGround()) total++; wasOnGround = mc.player.onGround(); }
            case SneakTime -> { if (mc.player.isShiftKeyDown()) total++; }
            case RideTime -> { if (mc.player.getVehicle() != null) total++; }
        }
    }
    @Override public String getInfoString() {
        return switch (type) {
            case SessionTime -> formatSeconds((System.currentTimeMillis() - startedAt) / 1000L);
            case Distance -> String.format("%.0f m", total);
            case Jumps -> Long.toString(Math.round(total));
            case SneakTime, RideTime -> formatSeconds(Math.round(total / 20.0));
        };
    }
    private void trackDistance() {
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (hasPosition) { double dx = x - previousX, dy = y - previousY, dz = z - previousZ; total += Math.sqrt(dx * dx + dy * dy + dz * dz); }
        previousX = x; previousY = y; previousZ = z; hasPosition = true;
    }
    private static String formatSeconds(long seconds) { return String.format("%d:%02d", seconds / 60L, seconds % 60L); }
}
