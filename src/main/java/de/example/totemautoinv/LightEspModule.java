package de.example.totemautoinv;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Highlights common vanilla blocks which act as bright, permanent light sources. */
public final class LightEspModule extends ChalkBlockEspModule {
    public LightEspModule() {
        super(
            "light-esp",
            "Highlights common client-loaded vanilla light-source blocks.",
            6,
            new SettingColor(255, 228, 90, 255),
            new SettingColor(255, 228, 90, 28)
        );
    }

    @Override
    protected boolean matches(BlockPos pos, BlockState state) {
        return state.is(Blocks.TORCH)
            || state.is(Blocks.WALL_TORCH)
            || state.is(Blocks.SOUL_TORCH)
            || state.is(Blocks.SOUL_WALL_TORCH)
            || state.is(Blocks.REDSTONE_TORCH)
            || state.is(Blocks.REDSTONE_WALL_TORCH)
            || state.is(Blocks.LANTERN)
            || state.is(Blocks.SOUL_LANTERN)
            || state.is(Blocks.GLOWSTONE)
            || state.is(Blocks.SEA_LANTERN)
            || state.is(Blocks.SHROOMLIGHT)
            || state.is(Blocks.OCHRE_FROGLIGHT)
            || state.is(Blocks.VERDANT_FROGLIGHT)
            || state.is(Blocks.PEARLESCENT_FROGLIGHT)
            || state.is(Blocks.JACK_O_LANTERN)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE)
            || state.is(Blocks.END_ROD)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.REDSTONE_LAMP);
    }
}
