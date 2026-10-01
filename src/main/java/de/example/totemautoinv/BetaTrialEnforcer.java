package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;

/** Disables Chalk modules once the unactivated or expired beta may no longer be used. */
public final class BetaTrialEnforcer {
    private boolean notified;

    public void enforceNow() {
        if ((!BetaTrialManager.isBetaEdition() && !BuyerLicenseManager.isBuyerEdition()) || OnlineLicense.usable()) return;

        for (Category category : TotemAutoInvAddon.getChalkCategories()) {
            for (Module module : new ArrayList<>(Modules.get().getGroup(category))) {
                if (!(module instanceof ChalkBetaLicenseModule) && !(module instanceof ChalkLicenseModule) && module.isActive()) module.disable();
            }
        }

        Hud hud = Hud.get();
        if (hud != null) {
            for (HudElement element : hud) {
                if (element instanceof ChalkOverlayHud && element.isActive()) element.toggle();
            }
        }

        if (!notified) {
            ChatUtils.errorPrefix(
                "Chalk License",
                BetaTrialManager.isExpired()
                    ? "The 24-hour beta period has expired."
                    : "Online license verification required. Open the Chalk license module and enter your code."
            );
            notified = true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        enforceNow();
    }
}
