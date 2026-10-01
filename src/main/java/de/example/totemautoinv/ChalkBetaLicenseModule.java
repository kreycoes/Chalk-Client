package de.example.totemautoinv;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.concurrent.TimeUnit;

/** Settings entry used by a beta tester to start their 24-hour test window. */
public final class ChalkBetaLicenseModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> betaCode = sgGeneral.add(new StringSetting.Builder()
        .name("beta-code")
        .description("Enter the beta activation code. The 24-hour test window starts when it is accepted.")
        .defaultValue("")
        .placeholder("Enter beta code")
        .onChanged(BetaTrialManager::submitCode)
        .wide()
        .build()
    );

    public ChalkBetaLicenseModule() {
        super(TotemAutoInvAddon.KREY_ADDON, "chalk-beta-tester", "Enter the beta code to start the 24-hour Chalk Client Beta Tester trial.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        if (BetaTrialManager.isUsable()) info("Beta is active: %s remaining.", getInfoString());
        else if (BetaTrialManager.isExpired()) warning("The 24-hour beta period has expired.");
        else BetaTrialManager.submitCode(betaCode.get());
    }

    @Override
    public String getInfoString() {
        if (!BetaTrialManager.isUsable()) return BetaTrialManager.isExpired() ? "Expired" : "Locked";

        long remaining = BetaTrialManager.getRemainingMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(remaining);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60L;
        return String.format("%dh %dm", hours, minutes);
    }
}
