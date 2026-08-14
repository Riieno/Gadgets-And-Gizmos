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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Add selectable linker faces which relay their configured redstone signal
public class ContraptionNetworkLinkerPlaneBlock extends Block implements IBE<ContraptionNetworkLinkerPlaneBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DirectionProperty ORIENTATION = DirectionProperty.create(
            "orientation", Direction.Plane.HORIZONTAL);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker plane block
    public ContraptionNetworkLinkerPlaneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(PipeBlock.UP, false)
                .setValue(PipeBlock.DOWN, false)
                .setValue(PipeBlock.NORTH, false)
                .setValue(PipeBlock.SOUTH, false)
                .setValue(PipeBlock.WEST, false)
                .setValue(PipeBlock.EAST, false)
                .setValue(ORIENTATION, Direction.NORTH));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.UP, PipeBlock.DOWN, PipeBlock.NORTH, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.EAST,
                ORIENTATION);
    }

    // Rotate the contraption network linker plane block
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction dir : Direction.values()) {
            rotated = rotated.setValue(property(rotation.rotate(dir)), state.getValue(property(dir)));
        }
        return rotated.setValue(ORIENTATION, rotation.rotate(state.getValue(ORIENTATION)));
    }

    // Mirror the contraption network linker plane block
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction dir : Direction.values()) {
            mirrored = mirrored.setValue(property(mirror.mirror(dir)), state.getValue(property(dir)));
        }
        return mirrored.setValue(ORIENTATION, mirror.mirror(state.getValue(ORIENTATION)));
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    // Get the occlusion shape
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    // Get the render shape
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // Check if this can be replaced
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return true;
    }

    // Check if this is a signal source
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    // Get the signal
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!(level instanceof Level worldLevel)) {
            return 0;
        }
        return ContraptionNetworkLinkerSignalBus.getPlaneBlockSignal(worldLevel, pos, side);
    }

    // Get the direct signal
    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    // Check if this can connect redstone
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return side != null && level instanceof Level worldLevel
                && ContraptionNetworkLinkerSignalBus.getPlaneBlockSignal(worldLevel, pos, side) > 0;
    }

    // Handle the neighboring block change
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean isMoving) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ContraptionNetworkLinkerPlaneBlockEntity plane)) {
            return;
        }
        for (Direction dir : Direction.values()) {
            if (hasPlane(state, dir) && pos.relative(dir.getOpposite()).equals(fromPos)
                    && level.getBlockState(fromPos).isAir()) {
                plane.removePlane(dir);
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    // Check if this has plane
    public static boolean hasPlane(BlockState state, Direction dir) {
        return state != null
                && state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock
                && state.getValue(property(dir));
    }

    // Check if this has any plane
    public static boolean hasAnyPlane(BlockState state) {
        if (state == null || !(state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            if (state.getValue(property(dir))) {
                return true;
            }
        }
        return false;
    }

    // Copy the contraption network linker plane block with the plane
    public static BlockState withPlane(BlockState state, Direction dir, boolean present) {
        return state.setValue(property(dir), present);
    }

    // Get the property
    private static BooleanProperty property(Direction dir) {
        return PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
    }

    // Get the horizontal quarter turns
    static int horizontalQuarterTurns(Direction from, Direction to) {
        if (from == null || to == null || !from.getAxis().isHorizontal() || !to.getAxis().isHorizontal()) {
            return 0;
        }
        for (int turns = 0; turns < 4; turns++) {
            if (rotateDirection(from, turns) == to) {
                return turns;
            }
        }
        return 0;
    }

    // Rotate the direction
    static Direction rotateDirection(Direction dir, int quarterTurns) {
        Direction rotated = dir == null ? Direction.NORTH : dir;
        int turns = Math.floorMod(quarterTurns, 4);
        for (int idx = 0; idx < turns; idx++) {
            rotated = Rotation.CLOCKWISE_90.rotate(rotated);
        }
        return rotated;
    }

    // Get the block entity class
    @Override
    public Class<ContraptionNetworkLinkerPlaneBlockEntity> getBlockEntityClass() {
        return ContraptionNetworkLinkerPlaneBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ContraptionNetworkLinkerPlaneBlockEntity> getBlockEntityType() {
        return CTBlockEntities.CONTRAPTION_NETWORK_LINKER_PLANE.get();
    }
}
