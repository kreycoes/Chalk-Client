package de.example.totemautoinv;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;

/** Shared renderer for Chalk entity ESP modules. */
abstract class ChalkEntityEspModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum entity distance in blocks.")
        .defaultValue(96)
        .min(8)
        .max(256)
        .sliderRange(8, 128)
        .build()
    );

    private final Setting<Integer> maximumEntities = sgGeneral.add(new IntSetting.Builder()
        .name("maximum-entities")
        .description("Maximum matching entities rendered each frame.")
        .defaultValue(128)
        .min(1)
        .max(1024)
        .sliderRange(1, 256)
        .build()
    );

    private final Setting<Boolean> seeThroughBlocks = sgRender.add(new BoolSetting.Builder()
        .name("see-through-blocks")
        .description("Render matching entities through normal blocks when Meteor rendering allows it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor;
    private final Setting<SettingColor> sideColor;
    private int renderedEntities;

    protected ChalkEntityEspModule(String name, String description, SettingColor defaultLineColor, SettingColor defaultSideColor) {
        super(TotemAutoInvAddon.CHALK_ESP, name, description);

        lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Outline color of matching entities.")
            .defaultValue(defaultLineColor)
            .build()
        );

        sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Transparent fill color of matching entities.")
            .defaultValue(defaultSideColor)
            .build()
        );
    }

    protected abstract boolean matches(Entity entity);

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) {
            renderedEntities = 0;
            return;
        }

        Renderer3D renderer = seeThroughBlocks.get() ? event.renderer : event.depthRenderer;
        double maxDistanceSquared = (double) range.get() * range.get();
        int limit = maximumEntities.get();
        int count = 0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (count >= limit) break;
            if (!entity.isAlive() || entity.distanceToSqr(mc.player) > maxDistanceSquared || !matches(entity)) continue;

            renderer.box(entity.getBoundingBox(), sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
            count++;
        }

        renderedEntities = count;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(renderedEntities);
    }
}
