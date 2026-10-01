package de.example.totemautoinv;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;

/** Highlights wandering traders currently tracked by the client. */
public final class WanderingTraderEspModule extends ChalkEntityEspModule {
    public WanderingTraderEspModule() {
        super(
            "wandering-trader-esp",
            "Highlights wandering traders currently visible to your client.",
            new SettingColor(84, 153, 255, 255),
            new SettingColor(84, 153, 255, 30)
        );
    }

    @Override
    protected boolean matches(Entity entity) {
        return entity instanceof WanderingTrader;
    }
}
