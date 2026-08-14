package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Assemble and drive a kinetic chain of physical scissor arms
public class ScissorPistonBlock extends DirectionalKineticBlock
        implements IBE<ScissorPistonBlockEntity>, IWrenchable, BlockSubLevelCollisionShape,
        BlockSubLevelAssemblyListener {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    private static final VoxelShape BASE_UP = Shapes.or(
            Block.box(0, 0, 0, 16, 12, 16),
            Block.box(0, 1, 0, 16, 13, 16));
    private static final VoxelShape HEAD_UP = Block.box(0, 12, 0, 16, 16, 16);
    private static final VoxelShape BASE_DOWN = rotateX(BASE_UP, 2);
    private static final VoxelShape BASE_NORTH = rotateX(BASE_UP, 3);
    private static final VoxelShape BASE_SOUTH = rotateX(BASE_UP, 1);
    private static final VoxelShape BASE_EAST = rotateY(BASE_SOUTH, 1);
    private static final VoxelShape BASE_WEST = rotateY(BASE_SOUTH, 3);
    private static final VoxelShape HEAD_DOWN = rotateX(HEAD_UP, 2);
    private static final VoxelShape HEAD_NORTH = rotateX(HEAD_UP, 3);
    private static final VoxelShape HEAD_SOUTH = rotateX(HEAD_UP, 1);
    private static final VoxelShape HEAD_EAST = rotateY(HEAD_SOUTH, 1);
    private static final VoxelShape HEAD_WEST = rotateY(HEAD_SOUTH, 3);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston block
    public ScissorPistonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(ASSEMBLED, false));
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
        builder.add(ASSEMBLED);
    }

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

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape base = baseShape(state);
        if (state.getValue(ASSEMBLED) || hasMountedAssembly(level, pos)) {
            return base;
        }
        return Shapes.or(base, headShape(state));
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        if (state.getValue(ASSEMBLED) || hasMountedAssembly(level, pos)) {
            return ScissorPistonPhysicsShapes.assembledBaseShape(state.getValue(FACING));
        }
        return getShape(state, level, pos, ctx);
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        if (state.getValue(ASSEMBLED)) {
            return ScissorPistonPhysicsShapes.assembledBaseShape(state.getValue(FACING));
        }
        return Shapes.or(baseShape(state), headShape(state));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle scissor piston block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isCrouching() || !isHeadHit(pos, state.getValue(FACING), hitResult)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof ScissorPistonBlockEntity piston)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (piston.isMountedAssemblyPresent()) {
                piston.disassembleMountedBlock();
            } else {
                piston.tryAssembleMountedBlock();
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Handle scissor piston block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isCrouching() || CTItems.SCISSOR_ARMS == null
                || !stack.is(CTItems.SCISSOR_ARMS.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof ScissorPistonBlockEntity piston)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && piston.addExtension(player, stack)) {
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ScissorPistonBlockEntity piston) {
            piston.disassembleMountedBlock();
        }
        BlockState currentState = level.getBlockState(pos);
        BlockState rotationSource = currentState.getBlock() instanceof ScissorPistonBlock
                ? currentState.setValue(ASSEMBLED, false)
                : state.setValue(ASSEMBLED, false);
        BlockState rotated = getRotatedBlockState(rotationSource, ctx.getClickedFace());
        if (!rotated.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }
        KineticBlockEntity.switchToBlockState(level, pos, updateAfterWrenched(rotated, ctx));
        if (level.getBlockState(pos) != state) {
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // Handle crouching wrench use
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ScissorPistonBlockEntity piston) {
            piston.breakWithWrench(player);
        } else {
            level.destroyBlock(pos, false);
        }
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof ScissorPistonBlockEntity piston) {
            piston.onBaseRemoved();
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = originLevel.getBlockEntity(oldPos);
        if (blockEntity instanceof ScissorPistonBlockEntity piston) {
            piston.beginAssemblyTransfer();
        }
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = resultingLevel.getBlockEntity(newPos);
        if (blockEntity instanceof ScissorPistonBlockEntity piston) {
            piston.finishAssemblyTransfer();
        }
    }

    // Get the block entity class
    @Override
    public Class<ScissorPistonBlockEntity> getBlockEntityClass() {
        return ScissorPistonBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ScissorPistonBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SCISSOR_PISTON.get();
    }

    // Check if this has mounted assembly
    private static boolean hasMountedAssembly(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ScissorPistonBlockEntity piston
                && piston.isMountedAssemblyPresent();
    }

    // Check if this is head hit
    private static boolean isHeadHit(BlockPos pos, Direction facing, BlockHitResult hitResult) {
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double axisCoordinate = switch (facing.getAxis()) {
            case X -> local.x;
            case Y -> local.y;
            case Z -> local.z;
        };
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? axisCoordinate >= 0.75D
                : axisCoordinate <= 0.25D;
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

    // Get the head shape
    private static VoxelShape headShape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> HEAD_UP;
            case DOWN -> HEAD_DOWN;
            case NORTH -> HEAD_NORTH;
            case SOUTH -> HEAD_SOUTH;
            case EAST -> HEAD_EAST;
            case WEST -> HEAD_WEST;
        };
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
