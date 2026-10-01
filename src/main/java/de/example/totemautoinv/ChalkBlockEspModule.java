package de.example.totemautoinv;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Shared, client-only scanner for Chalk's block ESP modules. It only examines
 * chunks already held by the client and processes the scan queue gradually so
 * a large render distance cannot produce one long frame.
 */
abstract class ChalkBlockEspModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanDistance;
    private final Setting<Integer> rescanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("Ticks between queueing a fresh scan of loaded chunks.")
        .defaultValue(200)
        .min(20)
        .max(1200)
        .sliderRange(20, 400)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Queued chunk scans processed per tick. Higher values update faster but use more CPU.")
        .defaultValue(2)
        .min(1)
        .max(4)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> maximumMarkers = sgGeneral.add(new IntSetting.Builder()
        .name("maximum-markers")
        .description("Maximum number of block markers rendered at once.")
        .defaultValue(512)
        .min(16)
        .max(4096)
        .sliderRange(16, 1024)
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgRender.add(new BoolSetting.Builder()
        .name("see-through-blocks")
        .description("Render markers through normal blocks when Meteor rendering allows it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor;
    private final Setting<SettingColor> sideColor;

    private final LongOpenHashSet markers = new LongOpenHashSet();
    private final LongOpenHashSet queuedChunks = new LongOpenHashSet();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();

    private ClientLevel lastLevel;
    private int ticksUntilRescan;
    private int renderedMarkers;

    protected ChalkBlockEspModule(String name, String description, int defaultScanDistance, SettingColor defaultLineColor, SettingColor defaultSideColor) {
        super(TotemAutoInvAddon.CHALK_ESP, name, description);

        scanDistance = sgGeneral.add(new IntSetting.Builder()
            .name("scan-distance")
            .description("Maximum chunk distance from you to scan and render.")
            .defaultValue(defaultScanDistance)
            .min(1)
            .max(32)
            .sliderRange(1, 16)
            .build()
        );

        lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Outline color of matching blocks.")
            .defaultValue(defaultLineColor)
            .build()
        );

        sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Transparent fill color of matching blocks.")
            .defaultValue(defaultSideColor)
            .build()
        );
    }

    /** Return true only for a block which this specific module should render. */
    protected abstract boolean matches(BlockPos pos, BlockState state);

    @Override
    public void onActivate() {
        clear();
        lastLevel = mc.level;
        ticksUntilRescan = 0;
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
        ticksUntilRescan = 0;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clear();
        lastLevel = null;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.level == null) return;
        queueIfInRange(event.chunk().getPos().x, event.chunk().getPos().z);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.player == null || mc.level == null || !isInRange(event.pos.getX() >> 4, event.pos.getZ() >> 4)) return;

        long key = event.pos.asLong();
        if (matches(event.pos, event.newState)) markers.add(key);
        else markers.remove(key);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            clear();
            lastLevel = mc.level;
            return;
        }

        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
            ticksUntilRescan = 0;
        }

        if (ticksUntilRescan-- <= 0) {
            queueLoadedChunksInRange();
            ticksUntilRescan = rescanInterval.get();
        }

        processScanQueue();
        removeUnavailableMarkers();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || markers.isEmpty()) return;

        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        int limit = maximumMarkers.get();
        int count = 0;

        for (LongIterator iterator = markers.iterator(); iterator.hasNext() && count < limit;) {
            BlockPos pos = BlockPos.of(iterator.nextLong());
            renderer.box(pos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            count++;
        }

        renderedMarkers = count;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(renderedMarkers);
    }

    private void queueLoadedChunksInRange() {
        int ownChunkX = blockToChunk(mc.player.getX());
        int ownChunkZ = blockToChunk(mc.player.getZ());
        int distance = scanDistance.get();

        for (int chunkX = ownChunkX - distance; chunkX <= ownChunkX + distance; chunkX++) {
            for (int chunkZ = ownChunkZ - distance; chunkZ <= ownChunkZ + distance; chunkZ++) {
                if (mc.level.hasChunk(chunkX, chunkZ)) queueIfInRange(chunkX, chunkZ);
            }
        }
    }

    private void queueIfInRange(int chunkX, int chunkZ) {
        if (!isInRange(chunkX, chunkZ)) return;

        long key = packChunk(chunkX, chunkZ);
        if (queuedChunks.add(key)) scanQueue.addLast(key);
    }

    private void processScanQueue() {
        int remaining = chunksPerTick.get();
        while (remaining-- > 0 && !scanQueue.isEmpty()) {
            long key = scanQueue.removeFirst();
            queuedChunks.remove(key);

            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) continue;

            scanChunk(chunkX, chunkZ);
        }
    }

    private void scanChunk(int chunkX, int chunkZ) {
        LevelChunk chunk = mc.level.getChunk(chunkX, chunkZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;

        // Clear stale matches from this one chunk before replacing them with the
        // authoritative current block states from the already-loaded chunk.
        for (LongIterator iterator = markers.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            BlockPos existing = BlockPos.of(key);
            if ((existing.getX() >> 4) == chunkX && (existing.getZ() >> 4) == chunkZ) iterator.remove();
        }

        for (int y = mc.level.getMinY(); y < mc.level.getMaxY(); y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    pos.set(minX + localX, y, minZ + localZ);
                    if (matches(pos, chunk.getBlockState(pos))) markers.add(pos.asLong());
                }
            }
        }
    }

    private void removeUnavailableMarkers() {
        for (LongIterator iterator = markers.iterator(); iterator.hasNext();) {
            BlockPos pos = BlockPos.of(iterator.nextLong());
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !isInRange(pos.getX() >> 4, pos.getZ() >> 4)) {
                iterator.remove();
            }
        }

        for (Iterator<Long> iterator = scanQueue.iterator(); iterator.hasNext();) {
            long key = iterator.next();
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) {
                iterator.remove();
                queuedChunks.remove(key);
            }
        }
    }

    private boolean isInRange(int chunkX, int chunkZ) {
        if (mc.player == null) return false;

        int deltaX = chunkX - blockToChunk(mc.player.getX());
        int deltaZ = chunkZ - blockToChunk(mc.player.getZ());
        int maximum = scanDistance.get();
        return deltaX * deltaX + deltaZ * deltaZ <= maximum * maximum;
    }

    private void clear() {
        markers.clear();
        queuedChunks.clear();
        scanQueue.clear();
        renderedMarkers = 0;
    }

    private static int blockToChunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackChunkZ(long packed) {
        return (int) packed;
    }
}
