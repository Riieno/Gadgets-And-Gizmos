package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

// Orient the kinetic machine which pulls fuel through its front connection
public class FuelOxidizerBlock extends DirectionalKineticBlock implements IBE<FuelOxidizerBlockEntity>, ICogWheel {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the fuel oxidizer block
    public FuelOxidizerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction preferred = getPreferredFacing(ctx);
        if (preferred == null) {
            preferred = ctx.getNearestLookingDirection();
        }
        if (ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown()) {
            preferred = preferred.getOpposite();
        }
        return defaultBlockState().setValue(FACING, preferred);
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    // Check if this is a small cog
    @Override
    public boolean isSmallCog() {
        return true;
    }

    // Check if this is a large cog
    @Override
    public boolean isLargeCog() {
        return false;
    }

    // Get the block entity class
    @Override
    public Class<FuelOxidizerBlockEntity> getBlockEntityClass() {
        return FuelOxidizerBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends FuelOxidizerBlockEntity> getBlockEntityType() {
        return CTBlockEntities.FUEL_OXIDIZER.get();
    }
}
