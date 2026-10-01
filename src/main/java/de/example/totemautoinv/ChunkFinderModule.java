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
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Marks loaded chunks that contain an unusual concentration of visible storage
 * block entities. This is an on-client heuristic, not a claim that a chunk is
 * a base, owned, or active on the server.
 */
public final class ChunkFinderModule extends Module {
    private static final double MARKER_Y = 63.0;
    private static final double MARKER_THICKNESS = 0.02;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minimumStorageBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-storage-blocks")
        .description("Storage block entities required before a loaded chunk is highlighted.")
        .defaultValue(4)
        .min(1)
        .max(64)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> scanDistance = sgGeneral.add(new IntSetting.Builder()
        .name("scan-distance")
        .description("Maximum distance in chunks from you to inspect.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> rescanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("Ticks between queueing a complete refresh of loaded chunks.")
        .defaultValue(200)
        .min(20)
        .max(1200)
        .sliderRange(20, 400)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Queued chunks checked per tick. Higher values update faster but use more CPU.")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderRange(1, 4)
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
        .description("Outline color of qualifying chunks.")
        .defaultValue(new SettingColor(70, 220, 255, 255))
        .build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Transparent fill color of qualifying chunks.")
        .defaultValue(new SettingColor(70, 220, 255, 32))
        .build()
    );

    private final Long2IntOpenHashMap storageCounts = new Long2IntOpenHashMap();
    private final LongOpenHashSet markedChunks = new LongOpenHashSet();
    private final LongOpenHashSet queuedChunks = new LongOpenHashSet();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();

    private ClientLevel lastLevel;
    private int ticksUntilRescan;

    public ChunkFinderModule() {
        super(TotemAutoInvAddon.CHALK_ESP,
            "chunk-finder",
            "Highlights client-loaded chunks with a configurable concentration of storage blocks."
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

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.level == null) return;
        queueIfInRange(event.chunk().getPos().x, event.chunk().getPos().z);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        queueIfInRange(event.pos.getX() >> 4, event.pos.getZ() >> 4);
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
        removeUnavailableChunks();
        rebuildMarkers();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || markedChunks.isEmpty()) return;

        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        for (LongIterator iterator = markedChunks.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            int minX = unpackX(key) << 4;
            int minZ = unpackZ(key) << 4;
            renderer.box(minX, MARKER_Y, minZ, minX + 16, MARKER_Y + MARKER_THICKNESS, minZ + 16,
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

    private void processScanQueue() {
        int remaining = chunksPerTick.get();
        while (remaining-- > 0 && !scanQueue.isEmpty()) {
            long key = scanQueue.removeFirst();
            queuedChunks.remove(key);

            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) {
                storageCounts.remove(key);
                continue;
            }

            storageCounts.put(key, countStorageBlocks(mc.level.getChunk(chunkX, chunkZ)));
        }
    }

    private int countStorageBlocks(LevelChunk chunk) {
        int count = 0;
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            if (isStorageBlock(chunk.getBlockState(pos))) count++;
        }
        return count;
    }

    private static boolean isStorageBlock(BlockState state) {
        return state.is(Blocks.CHEST)
            || state.is(Blocks.TRAPPED_CHEST)
            || state.is(Blocks.BARREL)
            || state.is(Blocks.HOPPER)
            || state.is(Blocks.DISPENSER)
            || state.is(Blocks.DROPPER)
            || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private void rebuildMarkers() {
        markedChunks.clear();
        int minimum = minimumStorageBlocks.get();
        for (LongIterator iterator = storageCounts.keySet().iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            if (storageCounts.get(key) >= minimum) markedChunks.add(key);
        }
    }

    private void removeUnavailableChunks() {
        for (LongIterator iterator = storageCounts.keySet().iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            if (!mc.level.hasChunk(unpackX(key), unpackZ(key)) || !isInRange(unpackX(key), unpackZ(key))) iterator.remove();
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
        storageCounts.clear();
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
