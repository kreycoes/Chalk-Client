package de.example.totemautoinv;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Highlights sweet berry bushes, with mature bushes selected by default. */
public final class SweetBerryEspModule extends ChalkBlockEspModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyMature = sgGeneral.add(new BoolSetting.Builder()
        .name("only-mature")
        .description("Only highlight bushes at their maximum harvestable growth stage.")
        .defaultValue(true)
        .build()
    );

    public SweetBerryEspModule() {
        super(
            "sweet-berry-esp",
            "Highlights loaded sweet berry bushes, optionally only when they are harvestable.",
            8,
            new SettingColor(245, 63, 102, 255),
            new SettingColor(245, 63, 102, 30)
        );
    }

    @Override
    protected boolean matches(BlockPos pos, BlockState state) {
        return state.is(Blocks.SWEET_BERRY_BUSH)
            && (!onlyMature.get() || state.getValue(SweetBerryBushBlock.AGE) == SweetBerryBushBlock.MAX_AGE);
    }
}
