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
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Client-side Amethyst chunk heuristic. It intentionally uses only block
 * states in chunks already loaded by the client and never sends any packets.
 */
public final class SusChunkFinderModule extends Module {
    private static final double MARKER_Y = 63.0;
    private static final double MARKER_THICKNESS = 0.02;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStrippedGeodes = settings.createGroup("Stripped Geodes");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> detectBuddingAmethyst = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-budding-amethyst")
        .description("Use Budding Amethyst blocks as geode evidence, even when bud models are not visually noticeable.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minimumBuddingAmethyst = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-budding-amethyst")
        .description("Budding Amethyst blocks required to mark a loaded chunk.")
        .defaultValue(1)
        .min(1)
        .max(64)
        .sliderRange(1, 16)
        .visible(detectBuddingAmethyst::get)
        .build()
    );

    private final Setting<Boolean> detectFullyGrownClusters = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-fully-grown-clusters")
        .description("Use fully-grown Amethyst Clusters as additional evidence.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minimumFullyGrownClusters = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-fully-grown-clusters")
        .description("Fully-grown Amethyst Clusters required to mark a loaded chunk.")
        .defaultValue(1)
        .min(1)
        .max(64)
        .sliderRange(1, 16)
        .visible(detectFullyGrownClusters::get)
        .build()
    );

    private final Setting<Boolean> detectStrippedGeodes = sgStrippedGeodes.add(new BoolSetting.Builder()
        .name("detect-stripped-geodes")
        .description("Detect geode structures using normal Amethyst Blocks when buds or clusters are absent.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smartStructureCheck = sgStrippedGeodes.add(new BoolSetting.Builder()
        .name("smart-structure-check")
        .description("Require nearby Calcite and Smooth Basalt in the same chunk to reduce false positives from player builds.")
        .defaultValue(true)
        .visible(detectStrippedGeodes::get)
        .build()
    );

    private final Setting<Integer> minimumAmethystBlocks = sgStrippedGeodes.add(new IntSetting.Builder()
        .name("minimum-amethyst-blocks")
        .description("Normal Amethyst Blocks required before a chunk can qualify as a stripped geode.")
        .defaultValue(12)
        .min(1)
        .max(512)
        .sliderRange(1, 64)
        .visible(detectStrippedGeodes::get)
        .build()
    );

    private final Setting<Integer> minimumCalciteBlocks = sgStrippedGeodes.add(new IntSetting.Builder()
        .name("minimum-calcite-blocks")
        .description("Calcite blocks required by the smart structure check.")
        .defaultValue(4)
        .min(1)
        .max(128)
        .sliderRange(1, 32)
        .visible(() -> detectStrippedGeodes.get() && smartStructureCheck.get())
        .build()
    );

    private final Setting<Integer> minimumSmoothBasaltBlocks = sgStrippedGeodes.add(new IntSetting.Builder()
        .name("minimum-smooth-basalt-blocks")
        .description("Smooth Basalt blocks required by the smart structure check.")
        .defaultValue(4)
        .min(1)
        .max(128)
        .sliderRange(1, 32)
        .visible(() -> detectStrippedGeodes.get() && smartStructureCheck.get())
        .build()
    );

    private final Setting<Integer> scanDistance = sgGeneral.add(new IntSetting.Builder()
        .name("scan-distance")
        .description("Maximum distance in chunks from you to scan and highlight.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Boolean> deepslateRescan = sgGeneral.add(new BoolSetting.Builder()
        .name("deepslate-rescan")
        .description("Prioritize a fresh local scan of your current loaded chunk whenever you enter it. Does not request server data.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rescanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("Ticks between queueing a refresh of all loaded chunks in range.")
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

    private final Setting<MarkerHeight> markerHeight = sgRender.add(new EnumSetting.Builder<MarkerHeight>()
        .name("marker-height")
        .description("Vertical position of the flat chunk marker.")
        .defaultValue(MarkerHeight.DetectedAmethyst)
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgRender.add(new BoolSetting.Builder()
        .name("see-through-blocks")
        .description("Render chunk markers through normal blocks when Meteor rendering allows it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of matching chunks.")
        .defaultValue(new SettingColor(255, 70, 195, 255))
        .build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Transparent fill color of matching chunks.")
        .defaultValue(new SettingColor(255, 70, 195, 34))
        .build()
    );

    private final Long2IntOpenHashMap buddingCounts = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap fullyGrownCounts = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap amethystBlockCounts = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap calciteCounts = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap smoothBasaltCounts = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap detectedHeights = new Long2IntOpenHashMap();
    private final LongOpenHashSet markedChunks = new LongOpenHashSet();
    private final LongOpenHashSet queuedChunks = new LongOpenHashSet();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();

    private ClientLevel lastLevel;
    private int ticksUntilRescan;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    public SusChunkFinderModule() {
        super(TotemAutoInvAddon.CHALK_ESP,
            "sus-chunkfinder",
            "Marks client-loaded chunks containing Budding Amethyst or fully-grown Amethyst Clusters."
        );
    }

    @Override
    public void onActivate() {
        clear();
        lastLevel = mc.level;
        ticksUntilRescan = 0;
        resetPlayerChunk();
    }

    @Override
    public void onDeactivate() {
        clear();
        lastLevel = null;
        resetPlayerChunk();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        clear();
        lastLevel = mc.level;
        ticksUntilRescan = 0;
        resetPlayerChunk();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clear();
        lastLevel = null;
        resetPlayerChunk();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.level == null) return;
        queueIfInRange(event.chunk().getPos().x, event.chunk().getPos().z);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!isAmethystSignal(event.oldState) && !isAmethystSignal(event.newState)) return;
        queueIfInRange(event.pos.getX() >> 4, event.pos.getZ() >> 4);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            clear();
            lastLevel = mc.level;
            resetPlayerChunk();
            return;
        }

        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
            ticksUntilRescan = 0;
            resetPlayerChunk();
        }

        queueCurrentChunkOnEnter();

        if (ticksUntilRescan-- <= 0) {
            queueLoadedChunksInRange();
            ticksUntilRescan = rescanInterval.get();
        }

        processScanQueue();
        removeUnavailableChunks();
        rebuildMarkedChunks();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || markedChunks.isEmpty()) return;

        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        for (LongIterator iterator = markedChunks.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            int minX = unpackX(key) << 4;
            int minZ = unpackZ(key) << 4;
            double y = switch (markerHeight.get()) {
                case DetectedAmethyst -> detectedHeights.getOrDefault(key, (int) MARKER_Y);
                case Player -> Math.floor(mc.player.getY()) - 1.0;
                case FixedY63 -> MARKER_Y;
            };

            renderer.box(minX, y, minZ, minX + 16, y + MARKER_THICKNESS, minZ + 16,
                fillColor.get(), lineColor.get(), ShapeMode.Both, 0);
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

    /**
     * Makes the chunk the player has just entered the next local scan. This
     * only reuses the client chunk cache and never sends a chunk request.
     */
    private void queueCurrentChunkOnEnter() {
        int currentChunkX = blockToChunk(mc.player.getX());
        int currentChunkZ = blockToChunk(mc.player.getZ());
        boolean enteredNewChunk = currentChunkX != lastPlayerChunkX || currentChunkZ != lastPlayerChunkZ;

        lastPlayerChunkX = currentChunkX;
        lastPlayerChunkZ = currentChunkZ;
        if (!deepslateRescan.get() || !enteredNewChunk || !mc.level.hasChunk(currentChunkX, currentChunkZ)) return;

        long key = pack(currentChunkX, currentChunkZ);
        if (!queuedChunks.add(key)) scanQueue.removeFirstOccurrence(key);
        scanQueue.addFirst(key);
    }

    private void processScanQueue() {
        int remaining = chunksPerTick.get();
        while (remaining-- > 0 && !scanQueue.isEmpty()) {
            long key = scanQueue.removeFirst();
            queuedChunks.remove(key);

            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) {
                buddingCounts.remove(key);
                fullyGrownCounts.remove(key);
                amethystBlockCounts.remove(key);
                calciteCounts.remove(key);
                smoothBasaltCounts.remove(key);
                detectedHeights.remove(key);
                continue;
            }

            scanChunk(key, chunkX, chunkZ);
        }
    }

    private void scanChunk(long key, int chunkX, int chunkZ) {
        LevelChunk chunk = mc.level.getChunk(chunkX, chunkZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int budding = 0;
        int fullyGrown = 0;
        int amethystBlocks = 0;
        int calcite = 0;
        int smoothBasalt = 0;
        int signalYTotal = 0;
        int signals = 0;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;

        for (int y = mc.level.getMinY(); y < mc.level.getMaxY(); y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    pos.set(minX + localX, y, minZ + localZ);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.is(Blocks.BUDDING_AMETHYST)) {
                        budding++;
                        signalYTotal += y;
                        signals++;
                    } else if (state.is(Blocks.AMETHYST_CLUSTER)) {
                        fullyGrown++;
                        signalYTotal += y;
                        signals++;
                    } else if (state.is(Blocks.AMETHYST_BLOCK)) {
                        amethystBlocks++;
                        signalYTotal += y;
                        signals++;
                    } else if (state.is(Blocks.CALCITE)) {
                        calcite++;
                    } else if (state.is(Blocks.SMOOTH_BASALT)) {
                        smoothBasalt++;
                    }
                }
            }
        }

        buddingCounts.put(key, budding);
        fullyGrownCounts.put(key, fullyGrown);
        amethystBlockCounts.put(key, amethystBlocks);
        calciteCounts.put(key, calcite);
        smoothBasaltCounts.put(key, smoothBasalt);
        if (signals > 0) detectedHeights.put(key, Math.round((float) signalYTotal / signals));
        else detectedHeights.remove(key);
    }

    private void rebuildMarkedChunks() {
        markedChunks.clear();
        for (LongIterator iterator = buddingCounts.keySet().iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            boolean hasBuddingEvidence = detectBuddingAmethyst.get() && buddingCounts.get(key) >= minimumBuddingAmethyst.get();
            boolean hasFullyGrownEvidence = detectFullyGrownClusters.get() && fullyGrownCounts.get(key) >= minimumFullyGrownClusters.get();
            boolean hasStrippedGeodeEvidence = detectStrippedGeodes.get()
                && amethystBlockCounts.get(key) >= minimumAmethystBlocks.get()
                && (!smartStructureCheck.get()
                    || calciteCounts.get(key) >= minimumCalciteBlocks.get()
                    && smoothBasaltCounts.get(key) >= minimumSmoothBasaltBlocks.get());

            if (hasBuddingEvidence || hasFullyGrownEvidence || hasStrippedGeodeEvidence) markedChunks.add(key);
        }
    }

    private void removeUnavailableChunks() {
        for (LongIterator iterator = buddingCounts.keySet().iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) {
                iterator.remove();
                fullyGrownCounts.remove(key);
                amethystBlockCounts.remove(key);
                calciteCounts.remove(key);
                smoothBasaltCounts.remove(key);
                detectedHeights.remove(key);
            }
        }

        for (Iterator<Long> iterator = scanQueue.iterator(); iterator.hasNext();) {
            long key = iterator.next();
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
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
        buddingCounts.clear();
        fullyGrownCounts.clear();
        amethystBlockCounts.clear();
        calciteCounts.clear();
        smoothBasaltCounts.clear();
        detectedHeights.clear();
        markedChunks.clear();
        queuedChunks.clear();
        scanQueue.clear();
    }

    private void resetPlayerChunk() {
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
    }

    private static boolean isAmethystSignal(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.BUDDING_AMETHYST)
            || state.is(Blocks.AMETHYST_CLUSTER)
            || state.is(Blocks.AMETHYST_BLOCK)
            || state.is(Blocks.CALCITE)
            || state.is(Blocks.SMOOTH_BASALT);
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

    public enum MarkerHeight {
        DetectedAmethyst,
        Player,
        FixedY63;

        @Override
        public String toString() {
            return switch (this) {
                case DetectedAmethyst -> "Detected Amethyst";
                case Player -> "Below Player";
                case FixedY63 -> "Fixed Y 63";
            };
        }
    }
}
