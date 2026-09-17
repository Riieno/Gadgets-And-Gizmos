package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

// Handle Bidirectional Gearshift
public class BiDirectionalGearshiftBlock extends BiDirectionalGearboxBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final IRotate ORANGE_LANE_ROTATION_CONFIGURATION = new IRotate() {
        // Check if this has a shaft on the side
        @Override
        public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
            return state.getBlock() instanceof BiDirectionalGearshiftBlock
                    && !state.getValue(GYRO_MODE)
                    && !(state.hasProperty(PASSTHROUGH_SPLIT) && state.getValue(PASSTHROUGH_SPLIT))
                    && face.getAxis() == getOrangeLaneAxis(state);
        }

        // Get the rotation axis
        @Override
        public Direction.Axis getRotationAxis(BlockState state) {
            return getOrangeLaneAxis(state);
        }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearshift block
    public BiDirectionalGearshiftBlock(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the cyan lane axis
    public static Direction.Axis getCyanLaneAxis(BlockState state) {
        return getCyanLaneAxis(getPlacementAxis(state));
    }

    // Get the orange lane axis
    public static Direction.Axis getOrangeLaneAxis(BlockState state) {
        return getOrangeLaneAxis(getPlacementAxis(state), getPlacementFacing(state));
    }

    // Get the placement axis
    private static Direction.Axis getPlacementAxis(BlockState state) {
        if (state == null || !state.hasProperty(AXIS)) {
            return Direction.Axis.Y;
        }
        return state.getValue(AXIS);
    }

    // Get the cyan lane axis
    private static Direction.Axis getCyanLaneAxis(Direction.Axis placementAxis) {
        return placementAxis;
    }

    // Get the placement facing
    private static Direction getPlacementFacing(BlockState state) {
        if (state == null || !state.hasProperty(FACING)) {
            return Direction.NORTH;
        }
        Direction facing = state.getValue(FACING);
        return facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
    }

    // Get the orange lane axis
    private static Direction.Axis getOrangeLaneAxis(Direction.Axis placementAxis, Direction facing) {
        return switch (placementAxis) {
            case Y -> facing.getAxis();
            case X -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction.Axis axis = getAxisForPlacement(ctx);
        Direction facing = axis == Direction.Axis.Y
                ? getVerticalFacingForPlacement(ctx)
                : Direction.UP;
        return defaultBlockState()
                .setValue(AXIS, axis)
                .setValue(GYRO_MODE, false)
                .setValue(FACING, facing);
    }

    // Get the axis for placement
    private Direction.Axis getAxisForPlacement(BlockPlaceContext ctx) {
        Direction.Axis fallbackAxis = ctx.getClickedFace().getAxis();
        Direction.Axis bestAxis = fallbackAxis;
        int bestConnections = countConnectedShaftFaces(ctx, fallbackAxis);

        for (Direction.Axis axis : Direction.Axis.values()) {
            int connections = countConnectedShaftFaces(ctx, axis);
            if (connections > bestConnections) {
                bestAxis = axis;
                bestConnections = connections;
            }
        }

        return bestAxis;
    }

    // Get the vertical facing for placement
    private Direction getVerticalFacingForPlacement(BlockPlaceContext ctx) {
        Direction playerFacing = ctx.getHorizontalDirection().getOpposite();
        int xConnections = countConnectedShaftsOnAxis(ctx, Direction.Axis.X);
        int zConnections = countConnectedShaftsOnAxis(ctx, Direction.Axis.Z);
        if (xConnections > zConnections && playerFacing.getAxis() != Direction.Axis.X) {
            return Direction.EAST;
        }
        if (zConnections > xConnections && playerFacing.getAxis() != Direction.Axis.Z) {
            return Direction.NORTH;
        }
        return playerFacing;
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        if (state.getValue(GYRO_MODE)) {
            return false;
        }
        Direction.Axis axis = face.getAxis();
        if (state.hasProperty(PASSTHROUGH_SPLIT) && state.getValue(PASSTHROUGH_SPLIT)) {
            return axis == getCyanLaneAxis(state) || axis == getOrangeLaneAxis(state);
        }
        return axis == getCyanLaneAxis(state);
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return getCyanLaneAxis(state);
    }

    // Get the extra kinetics rotation configuration
    @Override
    public IRotate getExtraKineticsRotationConfiguration() {
        return ORANGE_LANE_ROTATION_CONFIGURATION;
    }

    // Check if the states are kinetically equivalent
    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        if (oldState.getBlock() != newState.getBlock()) {
            return false;
        }
        if (oldState.getValue(GYRO_MODE) != newState.getValue(GYRO_MODE)
                || oldState.getValue(PASSTHROUGH_SPLIT) != newState.getValue(PASSTHROUGH_SPLIT)) {
            return false;
        }
        return getCyanLaneAxis(oldState) == getCyanLaneAxis(newState)
                && getOrangeLaneAxis(oldState) == getOrangeLaneAxis(newState);
    }

    // Count the connected shaft faces
    private int countConnectedShaftFaces(BlockPlaceContext ctx, Direction.Axis placementAxis) {
        int connections = 0;
        Direction.Axis cyanAxis = getCyanLaneAxis(placementAxis);
        Direction.Axis orangeAxis = placementAxis == Direction.Axis.Y
                ? getBestVerticalOrangeAxis(ctx)
                : getOrangeLaneAxis(placementAxis, Direction.NORTH);
        for (Direction side : Direction.values()) {
            Direction.Axis sideAxis = side.getAxis();
            if (sideAxis != cyanAxis && sideAxis != orangeAxis) {
                continue;
            }
            if (connectsToAdjacentShaft(ctx, side)) {
                connections++;
            }
        }
        return connections;
    }

    // Get the best vertical orange axis
    private Direction.Axis getBestVerticalOrangeAxis(BlockPlaceContext ctx) {
        int xConnections = countConnectedShaftsOnAxis(ctx, Direction.Axis.X);
        int zConnections = countConnectedShaftsOnAxis(ctx, Direction.Axis.Z);
        if (xConnections > zConnections) {
            return Direction.Axis.X;
        }
        if (zConnections > xConnections) {
            return Direction.Axis.Z;
        }
        return ctx.getHorizontalDirection().getAxis();
    }

    // Count the connected shafts on axis
    private int countConnectedShaftsOnAxis(BlockPlaceContext ctx, Direction.Axis axis) {
        int connections = 0;
        for (Direction side : Direction.values()) {
            if (side.getAxis() == axis && connectsToAdjacentShaft(ctx, side)) {
                connections++;
            }
        }
        return connections;
    }

    // Check if the block connects to the adjacent shaft
    private boolean connectsToAdjacentShaft(BlockPlaceContext ctx, Direction side) {
        BlockPos adjacentPos = ctx.getClickedPos().relative(side);
        BlockState adjacentState = ctx.getLevel().getBlockState(adjacentPos);
        return adjacentState.getBlock() instanceof IRotate rotate
                && rotate.hasShaftTowards(ctx.getLevel(), adjacentPos, adjacentState, side.getOpposite());
    }

    // Rotate the bi directional gearshift block
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (state.getValue(AXIS) == Direction.Axis.Y) {
            return state.setValue(FACING, rotation.rotate(getPlacementFacing(state)));
        }
        return super.rotate(state, rotation);
    }

    // Get the rotated block state
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction.Axis nextAxis = rotateAxisAround(originalState.getValue(AXIS), targetedFace.getAxis());
        Direction facing = originalState.getValue(FACING);
        if (nextAxis == Direction.Axis.Y && !facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        } else if (nextAxis != Direction.Axis.Y && facing.getAxis().isHorizontal()) {
            facing = Direction.UP;
        }
        return originalState.setValue(AXIS, nextAxis).setValue(FACING, facing);
    }

    // Rotate the axis around another axis
    private Direction.Axis rotateAxisAround(Direction.Axis axis, Direction.Axis rotationAxis) {
        if (axis == rotationAxis) {
            return axis;
        }
        for (Direction.Axis candidate : Direction.Axis.values()) {
            if (candidate != axis && candidate != rotationAxis) {
                return candidate;
            }
        }
        return axis;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle bi directional gearshift block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BiDirectionalGearshiftBlockEntity gearshift)) {
            return InteractionResult.PASS;
        }

        Direction clickedFace = hit.getDirection();
        BiDirectionalGearshiftBlockEntity.AxisRole role = axisRoleForFace(gearshift, clickedFace);
        if (role != null
                && gearshift.getAxisMode(role) == BiDirectionalGearshiftBlockEntity.AxisControlMode.PASSTHROUGH
                && !player.isCrouching()) {
            if (!level.isClientSide) {
                gearshift.toggleFaceOutputInverted(clickedFace);
                player.sendSystemMessage(Component.literal(clickedFace.name() + " output direction: "
                        + (gearshift.isFaceOutputInverted(clickedFace) ? "INVERTED" : "FORWARD")));
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            player.openMenu(gearshift, gearshift::sendToMenu);
        }
        return InteractionResult.SUCCESS;
    }

    // Get the axis role for face
    private BiDirectionalGearshiftBlockEntity.AxisRole axisRoleForFace(
            BiDirectionalGearshiftBlockEntity gearshift, Direction face) {
        if (face.getAxis() == gearshift.getLaneAxis(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY)) {
            return BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY;
        }
        if (face.getAxis() == gearshift.getLaneAxis(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY)) {
            return BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY;
        }
        return null;
    }

    // Get the block entity class
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @NotNull Class<BiDirectionalGearboxBlockEntity> getBlockEntityClass() {
        return (Class) BiDirectionalGearshiftBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends BiDirectionalGearboxBlockEntity> getBlockEntityType() {
        return CTBlockEntities.BI_DIRECTIONAL_GEARSHIFT.get();
    }
}
