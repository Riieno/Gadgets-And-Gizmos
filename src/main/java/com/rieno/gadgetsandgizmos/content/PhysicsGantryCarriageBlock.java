package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// Mount and orient a physical gantry carriage against a valid shaft
public class PhysicsGantryCarriageBlock extends DirectionalAxisKineticBlock implements IBE<PhysicsGantryCarriageBlockEntity>, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry carriage block
    public PhysicsGantryCarriageBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this can survive
    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction dir = state.getValue(FACING);
        BlockState shaft = world.getBlockState(pos.relative(dir.getOpposite()));
        if (shaft.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                && shaft.getValue(PhysicsGantryShaftBlock.FACING).getAxis() != dir.getAxis()) {
            return true;
        }

        if (world instanceof Level level) {
            if (level.getBlockEntity(pos) instanceof PhysicsGantryCarriageBlockEntity carriageBE
                    && carriageBE.isSubLevelAssembled()) {
                return true;
            }

            PhysicsGantryShaftBlockEntity resolvedShaft = SimulatedHelper.findBlockEntityIncludingSubLevels(
                    level,
                    pos.relative(dir.getOpposite()),
                    PhysicsGantryShaftBlockEntity.class);
            return resolvedShaft != null
                    && resolvedShaft.getBlockState().getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                    && resolvedShaft.getBlockState().getValue(PhysicsGantryShaftBlock.FACING).getAxis()
                    != dir.getAxis();
        }

        return false;
    }

    // Update the indirect neighbour shapes
    @Override
    public void updateIndirectNeighbourShapes(BlockState state, LevelAccessor world, BlockPos pos, int flags, int count) {
        super.updateIndirectNeighbourShapes(state, world, pos, flags, count);
        withBlockEntityDo((BlockGetter) world, pos, PhysicsGantryCarriageBlockEntity::checkValidGantryShaft);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the place event
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || oldState.getBlock() == state.getBlock()) {
            return;
        }
        if (isShaftFaceOccupied(level, pos, state, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        withBlockEntityDo(level, pos, PhysicsGantryCarriageBlockEntity::queueAssembly);
    }

    // Release a mounted payload before the carriage block entity is discarded
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof PhysicsGantryCarriageBlockEntity carriage) {
            carriage.onCarriageRemoved();
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // Get the facing for placement
    @Override
    protected Direction getFacingForPlacement(BlockPlaceContext ctx) {
        return ctx.getClickedFace();
    }

    // Handle physics gantry carriage block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, PhysicsGantryCarriageBlockEntity::forceToggleAssembly);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Handle physics gantry carriage block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState stateForPlacement = super.getStateForPlacement(ctx);
        if (stateForPlacement == null) {
            return null;
        }
        Direction opposite = stateForPlacement.getValue(FACING).getOpposite();
        BlockState placedState = cycleAxisIfNecessary(stateForPlacement, opposite,
                ctx.getLevel().getBlockState(ctx.getClickedPos().relative(opposite)));
        if (isShaftFaceOccupied(ctx.getLevel(), ctx.getClickedPos(), placedState, null)) {
            showPlacementMessage(ctx.getLevel(), ctx.getPlayer(),
                    "createthrusters.physics_gantry.error.face_occupied");
            return null;
        }
        return placedState;
    }

    // Show the placement message
    private static void showPlacementMessage(Level level, Player player, String translationKey) {
        if (!level.isClientSide && player != null) {
            player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    // Check if the shaft face is occupied
    private boolean isShaftFaceOccupied(Level level, BlockPos carriagePos, BlockState carriageState,
                                        BlockPos ignoredPlacedCarriagePos) {
        Direction carriageFacing = carriageState.getValue(FACING);
        BlockPos shaftPos = carriagePos.relative(carriageFacing.getOpposite());
        BlockEntity shaftBlockEntity = level.getBlockEntity(shaftPos);
        if (!(shaftBlockEntity instanceof PhysicsGantryShaftBlockEntity shaft)) {
            return false;
        }

        BlockState shaftState = shaft.getBlockState();
        if (shaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return false;
        }

        return PhysicsGantryCarriageBlockEntity.isShaftFaceOccupiedForPlacement(
                level,
                shaftPos,
                shaftState.getValue(PhysicsGantryShaftBlock.FACING),
                carriageFacing,
                SimulatedHelper.getContainingSubLevelId(shaft),
                ignoredPlacedCarriagePos);
    }

    // Handle the neighboring block change
    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos updatePos, boolean isMoving) {
        if (updatePos.equals(pos.relative(state.getValue(FACING).getOpposite())) && !canSurvive(state, world, pos)) {
            world.destroyBlock(pos, true);
        }
    }

    // Update the shape
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState otherState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(FACING) != dir.getOpposite()) {
            return state;
        }
        return cycleAxisIfNecessary(state, dir, otherState);
    }

    // Cycle the axis if necessary
    protected BlockState cycleAxisIfNecessary(BlockState state, Direction dir, BlockState otherState) {
        if (otherState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return state;
        }
        if (otherState.getValue(PhysicsGantryShaftBlock.FACING).getAxis() == dir.getAxis()) {
            return state;
        }
        if (isValidGantryShaftAxis(state, otherState)) {
            return state;
        }
        return state.cycle(AXIS_ALONG_FIRST_COORDINATE);
    }

    // Check if the gantry shaft axis is valid
    public static boolean isValidGantryShaftAxis(BlockState pinionState, BlockState gantryState) {
        return getValidGantryShaftAxis(pinionState) == gantryState.getValue(PhysicsGantryShaftBlock.FACING).getAxis();
    }

    // Get the valid gantry shaft axis
    public static Direction.Axis getValidGantryShaftAxis(BlockState state) {
        if (!(state.getBlock() instanceof PhysicsGantryCarriageBlock block)) {
            return Direction.Axis.Y;
        }
        Direction.Axis rotationAxis = block.getRotationAxis(state);
        Direction.Axis facingAxis = state.getValue(FACING).getAxis();
        for (Direction.Axis axis : Iterate.axes) {
            if (axis == rotationAxis || axis == facingAxis) {
                continue;
            }
            return axis;
        }
        return Direction.Axis.Y;
    }

    // Get the valid gantry pinion axis
    public static Direction.Axis getValidGantryPinionAxis(BlockState state, Direction.Axis shaftAxis) {
        Direction.Axis facingAxis = state.getValue(FACING).getAxis();
        for (Direction.Axis axis : Iterate.axes) {
            if (axis == shaftAxis || axis == facingAxis) {
                continue;
            }
            return axis;
        }
        return Direction.Axis.Y;
    }

    // Get the block entity class
    @Override
    public Class<PhysicsGantryCarriageBlockEntity> getBlockEntityClass() {
        return PhysicsGantryCarriageBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends PhysicsGantryCarriageBlockEntity> getBlockEntityType() {
        return CTBlockEntities.PHYSICS_GANTRY_CARRIAGE.get();
    }
}
