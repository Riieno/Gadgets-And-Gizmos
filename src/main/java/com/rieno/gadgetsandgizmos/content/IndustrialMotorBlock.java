package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Orient and configure a powered source of adjustable kinetic speed
public class IndustrialMotorBlock extends DirectionalKineticBlock implements IBE<IndustrialMotorBlockEntity>, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final double[][] SOUTH_SHAPE_BOXES = {
            {2D, 0.2D, 2D, 14D, 2.05D, 13D},
            {1.9D, 1.9D, 2.25D, 14.1D, 14.1D, 11.75D},
            {3.5D, 4D, -0.05D, 12.5D, 12D, 2D},
            {3D, 3D, 11.8D, 13D, 13D, 14.4D},
            {14.2D, 4.8D, 3D, 16.15D, 11.2D, 11D},
            {4.4D, 14.05D, 3D, 11.6D, 16.65D, 11.2D}
    };
    private static final VoxelShape SHAPE_SOUTH = createShape(SOUTH_SHAPE_BOXES);
    private static final VoxelShape SHAPE_NORTH = IndustrialMotorShapeRotation.fromSouth(SHAPE_SOUTH, Direction.NORTH);
    private static final VoxelShape SHAPE_EAST = IndustrialMotorShapeRotation.fromSouth(SHAPE_SOUTH, Direction.EAST);
    private static final VoxelShape SHAPE_WEST = IndustrialMotorShapeRotation.fromSouth(SHAPE_SOUTH, Direction.WEST);
    private static final VoxelShape SHAPE_UP = IndustrialMotorShapeRotation.fromSouth(SHAPE_SOUTH, Direction.UP);
    private static final VoxelShape SHAPE_DOWN = IndustrialMotorShapeRotation.fromSouth(SHAPE_SOUTH, Direction.DOWN);
    private static final VoxelShape BUTTON_SHAPE_SOUTH = box(0.9D, 6.0D, 3.0D, 1.8D, 10.0D, 11.0D);
    private static final VoxelShape BUTTON_SHAPE_NORTH = IndustrialMotorShapeRotation.fromSouth(BUTTON_SHAPE_SOUTH, Direction.NORTH);
    private static final VoxelShape BUTTON_SHAPE_EAST = IndustrialMotorShapeRotation.fromSouth(BUTTON_SHAPE_SOUTH, Direction.EAST);
    private static final VoxelShape BUTTON_SHAPE_WEST = IndustrialMotorShapeRotation.fromSouth(BUTTON_SHAPE_SOUTH, Direction.WEST);
    private static final VoxelShape BUTTON_SHAPE_UP = IndustrialMotorShapeRotation.fromSouth(BUTTON_SHAPE_SOUTH, Direction.UP);
    private static final VoxelShape BUTTON_SHAPE_DOWN = IndustrialMotorShapeRotation.fromSouth(BUTTON_SHAPE_SOUTH, Direction.DOWN);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the industrial motor block
    public IndustrialMotorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POWERED, true));
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
        builder.add(new Property[]{POWERED});
    }

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
        if ((ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) || preferred == null) {
            return super.getStateForPlacement(ctx);
        }
        return defaultBlockState().setValue(FACING, preferred).setValue(POWERED, true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle industrial motor block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.mayBuild() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!isButtonHit(state, pos, hit)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, IndustrialMotorBlockEntity::toggleEnabled);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Get the block entity class
    @Override
    public Class<IndustrialMotorBlockEntity> getBlockEntityClass() {
        return IndustrialMotorBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends IndustrialMotorBlockEntity> getBlockEntityType() {
        return CTBlockEntities.INDUSTRIAL_MOTOR.get();
    }

    // Hide the stress impact
    @Override
    public boolean hideStressImpact() {
        return true;
    }

    // Check if this is button hit
    private static boolean isButtonHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB box : getButtonShape(state).toAabbs()) {
            if (box.inflate(0.01D).contains(local)) {
                return true;
            }
        }
        return false;
    }

    // Get the button shape
    private static VoxelShape getButtonShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case NORTH -> BUTTON_SHAPE_NORTH;
            case SOUTH -> BUTTON_SHAPE_SOUTH;
            case EAST -> BUTTON_SHAPE_EAST;
            case WEST -> BUTTON_SHAPE_WEST;
            case UP -> BUTTON_SHAPE_UP;
            case DOWN -> BUTTON_SHAPE_DOWN;
        };
    }

    // Create the shape
    private static VoxelShape createShape(double[][] boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] coords : boxes) {
            shape = Shapes.or(shape, box(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]));
        }
        return shape.optimize();
    }

}
