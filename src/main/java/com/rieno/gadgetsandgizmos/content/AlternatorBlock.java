package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Expose the alternator shaft and orient its energy output body
public class AlternatorBlock extends DirectionalKineticBlock implements IBE<AlternatorBlockEntity>, IWrenchable {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double[][] SOUTH_SHAPE_BOXES = {
            {1.1D, 0D, 0.35D, 14.9D, 2.35D, 14.05D},
            {0.35D, 1.6D, 1.04D, 15.65D, 15.45D, 13.55D},
            {-0.42D, 4.4D, 3.7D, 1.82D, 11.6D, 11.9D},
            {14.18D, 4.4D, 7D, 16.42D, 11.6D, 11.9D},
            {4.1D, 14.8D, 4D, 11.9D, 17.45D, 10D}
    };
    private static final VoxelShape SHAPE_SOUTH = createShape(SOUTH_SHAPE_BOXES);
    private static final VoxelShape SHAPE_NORTH = rotateY(SHAPE_SOUTH, 2);
    private static final VoxelShape SHAPE_EAST = rotateY(SHAPE_SOUTH, 1);
    private static final VoxelShape SHAPE_WEST = rotateY(SHAPE_SOUTH, 3);
    private static final VoxelShape SHAPE_UP = rotateX(SHAPE_SOUTH, 3);
    private static final VoxelShape SHAPE_DOWN = rotateX(SHAPE_SOUTH, 1);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the alternator block
    public AlternatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
        };
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    // Get the minimum required speed level
    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.NONE;
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction preferred = getPreferredFacing(ctx);
        if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown() || preferred == null) {
            return super.getStateForPlacement(ctx);
        }
        return defaultBlockState().setValue(FACING, preferred);
    }

    // Get the block entity class
    @Override
    public Class<AlternatorBlockEntity> getBlockEntityClass() {
        return AlternatorBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends AlternatorBlockEntity> getBlockEntityType() {
        return CTBlockEntities.ALTERNATOR.get();
    }

    // Create the shape
    private static VoxelShape createShape(double[][] boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] coords : boxes) {
            shape = Shapes.or(shape, box(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]));
        }
        return shape.optimize();
    }

    // Rotate the x
    private static VoxelShape rotateX(VoxelShape shape, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        VoxelShape rotated = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(new AABB(
                        box.minX,
                        1.0D - box.maxZ,
                        box.minY,
                        box.maxX,
                        1.0D - box.minZ,
                        box.maxY
                )));
            }
            rotated = next.optimize();
        }
        return rotated;
    }

    // Rotate the y
    private static VoxelShape rotateY(VoxelShape shape, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        VoxelShape rotated = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(new AABB(
                        box.minZ,
                        box.minY,
                        1.0D - box.maxX,
                        box.maxZ,
                        box.maxY,
                        1.0D - box.minX
                )));
            }
            rotated = next.optimize();
        }
        return rotated;
    }
}
