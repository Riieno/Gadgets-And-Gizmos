package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Mount the half-height panel used to control a nearby ship
public class ShipControlModuleBlock extends Block implements IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<ShipControlModuleBlock> CODEC = simpleCodec(ShipControlModuleBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // The horizontal facing rotates the controls around their mounting face;
    // mount_face identifies the sturdy block face which physically supports
    // the half-height module.
    public static final DirectionProperty MOUNT_FACE = DirectionProperty.create("mount_face");
    private static final VoxelShape FLOOR_SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape CEILING_SHAPE = Block.box(0, 8, 0, 16, 16, 16);
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 0, 0, 8, 16, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(8, 0, 0, 16, 16, 16);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship control module block
    public ShipControlModuleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(MOUNT_FACE, Direction.DOWN));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT_FACE);
    }

    // Get the state for placement
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placed = defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(MOUNT_FACE, ctx.getClickedFace().getOpposite());
        return placed.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? placed : null;
    }

    // Check if this can survive
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction mountFace = mountFace(state);
        BlockPos supportPos = pos.relative(mountFace);
        BlockState supportState = level.getBlockState(supportPos);
        if (supportState.getBlock() instanceof SlabBlock
                || supportState.getBlock() instanceof ShipControlModuleBlock) {
            return false;
        }
        if (level.getBlockEntity(supportPos) instanceof AnalogueContraptionControllerBlockEntity controller) {
            BlockState embedded = controller.getEmbeddedBlockState();
            if (embedded != null && (embedded.getBlock() instanceof SlabBlock
                    || embedded.getBlock() instanceof ShipControlModuleBlock)) {
                return false;
            }
        }
        return supportState.isFaceSturdy(level, supportPos, mountFace.getOpposite());
    }

    // Update the shape
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == mountFace(state) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return switch (mountFace(state)) {
            case UP -> CEILING_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case DOWN -> FLOOR_SHAPE;
        };
    }

    // Rotate the ship control module block
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state
                .setValue(FACING, rotation.rotate(state.getValue(FACING)))
                .setValue(MOUNT_FACE, rotation.rotate(mountFace(state)));
    }

    // Mirror the ship control module block
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state
                .setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(MOUNT_FACE, mirror.mirror(mountFace(state)));
    }

    // Get the rotated block state
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    // Return the physical support-facing direction. Older schematic states
    // did not record mount_face and were always floor-mounted.
    private static Direction mountFace(BlockState state) {
        return state.hasProperty(MOUNT_FACE) ? state.getValue(MOUNT_FACE) : Direction.DOWN;
    }

    // Return the exposed face where an ACC can be embedded into this module.
    public static Direction exposedFace(BlockState state) {
        return mountFace(state).getOpposite();
    }

    // Get the codec
    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
