package de.example.totemautoinv;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.illager.Pillager;

/** Highlights pillagers currently tracked by the client. */
public final class PillagerEspModule extends ChalkEntityEspModule {
    public PillagerEspModule() {
        super(
            "pillager-esp",
            "Highlights pillagers currently visible to your client.",
            new SettingColor(225, 93, 255, 255),
            new SettingColor(225, 93, 255, 30)
        );
    }

    @Override
    protected boolean matches(Entity entity) {
        return entity instanceof Pillager;
    }
}
