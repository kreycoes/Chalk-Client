package de.example.totemautoinv;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;

/** Settings entry where a Lifetime-edition customer can submit a purchase code. */
public final class ChalkLicenseModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> purchaseCode = sgGeneral.add(new StringSetting.Builder()
        .name("purchase-code")
        .description("Enter the 12-character purchase code supplied by the seller. Hyphens are optional.")
        .defaultValue("")
        .placeholder("AB12-CD34-EF56")
        .onChanged(BuyerLicenseManager::submitCode)
        .wide()
        .build()
    );

    public ChalkLicenseModule() {
        super(TotemAutoInvAddon.KREY_ADDON, "chalk-lifetime-license", "Enter a Lifetime purchase code to unlock Chalk Client modules.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        if (BuyerLicenseManager.isLicensed()) info("Lifetime license is active.");
        else BuyerLicenseManager.submitCode(purchaseCode.get());
    }

    @Override
    public String getInfoString() {
        return OnlineLicense.status();
    }
}
