package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Handle Gyro Redstone Bridge
public class GyroRedstoneBridgeBlock extends CTDirectionalBlock implements IBE<GyroRedstoneBridgeBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final VoxelShape SHAPE = box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyro redstone bridge block
    public GyroRedstoneBridgeBlock(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // Check if this is a signal source
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    // Get the signal
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GyroRedstoneBridgeBlockEntity bridge) {
            return bridge.getSignal(dir.getOpposite());
        }
        return 0;
    }

    // Get the direct signal
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }

    // Check if this can connect redstone
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null;
    }

    // Get the analog output signal
    @Override
    protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GyroRedstoneBridgeBlockEntity bridge) {
            return bridge.getSignal(Direction.UP);
        }
        return 0;
    }

    // Check if this has analog output signal
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    // Get the block entity class
    @Override
    public Class<GyroRedstoneBridgeBlockEntity> getBlockEntityClass() {
        return GyroRedstoneBridgeBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends GyroRedstoneBridgeBlockEntity> getBlockEntityType() {
        return CTBlockEntities.GYRO_REDSTONE_BRIDGE.get();
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GyroRedstoneBridgeBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (!level.isClientSide && blockEntityType == CTBlockEntities.GYRO_REDSTONE_BRIDGE.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<GyroRedstoneBridgeBlockEntity>) GyroRedstoneBridgeBlockEntity::tickServer;
        }
        return null;
    }
}
