package de.example.totemautoinv;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marks client-observed block activity in nearby loaded chunks. This is an
 * activity clue only: Minecraft does not identify which actor caused a block
 * update, and no unseen players, chunks, or server data are requested.
 */
public final class PlayerActivityModule extends Module {
    private static final double MARKER_THICKNESS = 0.02;
    private static final long OWN_ACTION_GRACE_NANOS = 2_500_000_000L;
    private static final Long2LongOpenHashMap OWN_ACTION_DEADLINES = new Long2LongOpenHashMap();
    private static boolean trackOwnActions;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Maximum chunk distance from you for observed block activity.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> holdTicks = sgGeneral.add(new IntSetting.Builder()
        .name("hold-ticks")
        .description("How long a chunk stays marked after its last observed block update.")
        .defaultValue(600)
        .min(20)
        .max(7200)
        .sliderRange(100, 1200)
        .build()
    );

    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send a chat message when a nearby chunk first becomes active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderHeight = sgRender.add(new IntSetting.Builder()
        .name("render-height")
        .description("Y level used for the square chunk marker.")
        .defaultValue(63)
        .min(-64)
        .max(320)
        .sliderRange(-64, 128)
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgRender.add(new BoolSetting.Builder()
        .name("see-through-blocks")
        .description("Draw markers through normal blocks when Meteor rendering allows it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of active chunk markers.")
        .defaultValue(new SettingColor(255, 158, 43, 255))
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Transparent fill color of active chunk markers.")
        .defaultValue(new SettingColor(255, 158, 43, 35))
        .build()
    );

    private final LongOpenHashSet activeChunks = new LongOpenHashSet();
    private final Long2IntOpenHashMap lastActivityTick = new Long2IntOpenHashMap();

    private ClientLevel lastLevel;
    private int tick;

    public PlayerActivityModule() {
        super(TotemAutoInvAddon.CHALK_ESP,
            "player-bypass",
            "Marks nearby observed block placements or breaks, excluding your own direct actions and natural growth."
        );
        lastActivityTick.defaultReturnValue(Integer.MIN_VALUE);
        OWN_ACTION_DEADLINES.defaultReturnValue(0L);
    }

    @Override
    public void onActivate() {
        clear();
        trackOwnActions = true;
        lastLevel = mc.level;
    }

    @Override
    public void onDeactivate() {
        trackOwnActions = false;
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
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.player == null || mc.level == null || event.oldState.equals(event.newState)) return;
        if (!isPlacementOrBreak(event.oldState, event.newState)) return;
        if (hasRecentOwnAction(event.pos)) return;

        int chunkX = event.pos.getX() >> 4;
        int chunkZ = event.pos.getZ() >> 4;
        if (!isInRange(chunkX, chunkZ)) return;

        long chunk = packChunk(chunkX, chunkZ);
        boolean newlyActive = activeChunks.add(chunk);
        lastActivityTick.put(chunk, tick);

        if (newlyActive && chatAlerts.get()) {
            info("Observed block activity in chunk (%d, %d) at (%d, %d, %d).", chunkX, chunkZ, event.pos.getX(), event.pos.getY(), event.pos.getZ());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tick++;
        if (mc.player == null || mc.level == null) {
            clear();
            lastLevel = mc.level;
            return;
        }

        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
        }

        if (tick % 20 == 0) pruneExpiredOwnActions();

        for (LongIterator iterator = activeChunks.iterator(); iterator.hasNext();) {
            long chunk = iterator.nextLong();
            int chunkX = unpackChunkX(chunk);
            int chunkZ = unpackChunkZ(chunk);
            if (!isInRange(chunkX, chunkZ) || tick - lastActivityTick.get(chunk) > holdTicks.get()) {
                iterator.remove();
                lastActivityTick.remove(chunk);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || activeChunks.isEmpty()) return;

        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        double y = renderHeight.get();
        for (LongIterator iterator = activeChunks.iterator(); iterator.hasNext();) {
            long chunk = iterator.nextLong();
            int chunkX = unpackChunkX(chunk);
            int chunkZ = unpackChunkZ(chunk);
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            renderer.box(minX, y, minZ, minX + 16, y + MARKER_THICKNESS, minZ + 16,
                sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() {
        return Integer.toString(activeChunks.size());
    }

    private boolean isInRange(int chunkX, int chunkZ) {
        int ownX = blockToChunk(mc.player.getX());
        int ownZ = blockToChunk(mc.player.getZ());
        int deltaX = chunkX - ownX;
        int deltaZ = chunkZ - ownZ;
        int radius = scanRadius.get();
        return deltaX * deltaX + deltaZ * deltaZ <= radius * radius;
    }

    private void clear() {
        activeChunks.clear();
        lastActivityTick.clear();
        OWN_ACTION_DEADLINES.clear();
        tick = 0;
    }

    private static boolean isTransient(BlockState state) {
        return !state.getFluidState().isEmpty()
            || state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.NETHER_PORTAL)
            || state.is(Blocks.END_PORTAL)
            || state.is(Blocks.END_GATEWAY);
    }

    /** Called by the client input mixin immediately before the local player breaks or places a block. */
    public static void recordOwnAction(BlockPos pos) {
        if (!trackOwnActions || pos == null) return;
        OWN_ACTION_DEADLINES.put(pos.asLong(), System.nanoTime() + OWN_ACTION_GRACE_NANOS);
    }

    private static boolean hasRecentOwnAction(BlockPos pos) {
        long key = pos.asLong();
        long deadline = OWN_ACTION_DEADLINES.get(key);
        if (deadline == 0L) return false;

        if (System.nanoTime() <= deadline) return true;

        OWN_ACTION_DEADLINES.remove(key);
        return false;
    }

    private static boolean isPlacementOrBreak(BlockState oldState, BlockState newState) {
        // Keep the signal deliberately strict: only an air-to-solid placement
        // or solid-to-air break is relevant. State changes such as doors,
        // redstone, fluids and crop ages are not player build activity.
        if (oldState.isAir() == newState.isAir()) return false;
        if (isTransient(oldState) || isTransient(newState)) return false;
        return !isNaturalGrowthBlock(oldState) && !isNaturalGrowthBlock(newState);
    }

    private static boolean isNaturalGrowthBlock(BlockState state) {
        return state.is(Blocks.BUDDING_AMETHYST)
            || state.is(Blocks.SMALL_AMETHYST_BUD)
            || state.is(Blocks.MEDIUM_AMETHYST_BUD)
            || state.is(Blocks.LARGE_AMETHYST_BUD)
            || state.is(Blocks.AMETHYST_CLUSTER)
            || state.is(Blocks.POINTED_DRIPSTONE)
            || state.is(Blocks.KELP)
            || state.is(Blocks.KELP_PLANT)
            || state.is(Blocks.SEAGRASS)
            || state.is(Blocks.TALL_SEAGRASS)
            || state.is(Blocks.SUGAR_CANE)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.BAMBOO)
            || state.is(Blocks.BAMBOO_SAPLING)
            || state.is(Blocks.VINE)
            || state.is(Blocks.CAVE_VINES)
            || state.is(Blocks.CAVE_VINES_PLANT)
            || state.is(Blocks.TWISTING_VINES)
            || state.is(Blocks.TWISTING_VINES_PLANT)
            || state.is(Blocks.WEEPING_VINES)
            || state.is(Blocks.WEEPING_VINES_PLANT)
            || state.is(Blocks.CHORUS_FLOWER)
            || state.is(Blocks.CHORUS_PLANT)
            || state.is(Blocks.COCOA)
            || state.is(Blocks.NETHER_WART)
            || state.is(Blocks.WHEAT)
            || state.is(Blocks.CARROTS)
            || state.is(Blocks.POTATOES)
            || state.is(Blocks.BEETROOTS)
            || state.is(Blocks.TORCHFLOWER_CROP)
            || state.is(Blocks.PITCHER_CROP)
            || state.is(Blocks.MELON_STEM)
            || state.is(Blocks.PUMPKIN_STEM)
            || state.is(Blocks.ATTACHED_MELON_STEM)
            || state.is(Blocks.ATTACHED_PUMPKIN_STEM)
            || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static void pruneExpiredOwnActions() {
        long now = System.nanoTime();
        for (LongIterator iterator = OWN_ACTION_DEADLINES.keySet().iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            if (OWN_ACTION_DEADLINES.get(key) < now) iterator.remove();
        }
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
