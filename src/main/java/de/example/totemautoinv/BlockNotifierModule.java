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
import meteordevelopment.meteorclient.settings.BlockListSetting;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

/**
 * Alerts for selected block types in chunks that are already present in the
 * local client chunk cache. Scans are queued and bounded per tick to avoid
 * long frame times when many chunks arrive at once.
 */
public final class BlockNotifierModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Block types to detect in chunks already loaded by your client.")
        .defaultValue(Blocks.SPAWNER, Blocks.BEACON, Blocks.ENDER_CHEST)
        .onChanged(value -> requestFullRescan())
        .build()
    );

    private final Setting<Integer> scanDistance = sgGeneral.add(new IntSetting.Builder()
        .name("scan-distance")
        .description("Maximum distance in chunks from you to inspect.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .onChanged(value -> requestFullRescan())
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Loaded chunks scanned each tick. Higher values use more CPU.")
        .defaultValue(1)
        .min(1)
        .max(4)
        .sliderRange(1, 3)
        .build()
    );

    private final Setting<Boolean> chatAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send a client chat message when new matching blocks are found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showEsp = sgRender.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Draw a marker around detected blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyNew = sgRender.add(new BoolSetting.Builder()
        .name("only-new")
        .description("Render only blocks discovered since the module was enabled.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgRender.add(new BoolSetting.Builder()
        .name("see-through-blocks")
        .description("Render block markers through normal blocks when Meteor rendering allows it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maximumMarkers = sgRender.add(new IntSetting.Builder()
        .name("maximum-markers")
        .description("Maximum number of block markers rendered at once.")
        .defaultValue(256)
        .min(16)
        .max(2048)
        .sliderRange(32, 512)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of detected blocks.")
        .defaultValue(new SettingColor(255, 205, 70, 255))
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Transparent fill color of detected blocks.")
        .defaultValue(new SettingColor(255, 205, 70, 40))
        .build()
    );

    private final LongOpenHashSet foundBlocks = new LongOpenHashSet();
    private final LongOpenHashSet newlyFoundBlocks = new LongOpenHashSet();
    private final LongOpenHashSet queuedChunks = new LongOpenHashSet();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();

    private ClientLevel lastLevel;
    private boolean fullRescanRequested;
    private int renderedMarkers;

    public BlockNotifierModule() {
        super(TotemAutoInvAddon.CHALK_ESP,
            "block-notifier",
            "Alerts and marks selected blocks in chunks already loaded by your client."
        );
    }

    @Override
    public void onActivate() {
        clear();
        lastLevel = mc.level;
        fullRescanRequested = true;
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
        fullRescanRequested = true;
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
        if (!isInRange(event.pos.getX() >> 4, event.pos.getZ() >> 4)) return;

        long key = event.pos.asLong();
        if (matches(event.newState)) addFoundBlock(key, true);
        else {
            foundBlocks.remove(key);
            newlyFoundBlocks.remove(key);
        }
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
            fullRescanRequested = true;
        }

        if (fullRescanRequested) {
            fullRescanRequested = false;
            queueLoadedChunksInRange();
        }

        processScanQueue();
        removeUnavailableBlocks();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!showEsp.get() || mc.level == null) return;

        LongOpenHashSet renderSet = onlyNew.get() ? newlyFoundBlocks : foundBlocks;
        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        int count = 0;

        for (LongIterator iterator = renderSet.iterator(); iterator.hasNext() && count < maximumMarkers.get();) {
            renderer.box(BlockPos.of(iterator.nextLong()), sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            count++;
        }

        renderedMarkers = count;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(renderedMarkers);
    }

    private void requestFullRescan() {
        fullRescanRequested = true;
    }

    private void queueLoadedChunksInRange() {
        int chunkX = blockToChunk(mc.player.getX());
        int chunkZ = blockToChunk(mc.player.getZ());
        int distance = scanDistance.get();

        for (int x = chunkX - distance; x <= chunkX + distance; x++) {
            for (int z = chunkZ - distance; z <= chunkZ + distance; z++) {
                if (mc.level.hasChunk(x, z)) queueIfInRange(x, z);
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

            scanChunk(mc.level.getChunk(chunkX, chunkZ), chunkX, chunkZ);
        }
    }

    private void scanChunk(LevelChunk chunk, int chunkX, int chunkZ) {
        LongOpenHashSet currentMatches = new LongOpenHashSet();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;

        for (int y = mc.level.getMinY(); y < mc.level.getMaxY(); y++) {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    pos.set(minX + localX, y, minZ + localZ);
                    if (matches(chunk.getBlockState(pos))) currentMatches.add(pos.asLong());
                }
            }
        }

        for (LongIterator iterator = foundBlocks.iterator(); iterator.hasNext();) {
            long existing = iterator.nextLong();
            BlockPos existingPos = BlockPos.of(existing);
            if ((existingPos.getX() >> 4) == chunkX && (existingPos.getZ() >> 4) == chunkZ && !currentMatches.contains(existing)) {
                iterator.remove();
                newlyFoundBlocks.remove(existing);
            }
        }

        int additions = 0;
        BlockPos first = null;
        for (LongIterator iterator = currentMatches.iterator(); iterator.hasNext();) {
            long found = iterator.nextLong();
            if (foundBlocks.add(found)) {
                newlyFoundBlocks.add(found);
                additions++;
                if (first == null) first = BlockPos.of(found);
            }
        }

        if (additions > 0 && chatAlerts.get() && first != null) {
            info("Found %d configured block%s near (%d, %d, %d).", additions, additions == 1 ? "" : "s", first.getX(), first.getY(), first.getZ());
        }
    }

    private void addFoundBlock(long key, boolean alert) {
        if (!foundBlocks.add(key)) return;
        newlyFoundBlocks.add(key);
        if (alert && chatAlerts.get()) {
            BlockPos pos = BlockPos.of(key);
            info("Configured block found at (%d, %d, %d).", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private boolean matches(BlockState state) {
        return blocks.get().contains(state.getBlock());
    }

    private void removeUnavailableBlocks() {
        for (LongIterator iterator = foundBlocks.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            BlockPos pos = BlockPos.of(key);
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !isInRange(pos.getX() >> 4, pos.getZ() >> 4)) {
                iterator.remove();
                newlyFoundBlocks.remove(key);
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
        int ownX = blockToChunk(mc.player.getX());
        int ownZ = blockToChunk(mc.player.getZ());
        int deltaX = chunkX - ownX;
        int deltaZ = chunkZ - ownZ;
        int distance = scanDistance.get();
        return deltaX * deltaX + deltaZ * deltaZ <= distance * distance;
    }

    private void clear() {
        foundBlocks.clear();
        newlyFoundBlocks.clear();
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
