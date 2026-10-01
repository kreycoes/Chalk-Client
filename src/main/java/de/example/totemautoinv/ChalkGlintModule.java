package de.example.totemautoinv;

import meteordevelopment.meteorclient.systems.modules.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

import java.lang.reflect.Method;

/**
 * Enables Chalk's built-in glint resource pack.
 *
 * <p>The previous implementation created replacement render layers while the
 * world renderer was running. That is fragile across Minecraft renderer
 * changes and can leave parts of the world black. A resource pack replaces the
 * two vanilla glint textures through Minecraft's supported resource system,
 * so every normal render path (hand, entities, armor, dropped items and GUI)
 * continues to use the vanilla renderer safely.</p>
 */
public final class ChalkGlintModule extends Module {
    private static final Identifier PACK_IDENTIFIER = Identifier.fromNamespaceAndPath("totemautoinv", "chalk_glint");
    private static final String PACK_ID = PACK_IDENTIFIER.toString();
    private static boolean builtInPackRegistered;

    public ChalkGlintModule() {
        super(TotemAutoInvAddon.KREY_ADDON,
            "chalk-glint",
            "Enables Chalk's galaxy enchantment glint resource pack."
        );

        // A resource pack is also valid in the title screen. Keeping the
        // module active there prevents a disconnect from removing the pack and
        // immediately triggering another full resource reload.
        runInMainMenu = true;
    }

    /** Registers the bundled pack once while the addon is initialized. */
    public static void registerBuiltinPack() {
        if (builtInPackRegistered) return;

        FabricLoader.getInstance().getModContainer("totemautoinv").ifPresent(ChalkGlintModule::registerPackForContainer);
    }

    /**
     * Fabric API is supplied by the user's Modrinth profile. Reflection keeps
     * this optional API out of this addon's compile-time dependency graph while
     * still using Fabric's supported built-in-pack registry at runtime.
     */
    private static void registerPackForContainer(ModContainer container) {
        try {
            Class<?> activationType = Class.forName("net.fabricmc.fabric.api.resource.v1.pack.PackActivationType");
            Object normalActivation = activationType.getField("NORMAL").get(null);
            Class<?> resourceLoader = Class.forName("net.fabricmc.fabric.api.resource.v1.ResourceLoader");
            Method registerBuiltinPack = resourceLoader.getMethod(
                "registerBuiltinPack",
                Identifier.class,
                ModContainer.class,
                activationType
            );

            builtInPackRegistered = (boolean) registerBuiltinPack.invoke(
                null,
                PACK_IDENTIFIER,
                container,
                normalActivation
            );
        } catch (ReflectiveOperationException ignored) {
            // Without Fabric Resource Loader, leave Chalk Glint inactive rather
            // than risking a partial renderer replacement.
            builtInPackRegistered = false;
        }
    }

    @Override
    public void onActivate() {
        setPackEnabled(true);
    }

    @Override
    public void onDeactivate() {
        setPackEnabled(false);
    }

    private void setPackEnabled(boolean enabled) {
        if (!builtInPackRegistered) return;

        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repository = minecraft.getResourcePackRepository();

        // Refresh the repository first so the built-in pack is available even
        // when this module is first enabled after Minecraft has already loaded.
        repository.reload();
        boolean changed = enabled ? repository.addPack(PACK_ID) : repository.removePack(PACK_ID);

        // updateResourcePacks saves the selected pack list and reloads resources
        // only if the selection has really changed.
        if (changed) minecraft.options.updateResourcePacks(repository);
    }
}
