package de.example.totemautoinv;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.fabricmc.loader.api.FabricLoader;
public final class BetaTrialManager {
    public static final long TRIAL_DURATION_MILLIS = 86400000L;
    private BetaTrialManager() {}
    public static boolean isBetaEdition() {
        return FabricLoader.getInstance().getModContainer("totemautoinv").map(c -> c.getMetadata().getVersion().getFriendlyString().contains("beta")).orElse(false);
    }
    public static void initialize() { if (isBetaEdition()) OnlineLicense.initialize(); }
    public static boolean canActivate(Module module) {
        return !isBetaEdition() || !TotemAutoInvAddon.isChalkCategory(module.category) || module instanceof ChalkBetaLicenseModule || isUsable();
    }
    public static void submitCode(String code) { if (isBetaEdition()) OnlineLicense.submit(code); }
    public static boolean isUsable() { return OnlineLicense.usable(); }
    public static boolean isExpired() { return OnlineLicense.expired(); }
    public static long getRemainingMillis() { return OnlineLicense.remaining(); }
}
