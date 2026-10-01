package de.example.totemautoinv;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Sends a notification when a normal mob spawner is present in a chunk
 * that is already loaded on this client. It never requests chunks or guesses
 * blocks that the server has not sent.
 */
public final class SpawnerAlertModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> scanDistance = sgGeneral.add(new IntSetting.Builder()
        .name("scan-distance")
        .description("Maximum chunk distance from you to inspect.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> rescanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-interval")
        .description("Ticks between checks of already loaded chunks.")
        .defaultValue(200)
        .min(20)
        .max(1200)
        .sliderRange(20, 400)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Loaded chunks processed each tick. Higher values find spawners sooner but use more CPU.")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send a chat message when a newly loaded spawner is detected.")
        .defaultValue(true)
        .build()
    );

    private final LongOpenHashSet detectedSpawners = new LongOpenHashSet();
    private final LongOpenHashSet queuedChunks = new LongOpenHashSet();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();

    private ClientLevel lastLevel;
    private int ticksUntilRescan;

    public SpawnerAlertModule() {
        super(TotemAutoInvAddon.CHALK_ESP,
            "spawner-alert",
            "Alerts you when a normal mob spawner is present in a chunk already loaded by your client."
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
        if (!isInRange(event.pos.getX() >> 4, event.pos.getZ() >> 4)) return;

        long key = event.pos.asLong();
        if (isSpawner(event.newState)) addSpawner(key);
        else detectedSpawners.remove(key);
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
        removeUnavailableSpawners();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(detectedSpawners.size());
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

            scanChunk(mc.level.getChunk(chunkX, chunkZ), chunkX, chunkZ);
        }
    }

    private void scanChunk(LevelChunk chunk, int chunkX, int chunkZ) {
        LongOpenHashSet found = new LongOpenHashSet();

        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            if (isSpawner(chunk.getBlockState(pos))) found.add(pos.asLong());
        }

        for (LongIterator iterator = detectedSpawners.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            BlockPos pos = BlockPos.of(key);
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ && !found.contains(key)) iterator.remove();
        }

        for (LongIterator iterator = found.iterator(); iterator.hasNext();) {
            addSpawner(iterator.nextLong());
        }
    }

    private void addSpawner(long key) {
        if (!detectedSpawners.add(key) || !chatAlerts.get()) return;

        BlockPos pos = BlockPos.of(key);
        info("Spawner loaded at (%d, %d, %d).", pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean isSpawner(BlockState state) {
        return state.is(Blocks.SPAWNER);
    }

    private void removeUnavailableSpawners() {
        for (LongIterator iterator = detectedSpawners.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            BlockPos pos = BlockPos.of(key);
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isInRange(chunkX, chunkZ)) iterator.remove();
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
        int deltaX = chunkX - blockToChunk(mc.player.getX());
        int deltaZ = chunkZ - blockToChunk(mc.player.getZ());
        int maximum = scanDistance.get();
        return deltaX * deltaX + deltaZ * deltaZ <= maximum * maximum;
    }

    private void clear() {
        detectedSpawners.clear();
        queuedChunks.clear();
        scanQueue.clear();
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
