package de.example.totemautoinv;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.fabricmc.loader.api.FabricLoader;
public final class BuyerLicenseManager {
    private BuyerLicenseManager() {}
    public static boolean isBuyerEdition() {
        return FabricLoader.getInstance().getModContainer("totemautoinv").map(c -> c.getMetadata().getVersion().getFriendlyString().contains("lifetime")).orElse(false);
    }
    public static void initialize() { if (isBuyerEdition()) OnlineLicense.initialize(); }
    public static boolean canActivate(Module module) {
        return !isBuyerEdition() || !TotemAutoInvAddon.isChalkCategory(module.category) || module instanceof ChalkLicenseModule || isLicensed();
    }
    public static void submitCode(String code) { if (isBuyerEdition()) OnlineLicense.submit(code); }
    public static boolean isLicensed() { return OnlineLicense.usable(); }
}
