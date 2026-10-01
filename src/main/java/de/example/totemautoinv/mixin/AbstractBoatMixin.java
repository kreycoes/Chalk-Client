package de.example.totemautoinv.mixin;

import de.example.totemautoinv.BoatFlyModule;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Boat Fly's collision state to the singleplayer server-side boat. */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
    @Unique private boolean chalkClient$modified;
    @Unique private boolean chalkClient$previousNoPhysics;
    @Unique private boolean chalkClient$previousNoGravity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void chalkClient$syncSingleplayerPhase(CallbackInfo ci) {
        Entity boat = (Entity) (Object) this;
        if (boat.level().isClientSide()) return;

        ServerPlayer player = ((AbstractBoat) (Object) this).getControllingPassenger() instanceof ServerPlayer serverPlayer
            ? serverPlayer
            : null;
        boolean enabled = BoatFlyModule.allowsIntegratedServerPhase(boat, player);

        if (enabled) {
            if (!chalkClient$modified) {
                chalkClient$modified = true;
                chalkClient$previousNoPhysics = boat.noPhysics;
                chalkClient$previousNoGravity = boat.isNoGravity();
            }

            boat.noPhysics = true;
            boat.setNoGravity(true);
        } else if (chalkClient$modified) {
            boat.noPhysics = chalkClient$previousNoPhysics;
            boat.setNoGravity(chalkClient$previousNoGravity);
            chalkClient$modified = false;
        }
    }
}
