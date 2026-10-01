package de.example.totemautoinv;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;

/** Highlights villagers visible to the current client. */
public final class VillagerEspModule extends ChalkEntityEspModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> includeZombieVillagers = sgGeneral.add(new BoolSetting.Builder()
        .name("include-zombie-villagers")
        .description("Also highlight zombie villagers.")
        .defaultValue(true)
        .build()
    );

    public VillagerEspModule() {
        super(
            "villager-esp",
            "Highlights villagers visible to your client.",
            new SettingColor(85, 232, 130, 255),
            new SettingColor(85, 232, 130, 30)
        );
    }

    @Override
    protected boolean matches(Entity entity) {
        return entity instanceof Villager || includeZombieVillagers.get() && entity instanceof ZombieVillager;
    }
}
