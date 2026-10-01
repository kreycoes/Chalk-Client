package de.example.totemautoinv;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Highlights chunks that contain a remote player entity currently tracked by
 * this client. It intentionally does not claim to know server chunk tickets.
 */
public final class PlayerChunkESP extends Module {
    private static final double MARKER_Y = 63.0;
    private static final double MARKER_THICKNESS = 0.02;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Maximum distance in chunks from you for remote-player chunk markers.")
        .defaultValue(12)
        .min(1)
        .max(32)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Boolean> includeSpectators = sgGeneral.add(new BoolSetting.Builder()
        .name("include-spectators")
        .description("Also mark chunks containing other spectator players tracked by your client.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgRender.add(new BoolSetting.Builder()
        .name("see-through-blocks")
        .description("Draw the white chunk boundary through normal blocks when Meteor rendering allows it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of the tracked remote-player chunk marker.")
        .defaultValue(new SettingColor(255, 80, 180, 255))
        .build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Lightly transparent fill color of the tracked remote-player chunk marker.")
        .defaultValue(new SettingColor(255, 80, 180, 35))
        .build()
    );

    // Rebuilt every client tick: no historical positions or stale chunks remain.
    private final LongOpenHashSet markedChunks = new LongOpenHashSet();
    private ClientLevel lastLevel;

    public PlayerChunkESP() {
        super(TotemAutoInvAddon.CHALK_ESP,
            "player-chunk-esp",
            "Marks loaded chunks that currently contain another player entity tracked by your client."
        );
    }

    @Override
    public void onActivate() {
        clear();
        lastLevel = mc.level;
    }

    @Override
    public void onDeactivate() {
        clear();
        lastLevel = null;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        clear();
        lastLevel = mc.level;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clear();
        lastLevel = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            clear();
            lastLevel = mc.level;
            return;
        }

        // Respawns and dimension switches replace the client level. Clear first
        // so no marker from the previous world can survive a frame.
        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
        }

        rebuildMarkers();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || markedChunks.isEmpty()) return;

        // Meteor's built-in LightOverlay uses event.renderer for its
        // see-through option and depthRenderer for normal depth testing.
        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        for (LongIterator iterator = markedChunks.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);

            int minX = chunkX << 4;
            int minZ = chunkZ << 4;

            // A thin horizontal 16x16 slab on Y=63 is a clear square marker
            // instead of a tall, perspective-distorted chunk box.
            renderer.box(minX, MARKER_Y, minZ, minX + 16, MARKER_Y + MARKER_THICKNESS, minZ + 16,
                fillColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }

    private void rebuildMarkers() {
        markedChunks.clear();

        int ownChunkX = blockToChunk(mc.player.getX());
        int ownChunkZ = blockToChunk(mc.player.getZ());
        int maximumDistance = renderDistance.get();
        int maximumDistanceSquared = maximumDistance * maximumDistance;

        for (AbstractClientPlayer player : mc.level.players()) {
            // Never mark our own chunk, even while the player entity is replaced
            // during a respawn or dimension transition.
            if (player == mc.player || player.getUUID().equals(mc.player.getUUID())) continue;
            if (!player.isAlive()) continue;
            if (!includeSpectators.get() && player.isSpectator()) continue;

            int chunkX = blockToChunk(player.getX());
            int chunkZ = blockToChunk(player.getZ());
            int deltaX = chunkX - ownChunkX;
            int deltaZ = chunkZ - ownChunkZ;
            if (deltaX * deltaX + deltaZ * deltaZ > maximumDistanceSquared) continue;

            // This is the direct client-side fact we require: the chunk is in
            // this client's chunk cache and contains a tracked remote player.
            if (!mc.level.hasChunk(chunkX, chunkZ)) continue;
            markedChunks.add(pack(chunkX, chunkZ));
        }
    }

    private void clear() {
        markedChunks.clear();
    }

    private static int blockToChunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
