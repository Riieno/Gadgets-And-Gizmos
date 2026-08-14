package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// Expose the carriage coupler's attachment, redstone mode and safe wrench behavior
public class ShipCouplerBlock extends HorizontalDirectionalBlock
        implements IBE<ShipCouplerBlockEntity>, IWrenchable, BlockSubLevelAssemblyListener {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<ShipCouplerBlock> CODEC = simpleCodec(ShipCouplerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BooleanProperty.create("attached");
    public static final double ATTACH_RADIUS = 1.0D;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> ATTACHED_SHAPES = new EnumMap<>(Direction.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        VoxelShape northMount = Shapes.or(
                Block.box(1.0D, 1.0D, 11.0D, 15.0D, 15.0D, 16.0D),
                Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D));
        VoxelShape north = Shapes.or(
                northMount,
                Block.box(3.0D, 3.0D, 0.0D, 13.0D, 13.0D, 5.0D));
        SHAPES.put(Direction.NORTH, north);
        SHAPES.put(Direction.EAST, rotateClockwise(north));
        SHAPES.put(Direction.SOUTH, rotateClockwise(SHAPES.get(Direction.EAST)));
        SHAPES.put(Direction.WEST, rotateClockwise(SHAPES.get(Direction.SOUTH)));
        ATTACHED_SHAPES.put(Direction.NORTH, northMount);
        ATTACHED_SHAPES.put(Direction.EAST, rotateClockwise(northMount));
        ATTACHED_SHAPES.put(Direction.SOUTH, rotateClockwise(ATTACHED_SHAPES.get(Direction.EAST)));
        ATTACHED_SHAPES.put(Direction.WEST, rotateClockwise(ATTACHED_SHAPES.get(Direction.SOUTH)));
    }

    // Initialize the ship coupler block
    public ShipCouplerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(ATTACHED, false));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the codec
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, ATTACHED);
    }

    // Get the state for placement
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(POWERED, ctx.getLevel().hasNeighborSignal(pos))
                .setValue(ATTACHED, false);
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                   CollisionContext ctx) {
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext ctx) {
        if (state.getValue(ATTACHED) && ctx instanceof SableCollisionContext) {
            return ATTACHED_SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
        }
        return getShape(state, level, pos, ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle ship coupler block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!player.mayBuild()
                || !(level.getBlockEntity(pos) instanceof ShipCouplerBlockEntity coupler)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean inverted = coupler.toggleRedstoneBehavior();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(inverted
                    ? "createthrusters.ship_coupler.redstone.unpowered_attaches"
                    : "createthrusters.ship_coupler.redstone.powered_attaches"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Update the coupler state after wrenching
    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState previousState = level.getBlockState(pos);
        if (previousState.is(this)
                && previousState.getValue(FACING) != newState.getValue(FACING)) {
            if (!level.isClientSide) {
                withBlockEntityDo(level, pos, ShipCouplerBlockEntity::onFacingChanged);
            }
            newState = newState.setValue(ATTACHED, false);
        }
        return IWrenchable.super.updateAfterWrenched(newState, ctx);
    }

    // Handle the neighboring block change
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ShipCouplerBlockEntity coupler) {
            coupler.onRedstoneStateChanged(powered);
        } else if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof ShipCouplerBlockEntity coupler) {
            coupler.onBlockRemoved();
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(originLevel, oldPos, ShipCouplerBlockEntity::beginAssemblyTransfer);
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos, ShipCouplerBlockEntity::endAssemblyTransfer);
    }

    // Get the block entity class
    @Override
    public Class<ShipCouplerBlockEntity> getBlockEntityClass() {
        return ShipCouplerBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ShipCouplerBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SHIP_COUPLER.get();
    }

    // Create the block entity
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShipCouplerBlockEntity(pos, state);
    }

    // Rotate the clockwise
    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] res = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> res[0] = Shapes.or(res[0],
                Block.box(16.0D - maxZ * 16.0D, minY * 16.0D, minX * 16.0D,
                        16.0D - minZ * 16.0D, maxY * 16.0D, maxX * 16.0D)));
        return res[0];
    }
}
