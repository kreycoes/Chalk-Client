package de.example.totemautoinv;

import com.mojang.blaze3d.platform.InputConstants;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import net.minecraft.world.item.Items;

public final class TotemAutoInvAddon extends MeteorAddon {
    public static final Category KREY_ADDON = new Category("Krey Addon", Items.TOTEM_OF_UNDYING.getDefaultInstance());
    public static final Category CHALK_ESP = new Category("Chalk ESP", Items.SPYGLASS.getDefaultInstance());
    public static final Category CHALK_COMBAT = new Category("Chalk Combat", Items.END_CRYSTAL.getDefaultInstance());
    public static final Category CHALK_UTILITY = new Category("Chalk Utility", Items.COMPASS.getDefaultInstance());
    public static final HudGroup CHALK_HUD = new HudGroup("Chalk Client");
    private ChalkClientConfig chalkConfig;

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(KREY_ADDON);
        Modules.registerCategory(CHALK_ESP);
        Modules.registerCategory(CHALK_COMBAT);
        Modules.registerCategory(CHALK_UTILITY);
    }

    public static boolean isChalkCategory(Category category) {
        return category == KREY_ADDON || category == CHALK_ESP || category == CHALK_COMBAT || category == CHALK_UTILITY;
    }

    public static Category[] getChalkCategories() {
        return new Category[] { KREY_ADDON, CHALK_ESP, CHALK_COMBAT, CHALK_UTILITY };
    }

    @Override
    public void onInitialize() {
        ChalkGlintModule.registerBuiltinPack();

        Modules.get().add(new AutoInvTotemModule());
        Modules.get().add(new PlayerChunkESP());
        Modules.get().add(new PlayerActivityModule());
        Modules.get().add(new AmethystChunkESP());
        Modules.get().add(new PistonEspModule());
        Modules.get().add(new LightEspModule());
        Modules.get().add(new BeehiveEspModule());
        Modules.get().add(new VillagerEspModule());
        Modules.get().add(new DrownedTridentEspModule());
        Modules.get().add(new ChunkFinderModule());
        Modules.get().add(new SusChunkFinderModule());
        Modules.get().add(new BlockNotifierModule());
        Modules.get().add(new ChalkAlertsModule());
        Modules.get().add(new HomeResetModule());
        Modules.get().add(new SpeedMineModule());
        Modules.get().add(new OnlineAdminsModule());
        Modules.get().add(new ServerRegionHudModule());
        Modules.get().add(new SessionTrackerModule("session-timer", "Tracks the time since this module was enabled.", SessionTrackerModule.Type.SessionTime));
        Modules.get().add(new SessionTrackerModule("distance-tracker", "Tracks your movement distance while enabled.", SessionTrackerModule.Type.Distance));
        Modules.get().add(new SessionTrackerModule("jump-counter", "Counts normal ground-to-air jump transitions while enabled.", SessionTrackerModule.Type.Jumps));
        Modules.get().add(new SessionTrackerModule("sneak-timer", "Tracks time spent sneaking while enabled.", SessionTrackerModule.Type.SneakTime));
        Modules.get().add(new SessionTrackerModule("ride-timer", "Tracks time spent mounted while enabled.", SessionTrackerModule.Type.RideTime));
        Modules.get().add(new SweetBerryEspModule());
        Modules.get().add(new VineEspModule());
        Modules.get().add(new PillagerEspModule());
        Modules.get().add(new WanderingTraderEspModule());
        Modules.get().add(new KreysCrystalsModule());
        Modules.get().add(new BoatFlyModule());
        Modules.get().add(new ChalkGlintModule());
        Modules.get().add(new ChalkHudModule());
        if (BuyerLicenseManager.isBuyerEdition()) {
            BuyerLicenseManager.initialize();
            Modules.get().add(new ChalkLicenseModule());
            MeteorClient.EVENT_BUS.subscribe(new BetaTrialEnforcer());
        }
        if (BetaTrialManager.isBetaEdition()) {
            BetaTrialManager.initialize();
            Modules.get().add(new ChalkBetaLicenseModule());

            BetaTrialEnforcer betaTrialEnforcer = new BetaTrialEnforcer();
            MeteorClient.EVENT_BUS.subscribe(betaTrialEnforcer);
            betaTrialEnforcer.enforceNow();
        }
        Hud.get().register(ChalkOverlayHud.INFO);

        // Keep Chalk module state in meteor-client/chalk-client/settings.nbt.
        // Loading happens here, after all Chalk modules have been registered.
        chalkConfig = new ChalkClientConfig();
        Systems.add(chalkConfig);
        chalkConfig.load();
        if (!chalkConfig.getFile().exists()) chalkConfig.save();

        // Use Meteor's original Modules screen and its default Right Shift bind.
        KeyBinds.OPEN_GUI.setKey(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT));
    }

    @Override
    public String getPackage() {
        return "de.example.totemautoinv";
    }
}
