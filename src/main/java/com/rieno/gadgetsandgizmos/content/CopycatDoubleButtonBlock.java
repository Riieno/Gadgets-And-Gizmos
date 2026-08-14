package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Combine two timed redstone buttons with a shared copycat material
public class CopycatDoubleButtonBlock extends CopycatBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the copycat double button block
    public CopycatDoubleButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(DoubleButtonBlock.TOP_POWERED, false)
                .setValue(DoubleButtonBlock.BOTTOM_POWERED, false)
                .setValue(DoubleButtonBlock.SHOW_LINKS, true));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING, DoubleButtonBlock.TOP_POWERED, DoubleButtonBlock.BOTTOM_POWERED,
                DoubleButtonBlock.SHOW_LINKS);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(BlockStateProperties.FACING, ctx.getClickedFace());
    }

    // Rotate the copycat double button block
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.FACING,
                rotation.rotate(state.getValue(BlockStateProperties.FACING)));
    }

    // Mirror the copycat double button block
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.FACING)));
    }

    // Check if this can survive
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        return !level.getBlockState(pos.relative(facing.getOpposite())).canBeReplaced();
    }

    // Handle the neighboring block change
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        if (!level.isClientSide
                && fromPos.equals(pos.relative(state.getValue(BlockStateProperties.FACING).getOpposite()))
                && !canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return DoubleButtonBlock.shapeFor(state);
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        return DoubleButtonBlock.shapeFor(state);
    }

    // Get the interaction shape
    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return DoubleButtonBlock.shapeFor(state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle copycat double button block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DoubleButtonBlockEntity doubleButton)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                DoubleButtonBlock.openModeScreen(doubleButton);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        DoubleButtonBlock.Target target = DoubleButtonBlock.targetAt(state, pos, hit.getLocation());
        if (!(target instanceof DoubleButtonBlock.Target.Button buttonTarget)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            DoubleButtonBlock.startHolding(doubleButton, buttonTarget.button());
            return InteractionResult.SUCCESS;
        }

        doubleButton.activate(buttonTarget.button());
        return InteractionResult.CONSUME;
    }

    // Handle copycat double button block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        DoubleButtonBlock.Target target = DoubleButtonBlock.targetAt(state, pos, hit.getLocation());
        if (target instanceof DoubleButtonBlock.Target.Frequency frequencyTarget) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof DoubleButtonBlockEntity doubleButton)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                doubleButton.setFrequency(frequencyTarget.button(), frequencyTarget.firstFrequency(), stack);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    // Update the copycat double button block
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DoubleButtonBlockEntity doubleButton) {
            doubleButton.scheduledReleaseTick();
        }
    }

    // Check if this is a signal source
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    // Get the signal
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(DoubleButtonBlock.TOP_POWERED)
                || state.getValue(DoubleButtonBlock.BOTTOM_POWERED) ? 15 : 0;
    }

    // Get the direct signal
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return dir == state.getValue(BlockStateProperties.FACING) ? getSignal(state, level, pos, dir) : 0;
    }

    // Check if this can connect redstone
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null;
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DoubleButtonBlockEntity doubleButton) {
                doubleButton.onDestroyed();
            }
            DoubleButtonBlock.notifyNeighbors(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Get the block entity class
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<CopycatBlockEntity> getBlockEntityClass() {
        return (Class) DoubleButtonBlockEntity.class;
    }

    // Get the block entity type
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
        return (BlockEntityType) CTBlockEntities.DOUBLE_BUTTON.get();
    }

    // Get the ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (blockEntityType == CTBlockEntities.DOUBLE_BUTTON.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<DoubleButtonBlockEntity>) DoubleButtonBlockEntity::tick;
        }
        return null;
    }

    // Check if textures can connect toward the target
    @Override
    public boolean canConnectTexturesToward(BlockAndTintGetter level, BlockPos fromPos, BlockPos toPos,
                                            BlockState state) {
        return !toPos.equals(fromPos.relative(state.getValue(BlockStateProperties.FACING)));
    }
}
