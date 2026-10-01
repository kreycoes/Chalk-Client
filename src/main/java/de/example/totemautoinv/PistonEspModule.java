package de.example.totemautoinv;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Highlights client-loaded pistons and sticky pistons. */
public final class PistonEspModule extends ChalkBlockEspModule {
    public PistonEspModule() {
        super(
            "piston-esp",
            "Highlights pistons and sticky pistons in chunks already loaded by your client.",
            8,
            new SettingColor(255, 168, 54, 255),
            new SettingColor(255, 168, 54, 35)
        );
    }

    @Override
    protected boolean matches(BlockPos pos, BlockState state) {
        return state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON);
    }
}
