package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Adds a normal-client-interaction shortcut for placing an obsidian base and
 * then an End Crystal at one player-selected location.
 */
public final class KreysCrystalsModule extends Module {
    private static final long OBSIDIAN_CONFIRMATION_TIMEOUT_MS = 1_500L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> placementDelay = sgGeneral.add(new IntSetting.Builder()
        .name("placement-delay")
        .description("Milliseconds to wait after the client receives the Obsidian before placing the End Crystal.")
        .defaultValue(50)
        .min(0)
        .max(500)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Integer> speed = sgGeneral.add(new IntSetting.Builder()
        .name("speed")
        .description("Maximum automatic End Crystal placements per second. Minecraft ticks limit the effective maximum to 20.")
        .defaultValue(10)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Reserved for a future manual Crystal-break feature. This module currently does not attack or break crystals.")
        .defaultValue(100)
        .min(0)
        .max(500)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Boolean> autoObsidian = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-obsidian")
        .description("Automatically place Obsidian at the clicked vanilla placement position when needed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-crystal")
        .description("After confirmed Obsidian placement, automatically place an End Crystal on it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWhileHoldingCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-holding-crystal")
        .description("Only activate when the selected main-hand item is an End Crystal.")
        .defaultValue(true)
        .build()
    );

    private enum Phase {
        IDLE,
        WAIT_TO_PLACE_OBSIDIAN,
        WAIT_FOR_OBSIDIAN,
        WAIT_TO_PLACE_CRYSTAL
    }

    private Phase phase = Phase.IDLE;
    private ClientLevel actionLevel;
    private BlockPos targetPos;
    private BlockHitResult obsidianHit;
    private BlockHitResult crystalHit;
    private int originalSlot;
    private int obsidianSlot;
    private int crystalSlot;
    private long phaseStartMs;
    private long crystalPlaceAtMs;
    private long nextAutomaticCrystalPlacementMs;
    private boolean performingInteraction;

    public KreysCrystalsModule() {
        super(TotemAutoInvAddon.CHALK_COMBAT,
            "kreys-crystals",
            "Places an Obsidian base and then an End Crystal at your clicked placement position."
        );
    }

    @Override
    public void onActivate() {
        resetAction(false);
    }

    @Override
    public void onDeactivate() {
        resetAction(true);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetAction(false);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetAction(false);
    }

    /**
     * Captures the actual vanilla right-click hit result before Minecraft sends
     * the original interaction. It is intentionally not based on a fresh raycast.
     */
    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (performingInteraction || event.hand != InteractionHand.MAIN_HAND) return;

        // Do not allow an additional manual use to place an item while this
        // module owns the selected slot for a pending action.
        if (phase != Phase.IDLE) {
            event.cancel();
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!autoObsidian.get()) return;

        originalSlot = mc.player.getInventory().getSelectedSlot();
        crystalSlot = findCrystalSlot();
        if (crystalSlot < 0) return;

        // The normal/safe configuration must begin with a Crystal in the
        // selected main hand. Disabling this setting explicitly permits using
        // another Crystal already present in the hotbar.
        if (onlyWhileHoldingCrystal.get() && !mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return;

        BlockPos clickedPos = event.result.getBlockPos();
        BlockState clickedState = mc.level.getBlockState(clickedPos);

        // An End Crystal is placed on the block contained in the original
        // BlockHitResult. Therefore an already clicked Obsidian/Bedrock block
        // is the actual crystal base, not a request to place an Obsidian block
        // above it. Leave this original interaction fully to vanilla. This also
        // means that an obstructed existing base never causes Obsidian stacking.
        if (isCrystalBase(clickedState)) return;

        BlockPos resolvedTarget = resolvePlacementPosition(event.result);
        BlockState targetState = mc.level.getBlockState(resolvedTarget);

        // A non-replaceable block cannot become the requested base. Let vanilla
        // handle the click instead of attempting to force an invalid placement.
        if (!targetState.canBeReplaced()) return;

        obsidianSlot = findObsidianHotbarSlot();
        if (obsidianSlot < 0) return;

        // This checks the client-side replaceability and entity collision using
        // Meteor's existing helper. The actual interaction still goes through
        // Minecraft's normal ClientPlayerInteractionManager below.
        if (!BlockUtils.canPlaceBlock(resolvedTarget, true, Blocks.OBSIDIAN)) return;

        targetPos = new BlockPos(resolvedTarget);
        obsidianHit = copyHit(event.result);
        crystalHit = crystalHit(targetPos);
        actionLevel = mc.level;
        phaseStartMs = System.currentTimeMillis();
        phase = Phase.WAIT_TO_PLACE_OBSIDIAN;

        // The End Crystal click must not be sent first: it would be invalid
        // until the server has accepted an Obsidian base.
        event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (phase == Phase.IDLE) return;

        if (!hasValidActionWorld()) {
            resetAction(false);
            return;
        }

        switch (phase) {
            case WAIT_TO_PLACE_OBSIDIAN -> placeObsidian();
            case WAIT_FOR_OBSIDIAN -> waitForObsidian();
            case WAIT_TO_PLACE_CRYSTAL -> placeCrystalWhenReady();
            case IDLE -> {
                // Exhaustive switch case for future state additions.
            }
        }
    }

    private void placeObsidian() {
        if (findObsidianHotbarSlot() < 0 || !mc.level.getBlockState(targetPos).canBeReplaced()) {
            resetAction(true);
            return;
        }

        // The stored slot must still contain Obsidian. We never move inventory
        // stacks, and we stop if the player changed the hotbar meanwhile.
        if (!mc.player.getInventory().getItem(obsidianSlot).is(Items.OBSIDIAN)) {
            resetAction(true);
            return;
        }

        InvUtils.swap(obsidianSlot, false);
        if (mc.player.getInventory().getSelectedSlot() != obsidianSlot) {
            resetAction(true);
            return;
        }

        useOnBlock(obsidianHit);
        phaseStartMs = System.currentTimeMillis();
        phase = Phase.WAIT_FOR_OBSIDIAN;
    }

    private void waitForObsidian() {
        // If the player deliberately chose another slot while the server was
        // responding, do not seize the hotbar back for a Crystal placement.
        if (mc.player.getInventory().getSelectedSlot() != obsidianSlot) {
            resetAction(false);
            return;
        }

        if (isCrystalBase(mc.level.getBlockState(targetPos))) {
            if (!autoCrystal.get()) {
                resetAction(true);
                return;
            }
            if (crystalSlot < 0 || !mc.player.getInventory().getItem(crystalSlot).is(Items.END_CRYSTAL)) {
                resetAction(true);
                return;
            }

            InvUtils.swap(crystalSlot, false);
            // Both the server-confirmation delay and the configurable rate cap
            // must have elapsed before this module sends its next crystal use.
            crystalPlaceAtMs = Math.max(
                System.currentTimeMillis() + placementDelay.get(),
                nextAutomaticCrystalPlacementMs
            );
            phase = Phase.WAIT_TO_PLACE_CRYSTAL;
            return;
        }

        if (System.currentTimeMillis() - phaseStartMs >= OBSIDIAN_CONFIRMATION_TIMEOUT_MS) {
            // No server/client confirmation arrived. Do not assume a placement
            // succeeded and do not send a possibly invalid Crystal interaction.
            resetAction(true);
        }
    }

    private void placeCrystalWhenReady() {
        if (mc.player.getInventory().getSelectedSlot() != crystalSlot
            || !mc.player.getInventory().getItem(crystalSlot).is(Items.END_CRYSTAL)
            || !isCrystalBase(mc.level.getBlockState(targetPos))
            || !mc.level.getBlockState(targetPos.above()).canBeReplaced()) {
            resetAction(true);
            return;
        }

        if (System.currentTimeMillis() < crystalPlaceAtMs) return;

        // This uses the same client interaction path as a normal right-click.
        // Minecraft and the server retain final validation for reach, entities,
        // block state, game mode and all server-side placement rules.
        useOnBlock(crystalHit);
        nextAutomaticCrystalPlacementMs = System.currentTimeMillis() + automaticPlacementIntervalMs();
        resetAction(true);
    }

    private void useOnBlock(BlockHitResult hitResult) {
        performingInteraction = true;
        try {
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            performingInteraction = false;
        }
    }

    /** Mirrors vanilla's target rule: use a replaceable clicked block, otherwise its clicked face. */
    private BlockPos resolvePlacementPosition(BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        return mc.level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
    }

    private static BlockHitResult copyHit(BlockHitResult hit) {
        return new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), hit.isInside());
    }

    private static BlockHitResult crystalHit(BlockPos base) {
        return new BlockHitResult(Vec3.atCenterOf(base).add(0.0, 0.5, 0.0), Direction.UP, base, false);
    }

    private int findObsidianHotbarSlot() {
        if (mc.player == null) return -1;
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getItem(slot).is(Items.OBSIDIAN)) return slot;
        }
        return -1;
    }

    private int findCrystalSlot() {
        if (mc.player == null) return -1;
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return mc.player.getInventory().getSelectedSlot();

        if (!onlyWhileHoldingCrystal.get()) {
            for (int slot = 0; slot <= 8; slot++) {
                if (mc.player.getInventory().getItem(slot).is(Items.END_CRYSTAL)) return slot;
            }
        }
        return -1;
    }

    private boolean hasValidActionWorld() {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.level == actionLevel && targetPos != null;
    }

    private static boolean isCrystalBase(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private long automaticPlacementIntervalMs() {
        return Math.max(1L, 1_000L / speed.get());
    }

    private void resetAction(boolean restoreOriginalSlot) {
        boolean worldChanged = actionLevel != mc.level;

        if (restoreOriginalSlot && mc.player != null && originalSlot >= 0 && originalSlot <= 8) {
            // Only restore when this module still owns the active slot. This
            // never overrides a slot the player manually selected mid-action.
            int selected = mc.player.getInventory().getSelectedSlot();
            if (selected == obsidianSlot || selected == crystalSlot) InvUtils.swap(originalSlot, false);
        }

        phase = Phase.IDLE;
        actionLevel = null;
        targetPos = null;
        obsidianHit = null;
        crystalHit = null;
        originalSlot = -1;
        obsidianSlot = -1;
        crystalSlot = -1;
        phaseStartMs = 0L;
        crystalPlaceAtMs = 0L;
        if (worldChanged) nextAutomaticCrystalPlacementMs = 0L;
        performingInteraction = false;
    }
}
