package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.block.SpiritPlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

final class NaturalRockPlantBlock extends SpiritPlantBlock {
    NaturalRockPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(ModBlocks.GENSOKYO_SAND.get())
                || state.is(ModBlocks.HAKUGYOKU_STONE.get()) || state.is(ModBlocks.CHIREIDEN_DEEPSLATE.get());
    }
}
