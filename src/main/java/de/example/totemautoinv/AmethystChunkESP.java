package de.example.totemautoinv;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Caches the number of fully-grown amethyst clusters in client-loaded chunks
 * and outlines chunks which meet the configured minimum.
 *
 * <p>Only {@link Blocks#AMETHYST_CLUSTER} is counted. Small, medium and large
 * buds are distinct, immature vanilla blocks and are deliberately excluded.</p>
 */
public final class AmethystChunkESP extends Module {
    private static final double MARKER_Y = 63.0;
    private static final double MARKER_THICKNESS = 0.02;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minimumAmethystCount = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-amethyst-count")
        .description("Minimum number of fully-grown amethyst clusters required to mark a chunk.")
        .defaultValue(5)
        .min(1)
        .max(512)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> scanDistance = sgGeneral.add(new IntSetting.Builder()
        .name("scan-distance")
        .description("Maximum distance in chunks from you that is scanned and highlighted.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> rescanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("Ticks between background rescans of client-loaded chunks.")
        .defaultValue(200)
        .min(20)
        .max(1200)
        .sliderRange(20, 400)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Maximum queued chunk scans per tick. Higher values update faster but can cause lag.")
        .defaultValue(1)
        .min(1)
        .max(4)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Boolean> outline = sgRender.add(new BoolSetting.Builder()
        .name("outline")
        .description("Draw a pink outline along the marked chunk boundaries.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fill = sgRender.add(new BoolSetting.Builder()
        .name("fill")
        .description("Fill marked chunks with a lightly transparent pink color.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> outlineColor = sgRender.add(new ColorSetting.Builder()
        .name("outline-color")
        .description("Outline color for marked chunks.")
        .defaultValue(new SettingColor(255, 80, 180, 255))
        .build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Transparent fill color for marked chunks.")
        .defaultValue(new SettingColor(255, 80, 180, 35))
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgRender.add(new BoolSetting.Builder()
        .name("see-through-blocks")
        .description("Show chunk markers through normal blocks when Meteor rendering allows it.")
        .defaultValue(true)
        .build()
    );

    // The cache only contains chunks the current client has loaded and scanned.
    private final Long2IntOpenHashMap amethystCounts = new Long2IntOpenHashMap();
    private final LongOpenHashSet markedChunks = new LongOpenHashSet();
    private final LongOpenHashSet queuedChunks = new LongOpenHashSet();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();

    private ClientLevel lastLevel;
    private int ticksUntilRescan;

    public AmethystChunkESP() {
        super(TotemAutoInvAddon.CHALK_ESP,
            "amethyst-chunk-esp",
            "Highlights loaded chunks that contain many fully-grown amethyst clusters."
        );
    }

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

    /** Queue a newly received chunk without doing an expensive scan in the network event. */
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.level == null) return;

        int chunkX = event.chunk().getPos().x;
        int chunkZ = event.chunk().getPos().z;
        queueIfInRange(chunkX, chunkZ);
    }

    /**
     * A finished cluster can only appear or disappear through a block update.
     * Updates unrelated to AMETHYST_CLUSTER need no rescan at all.
     */
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!event.oldState.is(Blocks.AMETHYST_CLUSTER) && !event.newState.is(Blocks.AMETHYST_CLUSTER)) return;

        queueIfInRange(event.pos.getX() >> 4, event.pos.getZ() >> 4);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            clear();
            lastLevel = mc.level;
            return;
        }

        // Includes respawns and dimension/server changes, which replace the client level.
        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
            ticksUntilRescan = 0;
        }

        if (ticksUntilRescan-- <= 0) {
            queueLoadedChunksInRange();
            ticksUntilRescan = rescanInterval.get();
        }

        processQueuedScans();
        removeUnavailableOrOutOfRangeChunks();
        rebuildMarkedChunks();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || markedChunks.isEmpty() || (!outline.get() && !fill.get())) return;

        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        ShapeMode shapeMode = outline.get() && fill.get() ? ShapeMode.Both : outline.get() ? ShapeMode.Lines : ShapeMode.Sides;
        for (LongIterator iterator = markedChunks.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;

            // A thin horizontal 16x16 slab keeps the marker a proper square
            // on Y=63 instead of a tall world-height box in perspective.
            renderer.box(minX, MARKER_Y, minZ, minX + 16, MARKER_Y + MARKER_THICKNESS, minZ + 16,
                fillColor.get(), outlineColor.get(), shapeMode, 0);
        }
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

        long key = pack(chunkX, chunkZ);
        if (queuedChunks.add(key)) scanQueue.addLast(key);
    }

    private void processQueuedScans() {
        int scansRemaining = chunksPerTick.get();

        while (scansRemaining-- > 0 && !scanQueue.isEmpty()) {
            long key = scanQueue.removeFirst();
            queuedChunks.remove(key);

            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) {
                amethystCounts.remove(key);
                continue;
            }

            amethystCounts.put(key, countFullyGrownClusters(chunkX, chunkZ));
        }
    }

    private int countFullyGrownClusters(int chunkX, int chunkZ) {
        // hasChunk was checked immediately before this call, so getChunk does not
        // request new data from the server or cause the client to load a chunk.
        LevelChunk chunk = mc.level.getChunk(chunkX, chunkZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;

        for (int y = mc.level.getMinY(); y < mc.level.getMaxY(); y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    pos.set(minX + localX, y, minZ + localZ);
                    if (chunk.getBlockState(pos).is(Blocks.AMETHYST_CLUSTER)) count++;
                }
            }
        }

        return count;
    }

    /** Remove data as soon as the client unloads a chunk or it leaves the configured range. */
    private void removeUnavailableOrOutOfRangeChunks() {
        for (LongIterator iterator = amethystCounts.keySet().iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);

            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) iterator.remove();
        }

        // Queued chunks may unload or be left behind when the player moves.
        // Remove them from both queue structures immediately, so old work cannot
        // delay a newly relevant scan by many ticks.
        for (Iterator<Long> iterator = scanQueue.iterator(); iterator.hasNext();) {
            long key = iterator.next();
            if (!mc.level.hasChunk(unpackX(key), unpackZ(key)) || !isInRange(unpackX(key), unpackZ(key))) {
                iterator.remove();
                queuedChunks.remove(key);
            }
        }
    }

    private void rebuildMarkedChunks() {
        markedChunks.clear();
        int minimum = minimumAmethystCount.get();

        for (LongIterator iterator = amethystCounts.keySet().iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            if (amethystCounts.get(key) >= minimum) markedChunks.add(key);
        }
    }

    private boolean isInRange(int chunkX, int chunkZ) {
        if (mc.player == null) return false;

        int deltaX = chunkX - blockToChunk(mc.player.getX());
        int deltaZ = chunkZ - blockToChunk(mc.player.getZ());
        int maximumDistance = scanDistance.get();
        return deltaX * deltaX + deltaZ * deltaZ <= maximumDistance * maximumDistance;
    }

    private void clear() {
        amethystCounts.clear();
        markedChunks.clear();
        queuedChunks.clear();
        scanQueue.clear();
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
