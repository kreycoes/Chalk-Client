package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.entity.EntityMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/** Controls only the boat currently driven by the local player. */
public final class BoatFlyModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal boat speed in blocks per second.")
        .defaultValue(10.0)
        .min(0.5)
        .sliderRange(0.5, 30.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical boat speed in blocks per second.")
        .defaultValue(6.0)
        .min(0.5)
        .sliderRange(0.5, 20.0)
        .build()
    );

    private final Setting<Boolean> phase = sgGeneral.add(new BoolSetting.Builder()
        .name("phase-through-blocks")
        .description("Disable client-side boat collision while this module controls the boat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sneakDescends = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak-descends")
        .description("Use Sneak to fly down instead of leaving the boat.")
        .defaultValue(true)
        .build()
    );

    private Entity controlledBoat;
    private boolean previousNoPhysics;
    private boolean previousNoGravity;
    private boolean descending;

    public BoatFlyModule() {
        super(TotemAutoInvAddon.KREY_ADDON,
            "boat-fly",
            "Fly and phase through blocks with the boat you are currently controlling."
        );
    }

    @Override
    public void onActivate() {
        descending = false;
    }

    @Override
    public void onDeactivate() {
        restoreBoat();
        restoreSneakKey();
        descending = false;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        restoreBoat();
        restoreSneakKey();
        descending = false;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        Entity boat = getControlledBoat();
        if (boat == null) {
            restoreBoat();
            restoreSneakKey();
            descending = false;
            return;
        }

        beginControlling(boat);
        boat.noPhysics = phase.get();
        boat.setNoGravity(true);

        descending = sneakDescends.get() && Input.isPressed(mc.options.keyShift);
        if (descending) {
            // Minecraft normally uses Shift as its dismount key. Input is read
            // from Meteor's physical-key tracker first, then masked only for
            // vanilla's boat logic during this tick.
            mc.options.keyShift.setDown(false);
        } else {
            restoreSneakKey();
        }
    }

    @EventHandler
    private void onEntityMove(EntityMoveEvent event) {
        if (event.entity != controlledBoat || mc.player == null) return;

        Vec3 horizontal = PlayerUtils.getHorizontalVelocity(horizontalSpeed.get());
        double vertical = 0.0;
        if (Input.isPressed(mc.options.keyJump)) vertical += verticalSpeed.get() / 20.0;
        if (descending) vertical -= verticalSpeed.get() / 20.0;

        // EntityMoveEvent is emitted by Meteor immediately before this boat is
        // moved. Updating the passed Vec3 therefore controls the actual tick's
        // movement instead of storing stale position history.
        ((IVec3d) event.movement).meteor$set(horizontal.x(), vertical, horizontal.z());
    }

    private Entity getControlledBoat() {
        if (mc.player == null) return null;

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof AbstractBoat)) return null;
        if (((AbstractBoat) vehicle).getControllingPassenger() != mc.player) return null;

        return vehicle;
    }

    private void beginControlling(Entity boat) {
        if (controlledBoat == boat) return;

        restoreBoat();
        controlledBoat = boat;
        previousNoPhysics = boat.noPhysics;
        previousNoGravity = boat.isNoGravity();
    }

    private void restoreBoat() {
        if (controlledBoat == null) return;

        if (!controlledBoat.isRemoved()) {
            controlledBoat.noPhysics = previousNoPhysics;
            controlledBoat.setNoGravity(previousNoGravity);
        }
        controlledBoat = null;
    }

    private void restoreSneakKey() {
        if (mc != null) mc.options.keyShift.setDown(Input.isPressed(mc.options.keyShift));
    }

    /**
     * Used only by the integrated-server mixins. A dedicated or remote server
     * never qualifies, so this cannot change another server's collision rules.
     */
    public static boolean allowsIntegratedServerPhase(Entity boat, ServerPlayer serverPlayer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || !minecraft.hasSingleplayerServer()) return false;
        if (!(boat instanceof AbstractBoat) || serverPlayer == null) return false;
        if (!serverPlayer.getUUID().equals(minecraft.player.getUUID())) return false;

        BoatFlyModule module = Modules.get().get(BoatFlyModule.class);
        if (module == null || !module.isActive() || !module.phase.get()) return false;

        return ((AbstractBoat) boat).getControllingPassenger() == serverPlayer;
    }
}
