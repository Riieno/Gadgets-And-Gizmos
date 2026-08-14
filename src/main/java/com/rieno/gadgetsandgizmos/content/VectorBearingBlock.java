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
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Mount and assemble a physical bearing which can turn on two axes
public class VectorBearingBlock extends DirectionalKineticBlock implements IBE<VectorBearingBlockEntity>, IWrenchable,
        BlockSubLevelCollisionShape, BlockSubLevelAssemblyListener {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final VoxelShape BASE_UP = Shapes.or(
            Block.box(2, 0, 2, 14, 10, 14),
            Block.box(3, 10, 3, 13, 14, 13),
            Block.box(5, 14, 5, 11, 16, 11));
    private static final VoxelShape PHYSICS_BASE_UP = Shapes.or(
            Block.box(2, 0, 2, 14, 10, 14),
            Block.box(3, 10, 3, 13, 12, 13));
    private static final VoxelShape PLATE_UP = Block.box(0, 12, 0, 16, 16, 16);
    private static final VoxelShape BASE_DOWN = rotateX(BASE_UP, 2);
    private static final VoxelShape BASE_NORTH = rotateX(BASE_UP, 3);
    private static final VoxelShape BASE_SOUTH = rotateX(BASE_UP, 1);
    private static final VoxelShape BASE_EAST = rotateY(BASE_SOUTH, 1);
    private static final VoxelShape BASE_WEST = rotateY(BASE_SOUTH, 3);
    private static final VoxelShape PHYSICS_BASE_DOWN = rotateX(PHYSICS_BASE_UP, 2);
    private static final VoxelShape PHYSICS_BASE_NORTH = rotateX(PHYSICS_BASE_UP, 3);
    private static final VoxelShape PHYSICS_BASE_SOUTH = rotateX(PHYSICS_BASE_UP, 1);
    private static final VoxelShape PHYSICS_BASE_EAST = rotateY(PHYSICS_BASE_SOUTH, 1);
    private static final VoxelShape PHYSICS_BASE_WEST = rotateY(PHYSICS_BASE_SOUTH, 3);
    private static final VoxelShape PLATE_DOWN = rotateX(PLATE_UP, 2);
    private static final VoxelShape PLATE_NORTH = rotateX(PLATE_UP, 3);
    private static final VoxelShape PLATE_SOUTH = rotateX(PLATE_UP, 1);
    private static final VoxelShape PLATE_EAST = rotateY(PLATE_SOUTH, 1);
    private static final VoxelShape PLATE_WEST = rotateY(PLATE_SOUTH, 3);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vector bearing block
    public VectorBearingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Get the minimum required speed level
    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.SLOW;
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape base = baseShape(state);
        if (hasMountedAssembly(level, pos)) {
            return base;
        }
        return Shapes.or(base, plateShape(state));
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // Get the interaction shape
    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape base = baseShape(state);
        if (hasMountedAssembly(level, pos)) {
            return base;
        }
        return Shapes.or(base, plateShape(state));
    }

    // Check if this has mounted assembly
    private static boolean hasMountedAssembly(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof VectorBearingBlockEntity bearing
                && bearing.isMountedAssemblyPresent();
    }

    // Get the base shape
    private static VoxelShape baseShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> BASE_UP;
            case DOWN -> BASE_DOWN;
            case NORTH -> BASE_NORTH;
            case SOUTH -> BASE_SOUTH;
            case EAST -> BASE_EAST;
            case WEST -> BASE_WEST;
        };
    }

    // Get the plate shape
    private static VoxelShape plateShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> PLATE_UP;
            case DOWN -> PLATE_DOWN;
            case NORTH -> PLATE_NORTH;
            case SOUTH -> PLATE_SOUTH;
            case EAST -> PLATE_EAST;
            case WEST -> PLATE_WEST;
        };
    }

    // Get the physics base shape
    private static VoxelShape physicsBaseShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> PHYSICS_BASE_UP;
            case DOWN -> PHYSICS_BASE_DOWN;
            case NORTH -> PHYSICS_BASE_NORTH;
            case SOUTH -> PHYSICS_BASE_SOUTH;
            case EAST -> PHYSICS_BASE_EAST;
            case WEST -> PHYSICS_BASE_WEST;
        };
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return physicsBaseShape(state);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return super.getStateForPlacement(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle vector bearing block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof VectorBearingBlockEntity bearing)) {
            return InteractionResult.PASS;
        }

        if (player.isCrouching()) {
            if (!level.isClientSide) {
                player.openMenu(bearing, bearing::sendToMenu);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            bearing.toggleMountedBlock();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Handle vector bearing block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.isCrouching()) {
            return stack.isEmpty()
                    ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof VectorBearingBlockEntity bearing)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            player.openMenu(bearing, bearing::sendToMenu);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    // Handle the neighboring block change
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide || isMoving
                || !(level.getBlockEntity(pos) instanceof VectorBearingBlockEntity bearing)) {
            return;
        }
        if (fromPos.equals(bearing.getMountedBlockPos())) {
            bearing.absorbPlacedBlockOnAssembledHead(fromPos);
        }
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = originLevel.getBlockEntity(oldPos);
        if (blockEntity instanceof VectorBearingBlockEntity bearing) {
            bearing.beginAssemblyTransfer();
        }
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = resultingLevel.getBlockEntity(newPos);
        if (blockEntity instanceof VectorBearingBlockEntity bearing) {
            bearing.finishAssemblyTransfer();
        }
    }

    // Get the block entity class
    @Override
    public Class<VectorBearingBlockEntity> getBlockEntityClass() {
        return VectorBearingBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends VectorBearingBlockEntity> getBlockEntityType() {
        return CTBlockEntities.VECTOR_BEARING.get();
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
