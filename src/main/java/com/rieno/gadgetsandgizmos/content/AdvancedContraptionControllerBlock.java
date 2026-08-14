package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Give the advanced controller its embedded shape, runtime block entity and server ticker
public class AdvancedContraptionControllerBlock extends AnalogueContraptionControllerBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller block
    public AdvancedContraptionControllerBlock(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the controller shape
    @Override
    protected VoxelShape getControllerShape(BlockState state) {
        Direction surfaceNormal = state.getValue(FACING);
        Direction horizontalFacing = state.getValue(HORIZONTAL_FACING);
        int mountOffset = state.getValue(EMBEDDED_SLAB) ? state.getValue(MOUNT_OFFSET) : 0;
        return AdvancedContraptionControllerShapes.shapeForMount(
                surfaceNormal, horizontalFacing, mountOffset);
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedContraptionControllerBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide && type == CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<AdvancedContraptionControllerBlockEntity>)
                    AdvancedContraptionControllerBlockEntity::tickServer;
        }
        return null;
    }
}
