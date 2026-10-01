package de.example.totemautoinv.mixin;

import de.example.totemautoinv.PlayerActivityModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records only direct local interactions. The later server block update can
 * then be ignored by Player Bypass instead of being mistaken for another
 * player's building or breaking activity.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class PlayerActivityInputMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void chalk$rememberOwnBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        PlayerActivityModule.recordOwnAction(pos);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void chalk$rememberOwnPlacement(
        LocalPlayer player,
        InteractionHand hand,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        // A placement normally appears on the adjacent face. Tracking both
        // positions also covers direct-use blocks without assuming success.
        PlayerActivityModule.recordOwnAction(hitResult.getBlockPos());
        PlayerActivityModule.recordOwnAction(hitResult.getBlockPos().relative(hitResult.getDirection()));
    }
}
