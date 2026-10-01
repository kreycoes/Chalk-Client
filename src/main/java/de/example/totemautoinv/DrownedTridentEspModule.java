package de.example.totemautoinv;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.Items;

/** Highlights client-tracked drowned mobs which are currently holding a trident. */
public final class DrownedTridentEspModule extends ChalkEntityEspModule {
    public DrownedTridentEspModule() {
        super(
            "drowned-trident-esp",
            "Highlights drowned currently holding a trident on your client.",
            new SettingColor(75, 215, 255, 255),
            new SettingColor(75, 215, 255, 30)
        );
    }

    @Override
    protected boolean matches(Entity entity) {
        if (!(entity instanceof Drowned drowned)) return false;

        return drowned.getMainHandItem().is(Items.TRIDENT) || drowned.getOffhandItem().is(Items.TRIDENT);
    }
}
