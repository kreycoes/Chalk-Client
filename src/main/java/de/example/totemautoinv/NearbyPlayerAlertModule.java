package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Notifies only about player entities already tracked by the local client. */
public final class NearbyPlayerAlertModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum distance for alerts about visible player entities.")
        .defaultValue(64)
        .min(4)
        .max(256)
        .sliderRange(8, 128)
        .build()
    );
    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send a chat message when a newly tracked player enters range.")
        .defaultValue(true)
        .build()
    );

    private final Set<UUID> alertedPlayers = new HashSet<>();
    private int nearbyPlayers;

    public NearbyPlayerAlertModule() {
        super(TotemAutoInvAddon.CHALK_UTILITY, "nearby-player-alert", "Alerts about other player entities already visible to your client.");
    }

    @Override public void onActivate() { clear(); }
    @Override public void onDeactivate() { clear(); }
    @EventHandler private void onGameJoined(GameJoinedEvent event) { clear(); }
    @EventHandler private void onGameLeft(GameLeftEvent event) { clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) { clear(); return; }

        Set<UUID> current = new HashSet<>();
        nearbyPlayers = 0;
        int rangeSquared = range.get() * range.get();

        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || player.getUUID().equals(mc.player.getUUID()) || player.distanceToSqr(mc.player) > rangeSquared) continue;
            current.add(player.getUUID());
            nearbyPlayers++;
            if (chatAlerts.get() && alertedPlayers.add(player.getUUID())) info("Player detected nearby: %s.", player.getName().getString());
        }

        alertedPlayers.retainAll(current);
    }

    @Override public String getInfoString() { return Integer.toString(nearbyPlayers); }
    private void clear() { alertedPlayers.clear(); nearbyPlayers = 0; }
}
