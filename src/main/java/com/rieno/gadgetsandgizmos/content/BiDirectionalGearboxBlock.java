package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticHostBlock;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

// Handle Bidirectional Gearbox
public class BiDirectionalGearboxBlock extends RotatedPillarKineticBlock
        implements IBE<BiDirectionalGearboxBlockEntity>, ExtraKinetics.ExtraKineticsBlock, IWrenchable,
        VirtualKineticHostBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty GYRO_MODE = BooleanProperty.create("gyro_mode");
    public static final BooleanProperty PASSTHROUGH_SPLIT = BooleanProperty.create("passthrough_split");
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearbox block
    public BiDirectionalGearboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(GYRO_MODE, false)
                .setValue(PASSTHROUGH_SPLIT, false)
                .setValue(FACING, Direction.NORTH));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GYRO_MODE, PASSTHROUGH_SPLIT, FACING);
    }

    // Get the primary lane axis
    private static Direction.Axis primaryLaneAxis(BlockState state) {
        return state.getValue(AXIS) == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    // Get the secondary lane axis
    private static Direction.Axis secondaryLaneAxis(BlockState state) {
        return switch (state.getValue(AXIS)) {
            case Y -> Direction.Axis.X;
            case X -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        if (state.getValue(GYRO_MODE)) {
            return false;
        }
        if (state.hasProperty(PASSTHROUGH_SPLIT) && state.getValue(PASSTHROUGH_SPLIT)) {
            Direction.Axis axis = face.getAxis();
            return axis == primaryLaneAxis(state) || axis == secondaryLaneAxis(state);
        }
        return face.getAxis() == primaryLaneAxis(state);
    }

    // Get the minimum required speed level
    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.NONE;
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return primaryLaneAxis(state);
    }

    // Check if this can expose virtual kinetics
    @Override
    public boolean ct$canExposeVirtualKinetics(LevelReader level, BlockPos pos, BlockState state) {
        return state.hasProperty(GYRO_MODE) && state.getValue(GYRO_MODE);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(GYRO_MODE, false)
                .setValue(PASSTHROUGH_SPLIT, false)
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    // Get the rotated block state
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction facing = originalState.getValue(FACING);

        if (originalState.getValue(AXIS) == Direction.Axis.Y) {
            Direction next = facing.getAxis().isHorizontal()
                    ? facing.getClockWise(Direction.Axis.Y)
                    : Direction.NORTH;
            return originalState.setValue(FACING, next);
        }

        Direction next = facing == Direction.UP ? Direction.DOWN : Direction.UP;
        return originalState.setValue(FACING, next);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle bi directional gearbox block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            return InteractionResult.PASS;
        }

        Direction clickedFace = hit.getDirection();
        if (clickedFace.getAxis().isHorizontal()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BiDirectionalGearboxBlockEntity gearbox) {
                gearbox.toggleFaceOutputInverted(clickedFace);
                player.sendSystemMessage(Component.literal(clickedFace.name() + " output direction: "
                        + (gearbox.isFaceOutputInverted(clickedFace) ? "INVERTED" : "FORWARD")));
            }
            return InteractionResult.SUCCESS;
        }

        if (!player.isCrouching()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BiDirectionalGearboxBlockEntity gearbox) {
            if (gearbox.isGyroMode()) {
                player.sendSystemMessage(Component.literal("Gearbox Servo Mode | N/S/E/W: "
                        + gearbox.getOutputSignal(Direction.NORTH) + "/"
                        + gearbox.getOutputSignal(Direction.SOUTH) + "/"
                        + gearbox.getOutputSignal(Direction.EAST) + "/"
                        + gearbox.getOutputSignal(Direction.WEST)
                        + " | Reverse: " + (gearbox.isReverseMode() ? "ON" : "OFF")));
            } else {
                if (gearbox.isPassthroughSplitMode()) {
                    player.sendSystemMessage(Component.literal("Gearbox Kinetic Mode | Passthrough Split"));
                } else {
                    player.sendSystemMessage(Component.literal("Gearbox Kinetic Mode | Virtual lanes: N/S="
                            + gearbox.getLaneMode(Direction.Axis.Z)
                            + ", E/W=" + gearbox.getLaneMode(Direction.Axis.X)
                            + " | Place Advanced Data Link on top for servo outputs"));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Check if this has analog output signal
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return state.getValue(GYRO_MODE);
    }

    // Get the analog output signal
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BiDirectionalGearboxBlockEntity gearbox) {
            int max = 0;
            max = Math.max(max, gearbox.getOutputSignal(Direction.NORTH));
            max = Math.max(max, gearbox.getOutputSignal(Direction.SOUTH));
            max = Math.max(max, gearbox.getOutputSignal(Direction.EAST));
            max = Math.max(max, gearbox.getOutputSignal(Direction.WEST));
            return max;
        }
        return 0;
    }

    // Check if this is a signal source
    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(GYRO_MODE);
    }

    // Get the signal
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (!state.getValue(GYRO_MODE)) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BiDirectionalGearboxBlockEntity gearbox) {
            return gearbox.getOutputSignal(dir);
        }
        return 0;
    }

    // Get the block entity class
    @Override
    public @NotNull Class<BiDirectionalGearboxBlockEntity> getBlockEntityClass() {
        return BiDirectionalGearboxBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends BiDirectionalGearboxBlockEntity> getBlockEntityType() {
        return CTBlockEntities.BIDIRECTIONAL_GEARBOX.get();
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean removed = state.getBlock() != newState.getBlock();
        super.onRemove(state, level, pos, newState, isMoving);
        if (removed) {
            level.updateNeighborsAt(pos, state.getBlock());
            refreshAdjacentLaneKinetics(level, pos);
        }
    }

    // Refresh the adjacent lane kinetics
    private void refreshAdjacentLaneKinetics(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(dir));
            if (blockEntity instanceof BiDirectionalGearboxBlockEntity gearbox) {
                gearbox.queueLaneKineticsRefresh();
            }
        }
    }

    // Get the extra kinetics rotation configuration
    @Override
    public IRotate getExtraKineticsRotationConfiguration() {
        return BiDirectionalGearboxBlockEntity.EAST_WEST_ROTATION_CONFIGURATION;
    }
}
