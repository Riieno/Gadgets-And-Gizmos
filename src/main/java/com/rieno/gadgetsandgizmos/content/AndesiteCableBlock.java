package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

// Join adjacent cable faces and tick their Forge Energy network
public class AndesiteCableBlock extends Block implements EntityBlock, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final VoxelShape CORE = box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape ARM_DOWN = box(6.0D, 0.0D, 6.0D, 10.0D, 5.0D, 10.0D);
    private static final VoxelShape ARM_UP = box(6.0D, 11.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape ARM_NORTH = box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 5.0D);
    private static final VoxelShape ARM_SOUTH = box(6.0D, 6.0D, 11.0D, 10.0D, 10.0D, 16.0D);
    private static final VoxelShape ARM_WEST = box(0.0D, 6.0D, 6.0D, 5.0D, 10.0D, 10.0D);
    private static final VoxelShape ARM_EAST = box(11.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the andesite cable block
    public AndesiteCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(PipeBlock.UP, false)
                .setValue(PipeBlock.DOWN, false)
                .setValue(PipeBlock.NORTH, false)
                .setValue(PipeBlock.SOUTH, false)
                .setValue(PipeBlock.WEST, false)
                .setValue(PipeBlock.EAST, false));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.UP, PipeBlock.DOWN, PipeBlock.NORTH, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.EAST);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return refreshConnections(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    // Update the shape
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(property(dir), canConnectTo(level, pos, dir));
    }

    // Handle the neighboring block change
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        BlockState refreshed = refreshConnections(state, level, pos);
        if (!refreshed.equals(state)) {

            level.setBlock(pos, refreshed, 3);
        }
    }

    // Refresh the connections
    private BlockState refreshConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState refreshed = state;
        for (Direction dir : Direction.values()) {
            refreshed = refreshed.setValue(property(dir), canConnectTo(level, pos, dir));
        }
        return refreshed;
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = CORE;
        if (state.getValue(PipeBlock.DOWN)) {
            shape = Shapes.or(shape, ARM_DOWN);
        }
        if (state.getValue(PipeBlock.UP)) {
            shape = Shapes.or(shape, ARM_UP);
        }
        if (state.getValue(PipeBlock.NORTH)) {
            shape = Shapes.or(shape, ARM_NORTH);
        }
        if (state.getValue(PipeBlock.SOUTH)) {
            shape = Shapes.or(shape, ARM_SOUTH);
        }
        if (state.getValue(PipeBlock.WEST)) {
            shape = Shapes.or(shape, ARM_WEST);
        }
        if (state.getValue(PipeBlock.EAST)) {
            shape = Shapes.or(shape, ARM_EAST);
        }
        return shape;
    }

    // Check if the shared cable face should be skipped
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        if (adjacentBlockState.getBlock() instanceof AndesiteCableBlock && state.getValue(property(side))) {
            return true;
        }
        return super.skipRendering(state, adjacentBlockState, side);
    }

    // Check if this is connected
    public static boolean isConnected(BlockState state, Direction dir) {
        if (!(state.getBlock() instanceof AndesiteCableBlock)) {
            return false;
        }
        return state.getValue(property(dir));
    }

    // Check if this can connect to the target
    private boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction dir) {
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.getBlock() instanceof AndesiteCableBlock) {
            return true;
        }
        if (level instanceof Level world) {
            return world.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, dir.getOpposite()) != null;
        }
        return false;
    }

    // Get the property
    private static BooleanProperty property(Direction dir) {
        return PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AndesiteCableBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        if (blockEntityType == CTBlockEntities.ANDESITE_CABLE.get()) {

            return (BlockEntityTicker<T>) (BlockEntityTicker<AndesiteCableBlockEntity>) AndesiteCableBlockEntity::tick;
        }
        return null;
    }

    // Get the block entity type
    public BlockEntityType<? extends AndesiteCableBlockEntity> getBlockEntityType() {
        return CTBlockEntities.ANDESITE_CABLE.get();
    }
}
