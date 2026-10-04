package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public class CreativeRcsThrusterBlock extends RcsThrusterBlock {
    public CreativeRcsThrusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public boolean isSelfPowered() {
        return true;
    }
}
