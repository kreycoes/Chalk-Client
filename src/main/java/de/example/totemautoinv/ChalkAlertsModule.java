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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * One configurable home for Chalk's local client-side notifications.
 * Every alert has its own switch; disabled alerts do no work and never send a
 * message. The spawner check only examines chunks already sent to the client.
 */
public final class ChalkAlertsModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlayer = settings.createGroup("Player");
    private final SettingGroup sgSupplies = settings.createGroup("Supplies");
    private final SettingGroup sgWorld = settings.createGroup("World");
    private final SettingGroup sgSpawner = settings.createGroup("Spawner");

    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send enabled Chalk Alerts as client chat messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lowDurability = sgPlayer.add(toggle("low-durability", "Alert when equipped armor or a held item is low on durability."));
    private final Setting<Integer> durabilityThreshold = sgPlayer.add(number("durability-threshold", "Durability percentage that triggers the alert.", 15, 1, 100, 1, 50, lowDurability));
    private final Setting<Boolean> lowHealth = sgPlayer.add(toggle("low-health", "Alert when your health is low."));
    private final Setting<Integer> healthThreshold = sgPlayer.add(number("health-threshold", "Health amount that triggers the alert.", 8, 0, 40, 0, 20, lowHealth));
    private final Setting<Boolean> lowHunger = sgPlayer.add(toggle("low-hunger", "Alert when your hunger is low."));
    private final Setting<Integer> hungerThreshold = sgPlayer.add(number("hunger-threshold", "Hunger amount that triggers the alert.", 6, 0, 20, 0, 20, lowHunger));
    private final Setting<Boolean> inventoryFull = sgPlayer.add(toggle("inventory-full", "Alert when few inventory slots remain free."));
    private final Setting<Integer> freeSlotsThreshold = sgPlayer.add(number("free-slots-threshold", "Alert when this many or fewer inventory slots are free.", 2, 0, 36, 0, 18, inventoryFull));
    private final Setting<Boolean> emptyOffhand = sgPlayer.add(toggle("empty-offhand", "Alert when your offhand becomes empty."));
    private final Setting<Boolean> missingArmor = sgPlayer.add(toggle("missing-armor", "Alert when at least one armor slot is empty."));
    private final Setting<Boolean> burning = sgPlayer.add(toggle("burning", "Alert when you start burning."));
    private final Setting<Boolean> lowAir = sgPlayer.add(toggle("low-air", "Alert when your remaining air is low."));
    private final Setting<Integer> airThreshold = sgPlayer.add(number("air-threshold", "Remaining air that triggers the alert.", 60, 0, 300, 0, 150, lowAir));
    private final Setting<Boolean> inWater = sgPlayer.add(toggle("in-water", "Alert when you enter water."));
    private final Setting<Boolean> inLava = sgPlayer.add(toggle("in-lava", "Alert when you enter lava."));
    private final Setting<Boolean> mounted = sgPlayer.add(toggle("mounted", "Alert when you mount a vehicle."));

    private final Setting<Boolean> totemSupply = sgSupplies.add(toggle("totem-supply", "Alert when your Totem supply is low."));
    private final Setting<Integer> totemThreshold = sgSupplies.add(number("totem-threshold", "Totem count that triggers the alert.", 1, 0, 2304, 0, 64, totemSupply));
    private final Setting<Boolean> crystalSupply = sgSupplies.add(toggle("crystal-supply", "Alert when your End Crystal supply is low."));
    private final Setting<Integer> crystalThreshold = sgSupplies.add(number("crystal-threshold", "End Crystal count that triggers the alert.", 16, 0, 2304, 0, 128, crystalSupply));
    private final Setting<Boolean> gappleSupply = sgSupplies.add(toggle("gapple-supply", "Alert when your Golden Apple supply is low."));
    private final Setting<Integer> gappleThreshold = sgSupplies.add(number("gapple-threshold", "Golden Apple count that triggers the alert.", 8, 0, 2304, 0, 128, gappleSupply));
    private final Setting<Boolean> pearlSupply = sgSupplies.add(toggle("pearl-supply", "Alert when your Ender Pearl supply is low."));
    private final Setting<Integer> pearlThreshold = sgSupplies.add(number("pearl-threshold", "Ender Pearl count that triggers the alert.", 8, 0, 2304, 0, 128, pearlSupply));
    private final Setting<Boolean> rocketSupply = sgSupplies.add(toggle("rocket-supply", "Alert when your Firework Rocket supply is low."));
    private final Setting<Integer> rocketThreshold = sgSupplies.add(number("rocket-threshold", "Firework Rocket count that triggers the alert.", 16, 0, 2304, 0, 256, rocketSupply));
    private final Setting<Boolean> xpBottleSupply = sgSupplies.add(toggle("xp-bottle-supply", "Alert when your Experience Bottle supply is low."));
    private final Setting<Integer> xpBottleThreshold = sgSupplies.add(number("xp-bottle-threshold", "Experience Bottle count that triggers the alert.", 16, 0, 2304, 0, 256, xpBottleSupply));
    private final Setting<Boolean> obsidianSupply = sgSupplies.add(toggle("obsidian-supply", "Alert when your Obsidian supply is low."));
    private final Setting<Integer> obsidianThreshold = sgSupplies.add(number("obsidian-threshold", "Obsidian count that triggers the alert.", 32, 0, 2304, 0, 256, obsidianSupply));
    private final Setting<Boolean> arrowSupply = sgSupplies.add(toggle("arrow-supply", "Alert when your Arrow supply is low."));
    private final Setting<Integer> arrowThreshold = sgSupplies.add(number("arrow-threshold", "Arrow count that triggers the alert.", 32, 0, 2304, 0, 256, arrowSupply));

    private final Setting<Boolean> nearbyPlayer = sgWorld.add(toggle("nearby-player", "Alert about other player entities already visible to your client."));
    private final Setting<Integer> playerRange = sgWorld.add(number("player-range", "Maximum distance for visible-player alerts.", 64, 4, 256, 8, 128, nearbyPlayer));
    private final Setting<Boolean> weather = sgWorld.add(toggle("weather", "Alert when local rain or thunder starts or stops."));
    private final Setting<Boolean> dayNight = sgWorld.add(toggle("day-night", "Alert when the local world changes between day and night."));
    private final Setting<Boolean> chunkTransition = sgWorld.add(toggle("chunk-transition", "Alert when you cross a normal chunk border."));
    private final Setting<Boolean> lowFps = sgWorld.add(toggle("low-fps", "Alert when your local frame rate is low."));
    private final Setting<Integer> minimumFps = sgWorld.add(number("minimum-fps", "FPS value that triggers the alert.", 30, 1, 240, 10, 120, lowFps));
    private final Setting<Boolean> entityCrowd = sgWorld.add(toggle("entity-crowd", "Alert when many client-rendered entities are nearby."));
    private final Setting<Integer> entityRange = sgWorld.add(number("entity-range", "Distance used to count rendered entities.", 32, 4, 128, 8, 64, entityCrowd));
    private final Setting<Integer> entityThreshold = sgWorld.add(number("entity-threshold", "Entity count that triggers the alert.", 80, 1, 1000, 10, 250, entityCrowd));

    private final Setting<Boolean> spawner = sgSpawner.add(toggle("normal-spawner", "Alert when a normal mob spawner exists in a client-loaded chunk."));
    private final Setting<Integer> spawnerScanDistance = sgSpawner.add(number("scan-distance", "Maximum chunk distance to inspect for normal spawners.", 8, 1, 32, 1, 16, spawner));
    private final Setting<Integer> spawnerRescanInterval = sgSpawner.add(number("rescan-interval", "Ticks between checks of already loaded chunks.", 200, 20, 1200, 20, 400, spawner));
    private final Setting<Integer> spawnerChunksPerTick = sgSpawner.add(number("chunks-per-tick", "Client-loaded chunks to process each tick.", 2, 1, 8, 1, 4, spawner));

    private final Set<String> activeAlerts = new HashSet<>();
    private final Set<UUID> alertedPlayers = new HashSet<>();
    private final LongOpenHashSet detectedSpawners = new LongOpenHashSet();
    private final LongOpenHashSet queuedSpawnerChunks = new LongOpenHashSet();
    private final ArrayDeque<Long> spawnerScanQueue = new ArrayDeque<>();

    private ClientLevel lastLevel;
    private int weatherState = -1;
    private int dayNightState = -1;
    private int currentChunkX;
    private int currentChunkZ;
    private boolean chunkInitialized;
    private int ticksUntilSpawnerRescan;
    private int nearbyPlayers;

    public ChalkAlertsModule() {
        super(TotemAutoInvAddon.CHALK_UTILITY, "chalk-alerts", "Choose exactly which local client-side alerts Chalk Client should send.");
    }

    @Override
    public void onActivate() {
        resetAll();
        lastLevel = mc.level;
    }

    @Override
    public void onDeactivate() {
        resetAll();
        lastLevel = null;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetAll();
        lastLevel = mc.level;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetAll();
        lastLevel = null;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (spawner.get() && mc.player != null && mc.level != null) queueSpawnerChunk(event.chunk().getPos().x, event.chunk().getPos().z);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!spawner.get() || mc.player == null || mc.level == null || !isSpawnerChunkInRange(event.pos.getX() >> 4, event.pos.getZ() >> 4)) return;

        long key = event.pos.asLong();
        if (event.newState.is(Blocks.SPAWNER)) addSpawner(key);
        else detectedSpawners.remove(key);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            resetAll();
            lastLevel = mc.level;
            return;
        }

        if (mc.level != lastLevel) {
            resetAll();
            lastLevel = mc.level;
        }

        checkPlayerAlerts();
        checkSupplyAlerts();
        checkWorldAlerts();
        updateSpawnerAlerts();
    }

    @Override
    public String getInfoString() {
        if (spawner.get() && !detectedSpawners.isEmpty()) return detectedSpawners.size() + " spawners";
        if (nearbyPlayer.get() && nearbyPlayers > 0) return nearbyPlayers + " players";
        return activeAlerts.isEmpty() ? null : activeAlerts.size() + " active";
    }

    private void checkPlayerAlerts() {
        int durability = lowestDurabilityPercent();
        alert("durability", lowDurability.get(), durability <= durabilityThreshold.get(), "An equipped item is low on durability: " + durability + "% remaining.");

        int health = Math.round(mc.player.getHealth() + mc.player.getAbsorptionAmount());
        alert("health", lowHealth.get(), health <= healthThreshold.get(), "Health is low: " + health + ".");

        int hunger = mc.player.getFoodData().getFoodLevel();
        alert("hunger", lowHunger.get(), hunger <= hungerThreshold.get(), "Hunger is low: " + hunger + ".");

        int freeSlots = countFreeInventorySlots();
        alert("inventory", inventoryFull.get(), freeSlots <= freeSlotsThreshold.get(), "Inventory is almost full: " + freeSlots + " free slot(s).");

        alert("offhand", emptyOffhand.get(), mc.player.getOffhandItem().isEmpty(), "Offhand is empty.");
        boolean armorMissing = mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || mc.player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || mc.player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        alert("armor", missingArmor.get(), armorMissing, "At least one armor slot is empty.");
        alert("burning", burning.get(), mc.player.isOnFire(), "You are on fire.");
        alert("air", lowAir.get(), mc.player.getAirSupply() <= airThreshold.get(), "Air supply is low.");
        alert("water", inWater.get(), mc.player.isInWater(), "You entered water.");
        alert("lava", inLava.get(), mc.player.isInLava(), "You entered lava.");
        alert("mounted", mounted.get(), mc.player.getVehicle() != null, "You mounted a vehicle.");
    }

    private void checkSupplyAlerts() {
        checkSupply("totems", totemSupply, totemThreshold, Items.TOTEM_OF_UNDYING, "Totem");
        checkSupply("crystals", crystalSupply, crystalThreshold, Items.END_CRYSTAL, "End Crystal");
        checkSupply("gapples", gappleSupply, gappleThreshold, Items.GOLDEN_APPLE, "Golden Apple");
        checkSupply("pearls", pearlSupply, pearlThreshold, Items.ENDER_PEARL, "Ender Pearl");
        checkSupply("rockets", rocketSupply, rocketThreshold, Items.FIREWORK_ROCKET, "Firework Rocket");
        checkSupply("xp-bottles", xpBottleSupply, xpBottleThreshold, Items.EXPERIENCE_BOTTLE, "Experience Bottle");
        checkSupply("obsidian", obsidianSupply, obsidianThreshold, Items.OBSIDIAN, "Obsidian");
        checkSupply("arrows", arrowSupply, arrowThreshold, Items.ARROW, "Arrow");
    }

    private void checkWorldAlerts() {
        updateNearbyPlayers();

        int fps = Minecraft.getInstance().getFps();
        alert("fps", lowFps.get(), fps <= minimumFps.get(), "Low FPS: " + fps + ".");

        int entities = countNearbyEntities();
        alert("entities", entityCrowd.get(), entities >= entityThreshold.get(), "High nearby entity count: " + entities + ".");

        int currentWeather = mc.level.isThundering() ? 2 : mc.level.isRaining() ? 1 : 0;
        if (weather.get() && weatherState != -1 && currentWeather != weatherState) notifyAlert(currentWeather == 2 ? "Thunder started." : currentWeather == 1 ? "Rain started." : "Weather cleared.");
        weatherState = currentWeather;

        long time = Math.floorMod(mc.level.getDayTime(), 24000L);
        int currentDayNight = time >= 12000L && time < 23000L ? 1 : 0;
        if (dayNight.get() && dayNightState != -1 && currentDayNight != dayNightState) notifyAlert(currentDayNight == 1 ? "Night started." : "Day started.");
        dayNightState = currentDayNight;

        int chunkX = ((int) Math.floor(mc.player.getX())) >> 4;
        int chunkZ = ((int) Math.floor(mc.player.getZ())) >> 4;
        if (chunkTransition.get() && chunkInitialized && (chunkX != currentChunkX || chunkZ != currentChunkZ)) notifyAlert("Entered chunk " + chunkX + ", " + chunkZ + ".");
        currentChunkX = chunkX;
        currentChunkZ = chunkZ;
        chunkInitialized = true;
    }

    private void updateNearbyPlayers() {
        if (!nearbyPlayer.get()) {
            alertedPlayers.clear();
            nearbyPlayers = 0;
            return;
        }

        Set<UUID> current = new HashSet<>();
        nearbyPlayers = 0;
        int rangeSquared = playerRange.get() * playerRange.get();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || player.getUUID().equals(mc.player.getUUID()) || player.distanceToSqr(mc.player) > rangeSquared) continue;
            current.add(player.getUUID());
            nearbyPlayers++;
            if (alertedPlayers.add(player.getUUID())) notifyAlert("Player detected nearby: " + player.getName().getString() + ".");
        }
        alertedPlayers.retainAll(current);
    }

    private void updateSpawnerAlerts() {
        if (!spawner.get()) {
            clearSpawnerData();
            return;
        }

        if (ticksUntilSpawnerRescan-- <= 0) {
            queueLoadedSpawnerChunks();
            ticksUntilSpawnerRescan = spawnerRescanInterval.get();
        }

        processSpawnerQueue();
        removeUnavailableSpawners();
    }

    private void checkSupply(String key, Setting<Boolean> enabled, Setting<Integer> threshold, Item item, String label) {
        if (!enabled.get()) {
            activeAlerts.remove(key);
            return;
        }

        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        alert(key, true, count <= threshold.get(), label + " supply is low: " + count + " remaining.");
    }

    private void alert(String key, boolean enabled, boolean current, String message) {
        if (!enabled || !current) {
            activeAlerts.remove(key);
            return;
        }
        if (activeAlerts.add(key)) notifyAlert(message);
    }

    private void notifyAlert(String message) {
        if (chatAlerts.get()) info(message);
    }

    private int lowestDurabilityPercent() {
        int lowest = 100;
        lowest = Math.min(lowest, durabilityPercent(mc.player.getMainHandItem()));
        lowest = Math.min(lowest, durabilityPercent(mc.player.getOffhandItem()));
        lowest = Math.min(lowest, durabilityPercent(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
        lowest = Math.min(lowest, durabilityPercent(mc.player.getItemBySlot(EquipmentSlot.CHEST)));
        lowest = Math.min(lowest, durabilityPercent(mc.player.getItemBySlot(EquipmentSlot.LEGS)));
        return Math.min(lowest, durabilityPercent(mc.player.getItemBySlot(EquipmentSlot.FEET)));
    }

    private static int durabilityPercent(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 100;
        return Math.round((stack.getMaxDamage() - stack.getDamageValue()) * 100.0f / stack.getMaxDamage());
    }

    private int countFreeInventorySlots() {
        int free = 0;
        for (int slot = 0; slot < 36; slot++) if (mc.player.getInventory().getItem(slot).isEmpty()) free++;
        return free;
    }

    private int countNearbyEntities() {
        if (!entityCrowd.get()) return 0;
        int count = 0;
        int rangeSquared = entityRange.get() * entityRange.get();
        for (Entity entity : mc.level.entitiesForRendering()) if (entity != mc.player && entity.distanceToSqr(mc.player) <= rangeSquared) count++;
        return count;
    }

    private void queueLoadedSpawnerChunks() {
        int chunkX = blockToChunk(mc.player.getX());
        int chunkZ = blockToChunk(mc.player.getZ());
        int distance = spawnerScanDistance.get();
        for (int x = chunkX - distance; x <= chunkX + distance; x++) {
            for (int z = chunkZ - distance; z <= chunkZ + distance; z++) {
                if (mc.level.hasChunk(x, z)) queueSpawnerChunk(x, z);
            }
        }
    }

    private void queueSpawnerChunk(int chunkX, int chunkZ) {
        if (!isSpawnerChunkInRange(chunkX, chunkZ)) return;
        long key = packChunk(chunkX, chunkZ);
        if (queuedSpawnerChunks.add(key)) spawnerScanQueue.addLast(key);
    }

    private void processSpawnerQueue() {
        int remaining = spawnerChunksPerTick.get();
        while (remaining-- > 0 && !spawnerScanQueue.isEmpty()) {
            long key = spawnerScanQueue.removeFirst();
            queuedSpawnerChunks.remove(key);
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            if (!mc.level.hasChunk(chunkX, chunkZ) || !isSpawnerChunkInRange(chunkX, chunkZ)) continue;
            scanSpawnerChunk(mc.level.getChunk(chunkX, chunkZ), chunkX, chunkZ);
        }
    }

    private void scanSpawnerChunk(LevelChunk chunk, int chunkX, int chunkZ) {
        LongOpenHashSet found = new LongOpenHashSet();
        for (BlockPos pos : chunk.getBlockEntities().keySet()) if (chunk.getBlockState(pos).is(Blocks.SPAWNER)) found.add(pos.asLong());

        for (LongIterator iterator = detectedSpawners.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            BlockPos pos = BlockPos.of(key);
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ && !found.contains(key)) iterator.remove();
        }
        for (LongIterator iterator = found.iterator(); iterator.hasNext();) addSpawner(iterator.nextLong());
    }

    private void addSpawner(long key) {
        if (!detectedSpawners.add(key)) return;
        BlockPos pos = BlockPos.of(key);
        notifyAlert("Spawner loaded at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ").");
    }

    private void removeUnavailableSpawners() {
        for (LongIterator iterator = detectedSpawners.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            BlockPos pos = BlockPos.of(key);
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !isSpawnerChunkInRange(pos.getX() >> 4, pos.getZ() >> 4)) iterator.remove();
        }
        for (Iterator<Long> iterator = spawnerScanQueue.iterator(); iterator.hasNext();) {
            long key = iterator.next();
            if (!mc.level.hasChunk(unpackChunkX(key), unpackChunkZ(key)) || !isSpawnerChunkInRange(unpackChunkX(key), unpackChunkZ(key))) {
                iterator.remove();
                queuedSpawnerChunks.remove(key);
            }
        }
    }

    private boolean isSpawnerChunkInRange(int chunkX, int chunkZ) {
        int deltaX = chunkX - blockToChunk(mc.player.getX());
        int deltaZ = chunkZ - blockToChunk(mc.player.getZ());
        int distance = spawnerScanDistance.get();
        return deltaX * deltaX + deltaZ * deltaZ <= distance * distance;
    }

    private void clearSpawnerData() {
        detectedSpawners.clear();
        queuedSpawnerChunks.clear();
        spawnerScanQueue.clear();
        ticksUntilSpawnerRescan = 0;
    }

    private void resetAll() {
        activeAlerts.clear();
        alertedPlayers.clear();
        clearSpawnerData();
        weatherState = -1;
        dayNightState = -1;
        chunkInitialized = false;
        nearbyPlayers = 0;
    }

    private static Setting<Boolean> toggle(String name, String description) {
        return new BoolSetting.Builder().name(name).description(description).defaultValue(false).build();
    }

    private static Setting<Integer> number(String name, String description, int defaultValue, int min, int max, int sliderMin, int sliderMax, Setting<Boolean> visibleWhen) {
        return new IntSetting.Builder().name(name).description(description).defaultValue(defaultValue).min(min).max(max).sliderRange(sliderMin, sliderMax).visible(visibleWhen::get).build();
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
