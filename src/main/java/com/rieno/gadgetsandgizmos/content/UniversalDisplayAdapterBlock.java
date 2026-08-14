package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.lib.display.AccDisplayConnectionRegistry;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// Orient and tick the adapter which feeds external data into Create displays
public final class UniversalDisplayAdapterBlock extends DirectionalBlock
        implements IBE<UniversalDisplayAdapterBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final MapCodec<UniversalDisplayAdapterBlock> CODEC =
            simpleCodec(UniversalDisplayAdapterBlock::new);
    // Connection state properties
    private static final Map<Direction, BooleanProperty> CONNECTION_PROPERTIES = Map.of(
            Direction.DOWN, BlockStateProperties.DOWN,
            Direction.UP, BlockStateProperties.UP,
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.EAST, BlockStateProperties.EAST);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the universal display adapter block
    public UniversalDisplayAdapterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(connectionState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the codec
    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    // Get the state for placement
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connectionState(defaultBlockState().setValue(FACING, ctx.getClickedFace()),
                ctx.getLevel(), ctx.getClickedPos());
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.DOWN, BlockStateProperties.UP,
                BlockStateProperties.NORTH, BlockStateProperties.SOUTH,
                BlockStateProperties.WEST, BlockStateProperties.EAST);
    }

    // Update the changed connection
    @Override
    public BlockState updateShape(BlockState state, Direction direction,
                                  BlockState neighbourState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(CONNECTION_PROPERTIES.get(direction),
                connectsTo(level.getBlockEntity(neighbourPos), direction, state));
    }

    // Set all adjacent connections
    private BlockState connectionState(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState connected = state;
        for (Direction direction : Direction.values()) {
            connected = connected.setValue(CONNECTION_PROPERTIES.get(direction),
                    connectsTo(level.getBlockEntity(pos.relative(direction)), direction, state));
        }
        return connected;
    }

    // Create the default disconnected state
    private BlockState connectionState(BlockState state) {
        BlockState disconnected = state;
        for (BooleanProperty property : CONNECTION_PROPERTIES.values()) {
            disconnected = disconnected.setValue(property, false);
        }
        return disconnected;
    }

    // Check if the adjacent block supports an adapter connection
    private boolean connectsTo(@Nullable BlockEntity neighbour, Direction direction,
                               BlockState state) {
        return AccDisplayConnectionRegistry.isConnection(neighbour);
    }

    // Get the block entity class
    @Override
    public Class<UniversalDisplayAdapterBlockEntity> getBlockEntityClass() {
        return UniversalDisplayAdapterBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends UniversalDisplayAdapterBlockEntity> getBlockEntityType() {
        return CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER.get();
    }

    // Create the block entity
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UniversalDisplayAdapterBlockEntity(pos, state);
    }

    // Get the ticker
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide && type == CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<UniversalDisplayAdapterBlockEntity>)
                    UniversalDisplayAdapterBlockEntity::tickServer;
        }
        return null;
    }
}
