package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Safely refills an empty offhand after the server's own Totem-pop status packet. */
public final class AutoInvTotemModule extends Module {
    private static final int SERVER_CONFIRMATION_TICKS = 20;
    private static final int MAX_MOVE_ATTEMPTS = 2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Open the player inventory automatically after a real Totem pop.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useOpenInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("use-open-inventory")
        .description("Allow moving a Totem while you already have your own inventory open.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> includeHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("include-hotbar")
        .description("Also search the hotbar for an existing Totem.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> openDelay = sgGeneral.add(new IntSetting.Builder()
        .name("open-delay")
        .description("Ticks between the Totem pop and opening the inventory.")
        .defaultValue(2)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .visible(autoOpen::get)
        .build()
    );

    private final Setting<Integer> moveDelay = sgGeneral.add(new IntSetting.Builder()
        .name("move-delay")
        .description("Ticks to wait after the player inventory is ready before moving the Totem.")
        .defaultValue(2)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> closeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("close-delay")
        .description("Ticks to wait before closing an inventory opened by this module.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .visible(autoOpen::get)
        .build()
    );

    private enum Phase {
        IDLE,
        WAIT_FOR_OFFHAND_EMPTY,
        WAIT_FOR_CLEAR_SCREEN,
        WAIT_TO_OPEN,
        WAIT_TO_MOVE,
        WAIT_FOR_CONFIRMATION,
        WAIT_TO_CLOSE
    }

    private Phase phase = Phase.IDLE;
    private int ticks;
    private int pendingPops;
    private int confirmationTicks;
    private int moveAttempts;
    private boolean autoOpenedInventory;

    public AutoInvTotemModule() {
        super(TotemAutoInvAddon.CHALK_COMBAT,
            "auto-totem",
            "After a real Totem pop, safely moves an existing Totem into the empty offhand."
        );
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onJoin(GameJoinedEvent event) {
        resetState();
    }

    @EventHandler
    private void onLeave(GameLeftEvent event) {
        resetState();
    }

    @EventHandler
    private void onTotemPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundEntityEventPacket packet)) return;
        if (packet.getEventId() != 35 || mc.player == null || mc.level == null) return;

        Player entity = packet.getEntity(mc.level) instanceof Player player ? player : null;
        if (entity != mc.player) return;

        // Every real server status packet is queued, so subsequent pops work too.
        pendingPops = Math.min(pendingPops + 1, 20);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;

        switch (phase) {
            case IDLE -> startNextCycle();
            case WAIT_FOR_OFFHAND_EMPTY -> waitForOffhandEmpty();
            case WAIT_FOR_CLEAR_SCREEN -> waitForClearScreen();
            case WAIT_TO_OPEN -> waitToOpen();
            case WAIT_TO_MOVE -> waitToMove();
            case WAIT_FOR_CONFIRMATION -> waitForConfirmation();
            case WAIT_TO_CLOSE -> waitToClose();
        }
    }

    private void startNextCycle() {
        if (pendingPops <= 0) return;
        if (hasTotemInOffhand()) {
            // The entity-status packet can arrive one or more ticks before the
            // inventory slot update. Wait for the real post-pop empty state.
            phase = Phase.WAIT_FOR_OFFHAND_EMPTY;
            ticks = 0;
            return;
        }
        if (!mc.player.getOffhandItem().isEmpty()) {
            // Another action already filled the offhand; never replace it.
            consumePop();
            return;
        }
        if (findTotemInventoryIndex() < 0) {
            // Never open a screen if no usable Totem actually exists.
            consumePop();
            return;
        }

        if (mc.screen instanceof InventoryScreen && useOpenInventory.get()) {
            beginMove(false);
            return;
        }
        if (mc.screen != null) {
            phase = Phase.WAIT_FOR_CLEAR_SCREEN;
            return;
        }
        if (!autoOpen.get()) {
            consumePop();
            return;
        }

        phase = Phase.WAIT_TO_OPEN;
        ticks = openDelay.get();
    }

    private void waitForOffhandEmpty() {
        if (!hasTotemInOffhand()) {
            phase = Phase.IDLE;
            ticks = 0;
            return;
        }
        if (++ticks < SERVER_CONFIRMATION_TICKS) return;

        // A replacement Totem is still present after a full second, so there is
        // no empty offhand to fill and no item should be moved or overwritten.
        consumePop();
        resetCycle();
    }

    private void waitForClearScreen() {
        if (hasTotemInOffhand()) {
            consumePop();
            resetCycle();
            return;
        }
        if (findTotemInventoryIndex() < 0) {
            consumePop();
            resetCycle();
            return;
        }
        if (mc.screen instanceof InventoryScreen && useOpenInventory.get()) {
            beginMove(false);
            return;
        }
        if (mc.screen != null) return;
        if (!autoOpen.get()) {
            consumePop();
            resetCycle();
            return;
        }

        phase = Phase.WAIT_TO_OPEN;
        ticks = openDelay.get();
    }

    private void waitToOpen() {
        if (mc.screen != null) {
            autoOpenedInventory = false;
            phase = Phase.WAIT_FOR_CLEAR_SCREEN;
            return;
        }
        if (findTotemInventoryIndex() < 0) {
            consumePop();
            resetCycle();
            return;
        }
        if (ticks-- > 1) return;

        mc.setScreen(new InventoryScreen(mc.player));
        beginMove(true);
    }

    private void beginMove(boolean openedByModule) {
        autoOpenedInventory = openedByModule;
        ticks = moveDelay.get();
        confirmationTicks = 0;
        moveAttempts = 0;
        phase = Phase.WAIT_TO_MOVE;
    }

    private void waitToMove() {
        if (!(mc.screen instanceof InventoryScreen)) {
            autoOpenedInventory = false;
            phase = Phase.WAIT_FOR_CLEAR_SCREEN;
            return;
        }
        if (ticks-- > 1) return;

        // Never overwrite an offhand item or touch a stack held by the cursor.
        if (!mc.player.getOffhandItem().isEmpty()) {
            consumePop();
            finishCycle();
            return;
        }
        if (!mc.player.containerMenu.getCarried().isEmpty()) return;

        int inventoryIndex = findTotemInventoryIndex();
        if (inventoryIndex < 0) {
            consumePop();
            finishCycle();
            return;
        }

        // Meteor translates inventory indices to the active player-container slots.
        // This avoids fragile hard-coded menu ids and offhand slot numbers.
        InvUtils.move().from(inventoryIndex).toOffhand();
        moveAttempts++;
        confirmationTicks = 0;
        phase = Phase.WAIT_FOR_CONFIRMATION;
    }

    private void waitForConfirmation() {
        if (!(mc.screen instanceof InventoryScreen)) {
            autoOpenedInventory = false;
            phase = Phase.WAIT_FOR_CLEAR_SCREEN;
            return;
        }
        if (hasTotemInOffhand()) {
            consumePop();
            finishCycle();
            return;
        }
        if (!mc.player.getOffhandItem().isEmpty()) {
            // Another action filled it. Leave that item untouched.
            consumePop();
            finishCycle();
            return;
        }
        if (++confirmationTicks < SERVER_CONFIRMATION_TICKS) return;

        // Server updates may arrive later than a few ticks. Retry once, and only
        // while the source Totem is still present and the cursor is empty.
        if (moveAttempts < MAX_MOVE_ATTEMPTS
            && findTotemInventoryIndex() >= 0
            && mc.player.containerMenu.getCarried().isEmpty()) {
            ticks = 2;
            phase = Phase.WAIT_TO_MOVE;
            return;
        }

        consumePop();
        finishCycle();
    }

    private void waitToClose() {
        if (!(mc.screen instanceof InventoryScreen)) {
            resetCycle();
            return;
        }
        if (ticks-- > 1) return;

        mc.setScreen(null);
        resetCycle();
    }

    private void finishCycle() {
        if (autoOpenedInventory && mc.screen instanceof InventoryScreen) {
            phase = Phase.WAIT_TO_CLOSE;
            ticks = closeDelay.get();
            return;
        }
        resetCycle();
    }

    private void consumePop() {
        pendingPops = Math.max(0, pendingPops - 1);
    }

    private void resetState() {
        phase = Phase.IDLE;
        ticks = 0;
        pendingPops = 0;
        confirmationTicks = 0;
        moveAttempts = 0;
        autoOpenedInventory = false;
    }

    private void resetCycle() {
        phase = Phase.IDLE;
        ticks = 0;
        confirmationTicks = 0;
        moveAttempts = 0;
        autoOpenedInventory = false;
    }

    private boolean hasTotemInOffhand() {
        return mc.player != null && mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private int findTotemInventoryIndex() {
        if (mc.player == null) return -1;
        var inventory = mc.player.getInventory();

        // Armor and offhand are intentionally excluded.
        for (int index = 9; index <= 35; index++) {
            if (inventory.getItem(index).is(Items.TOTEM_OF_UNDYING)) return index;
        }
        if (includeHotbar.get()) {
            for (int index = 0; index <= 8; index++) {
                if (inventory.getItem(index).is(Items.TOTEM_OF_UNDYING)) return index;
            }
        }
        return -1;
    }
}
