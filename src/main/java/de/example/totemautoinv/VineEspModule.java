package de.example.totemautoinv;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Highlights the lowest vine in a vine column when it touches a solid block. */
public final class VineEspModule extends ChalkBlockEspModule {
    public VineEspModule() {
        super(
            "vine-esp",
            "Highlights loaded vine columns where the lowest vine touches a non-air block.",
            8,
            new SettingColor(67, 207, 105, 255),
            new SettingColor(67, 207, 105, 28)
        );
    }

    @Override
    protected boolean matches(BlockPos pos, BlockState state) {
        if (!state.is(Blocks.VINE)) return false;

        BlockState below = mc.level.getBlockState(pos.below());
        return !below.is(Blocks.VINE) && !below.isAir();
    }
}
