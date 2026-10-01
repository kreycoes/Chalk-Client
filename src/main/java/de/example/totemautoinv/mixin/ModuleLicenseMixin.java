package de.example.totemautoinv.mixin;

import de.example.totemautoinv.BetaTrialManager;
import de.example.totemautoinv.BuyerLicenseManager;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops Buyer-edition Chalk modules before their activation lifecycle begins. */
@Mixin(Module.class)
abstract class ModuleLicenseMixin {
    @Inject(method = "toggle", at = @At("HEAD"), cancellable = true)
    private void chalk$requireLicenseBeforeActivation(CallbackInfo info) {
        Module module = (Module) (Object) this;
        if (!module.isActive() && (!BuyerLicenseManager.canActivate(module) || !BetaTrialManager.canActivate(module))) info.cancel();
    }
}
