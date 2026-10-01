package de.example.totemautoinv;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Highlights beehives and bee nests, with an optional full-hive filter. */
public final class BeehiveEspModule extends ChalkBlockEspModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyFull = sgGeneral.add(new BoolSetting.Builder()
        .name("only-full")
        .description("Only highlight beehives and bee nests that currently contain the maximum number of bees.")
        .defaultValue(true)
        .build()
    );

    public BeehiveEspModule() {
        super(
            "beehive-esp",
            "Highlights loaded beehives and bee nests, optionally only when they are full.",
            8,
            new SettingColor(255, 197, 57, 255),
            new SettingColor(255, 197, 57, 32)
        );
    }

    @Override
    protected boolean matches(BlockPos pos, BlockState state) {
        if (!state.is(Blocks.BEEHIVE) && !state.is(Blocks.BEE_NEST)) return false;
        if (!onlyFull.get()) return true;

        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        return blockEntity instanceof BeehiveBlockEntity beehive && beehive.isFull();
    }
}
