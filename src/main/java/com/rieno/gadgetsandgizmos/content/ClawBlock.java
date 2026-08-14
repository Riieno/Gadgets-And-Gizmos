package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedRopeCompat;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.EnumMap;
import java.util.Map;

// Place and configure the rope claw used to capture physical targets
public class ClawBlock extends CTDirectionalBlock implements RopeHolderBlock<ClawBlockEntity>, BlockSubLevelCollisionShape, BlockSubLevelCustomCenterOfMass {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the jaw direction
    public static Direction getJawDirection(BlockState state) {

        return state.getValue(FACING);
    }

    // Get the base direction
    public static Direction getBaseDirection(BlockState state) {
        return getJawDirection(state).getOpposite();
    }

    private static final double ROPE_WINCH_SCAN_RADIUS_SQ = 8.0 * 8.0;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> COLLISION_SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> SUBLEVEL_COLLISION_SHAPES = new EnumMap<>(Direction.class);
    private static final VoxelShape SUBLEVEL_FALLBACK_CARDINAL_RING = Shapes.or(
            Block.box(0, 8, 0, 16, 16, 4),
            Block.box(0, 8, 12, 16, 16, 16),
            Block.box(0, 8, 0, 4, 16, 16),
            Block.box(12, 8, 0, 16, 16, 16));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        SHAPES.put(Direction.UP,
                Shapes.or(Block.box(0, 0, 0, 16, 9, 16),
                          Block.box(2, 9, 6, 14, 16, 10)));
        COLLISION_SHAPES.put(Direction.UP,
            Block.box(0, 0, 0, 16, 9, 16));

            SUBLEVEL_COLLISION_SHAPES.put(Direction.UP,
                Shapes.empty());

        SHAPES.put(Direction.DOWN,
                Shapes.or(Block.box(0, 7, 0, 16, 16, 16),
                          Block.box(2, 0, 6, 14, 7, 10)));
        COLLISION_SHAPES.put(Direction.DOWN,
            Block.box(0, 7, 0, 16, 16, 16));

            SUBLEVEL_COLLISION_SHAPES.put(Direction.DOWN,
                Shapes.empty());

        SHAPES.put(Direction.NORTH,
                Shapes.or(Block.box(0, 0, 7, 16, 16, 16),
                          Block.box(2, 6, 0, 14, 10, 7)));
        COLLISION_SHAPES.put(Direction.NORTH,
            Block.box(0, 0, 7, 16, 16, 16));

            SUBLEVEL_COLLISION_SHAPES.put(Direction.NORTH,
                Block.box(0, 8, 0, 16, 16, 4));

        SHAPES.put(Direction.SOUTH,
                Shapes.or(Block.box(0, 0, 0, 16, 16, 9),
                          Block.box(2, 6, 9, 14, 10, 16)));
        COLLISION_SHAPES.put(Direction.SOUTH,
            Block.box(0, 0, 0, 16, 16, 9));

            SUBLEVEL_COLLISION_SHAPES.put(Direction.SOUTH,
                Block.box(0, 8, 12, 16, 16, 16));

        SHAPES.put(Direction.EAST,
                Shapes.or(Block.box(0, 0, 0, 9, 16, 16),
                          Block.box(9, 6, 2, 16, 10, 14)));
        COLLISION_SHAPES.put(Direction.EAST,
            Block.box(0, 0, 0, 9, 16, 16));

            SUBLEVEL_COLLISION_SHAPES.put(Direction.EAST,
                Block.box(12, 8, 0, 16, 16, 16));

        SHAPES.put(Direction.WEST,
                Shapes.or(Block.box(7, 0, 0, 16, 16, 16),
                          Block.box(0, 6, 2, 7, 10, 14)));
        COLLISION_SHAPES.put(Direction.WEST,
            Block.box(7, 0, 0, 16, 16, 16));

            SUBLEVEL_COLLISION_SHAPES.put(Direction.WEST,
                Block.box(0, 8, 0, 4, 16, 16));
    }

    // Initialize the claw block
    public ClawBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {

        return COLLISION_SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {

        return COLLISION_SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter blockGetter, BlockState state) {

        VoxelShape shape = SUBLEVEL_COLLISION_SHAPES.getOrDefault(state.getValue(FACING), Shapes.empty());
        return shape.isEmpty() ? SUBLEVEL_FALLBACK_CARDINAL_RING : shape;
    }

    // Get the block support shape
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {

        return Shapes.block();
    }

    // Handle the neighboring block change
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (!level.isClientSide()) {
            Direction baseFace = getBaseDirection(state);
            int signal = 0;
            for (Direction d : Direction.values()) {
                if (d != baseFace) {

                    signal = Math.max(signal, level.getSignal(pos.relative(d), d));
                }
            }
            final int finalSignal = signal;
            withBlockEntityDo(level, pos, be -> be.updateSignal(finalSignal));
        }
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {

        BlockState placed = super.getStateForPlacement(ctx);
        if (placed == null) {
            placed = defaultBlockState();
        }
        return placed.setValue(FACING, Direction.DOWN).setValue(POWERED, false);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {

        com.simibubi.create.foundation.block.IBE.onRemove(state, level, pos, newState);
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(originLevel, oldPos, ClawBlockEntity::beginAssemblyTransfer);
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos, ClawBlockEntity::endAssemblyTransfer);
        RopeHolderBlock.super.afterMove(originLevel, resultingLevel, newState, oldPos, newPos);
    }

    // Get the block entity class
    @Override
    public Class<ClawBlockEntity> getBlockEntityClass() {
        return ClawBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ClawBlockEntity> getBlockEntityType() {
        return com.rieno.gadgetsandgizmos.registry.CTBlockEntities.CLAW.get();
    }

    // Get the center of mass
    @Override
    public Vector3dc getCenterOfMass(BlockGetter blockGetter, BlockState state) {

        return new Vector3d(0.5, 0.5, 0.5);
    }

    // Handle claw block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {

        if (!level.isClientSide() && stack.is(dev.simulated_team.simulated.index.SimTags.Items.DESTROYS_ROPE)) {
            return RopeHolderBlock.shearRope(this, level, pos, (ServerPlayer) player);
        }

        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() instanceof RopeItem) {
                if (tryAutoConnectRopeToWinch(level, pos, player, offhand)) {
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    // Handle claw block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                withBlockEntityDo(level, pos, be -> serverPlayer.openMenu(be, be::sendToMenu));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()) {
            withBlockEntityDo(level, pos, ClawBlockEntity::toggleManualOpenClosed);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // Try to auto connect rope to winch
    private boolean tryAutoConnectRopeToWinch(Level level, BlockPos clawPos, Player player,
                                               ItemStack ropeStack) {

        ClawBlockEntity clawBE = (ClawBlockEntity) level.getBlockEntity(clawPos);
        if (clawBE == null) return false;
        RopeStrandHolderBehavior clawHolder = clawBE.getRopeHolder();
        if (clawHolder == null || clawHolder.isAttached()) return false;

        BlockPos bestWinchPos = null;
        double bestDist = Double.MAX_VALUE;

        int r = 8;
        for (BlockPos candidate : BlockPos.betweenClosed(
                clawPos.offset(-r, -r, -r), clawPos.offset(r, r, r))) {
            if (!(level.getBlockEntity(candidate) instanceof RopeWinchBlockEntity winch)) continue;
            RopeStrandHolderBehavior winchHolder = winch.getBehaviour(RopeStrandHolderBehavior.TYPE);
            if (winchHolder == null || winchHolder.isAttached()) continue;
            double dist = candidate.distSqr(clawPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestWinchPos = candidate.immutable();
            }
        }

        if (bestWinchPos == null) return false;

        RopeWinchBlockEntity winchBE = (RopeWinchBlockEntity) level.getBlockEntity(bestWinchPos);
        if (winchBE == null) return false;
        RopeStrandHolderBehavior winchHolder = winchBE.getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (winchHolder == null || winchHolder.isAttached()) return false;

        if (SimulatedRopeCompat.createRope(winchHolder, clawHolder, !player.hasInfiniteMaterials())) {
            level.playSound(null, clawPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
            if (!player.isCreative()) {
                ropeStack.shrink(1);
            }
            return true;
        }
        return false;
    }

    // Get the x rotation degrees
    public static float getXRotationDegrees(Direction facing) {
        return switch (facing) {
            case UP   -> 0f;
            case DOWN -> 180f;
            default   -> 90f;
        };
    }

    // Get the y rotation degrees
    public static float getYRotationDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case EAST  -> 90f;
            case WEST  -> 270f;
            default    -> 0f;
        };
    }
}
