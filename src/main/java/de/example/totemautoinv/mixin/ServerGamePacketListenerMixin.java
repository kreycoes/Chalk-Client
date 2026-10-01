package de.example.totemautoinv.mixin;

import de.example.totemautoinv.BoatFlyModule;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the integrated server accept the collision-free Boat Fly position for
 * its own local player. Remote and dedicated servers never pass the module's
 * integrated-server check.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Shadow
    protected abstract boolean isEntityCollidingWithAnythingNew(LevelReader level, Entity entity, AABB oldBox, double x, double y, double z);

    @Redirect(
        method = "handleMoveVehicle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isEntityCollidingWithAnythingNew(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;DDD)Z"
        )
    )
    private boolean chalkClient$allowSingleplayerBoatPhase(
        ServerGamePacketListenerImpl listener,
        LevelReader level,
        Entity vehicle,
        AABB oldBox,
        double x,
        double y,
        double z
    ) {
        if (BoatFlyModule.allowsIntegratedServerPhase(vehicle, player)) return false;
        return isEntityCollidingWithAnythingNew(level, vehicle, oldBox, x, y, z);
    }

    @Redirect(
        method = "handleMoveVehicle",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean chalkClient$skipSingleplayerBoatCorrection(ServerLevel level, Entity vehicle, AABB oldBox) {
        // Returning false bypasses only the position-correction branch. The
        // shared guard still refuses remote or dedicated-server movement.
        if (BoatFlyModule.allowsIntegratedServerPhase(vehicle, player)) return false;
        return level.noCollision(vehicle, oldBox);
    }
}
