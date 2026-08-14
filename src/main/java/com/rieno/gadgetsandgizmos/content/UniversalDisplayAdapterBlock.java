package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the universal display adapter block
    public UniversalDisplayAdapterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
