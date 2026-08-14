package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockSubLevelCustomCenterOfMass;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.EnumMap;
import java.util.Map;

// Place, link and configure powered rope ziplines across normal and Sable levels
public class PoweredZiplineBlock extends CTDirectionalBlock implements RopeHolderBlock<PoweredZiplineBlockEntity>, BlockSubLevelCollisionShape, BlockSubLevelCustomCenterOfMass {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        SHAPES.put(Direction.UP, Block.box(0, 0, 0, 16, 9, 16));
        SHAPES.put(Direction.DOWN, Block.box(0, 7, 0, 16, 16, 16));
        SHAPES.put(Direction.NORTH, Block.box(0, 0, 7, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 9));
        SHAPES.put(Direction.EAST, Block.box(0, 0, 0, 9, 16, 16));
        SHAPES.put(Direction.WEST, Block.box(7, 0, 0, 16, 16, 16));
    }

    // Initialize the powered zipline block
    public PoweredZiplineBlock(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the base direction
    public static Direction getBaseDirection(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter blockGetter, BlockState state) {
        return Shapes.empty();
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placed = super.getStateForPlacement(ctx);
        return (placed == null ? defaultBlockState() : placed).setValue(FACING, Direction.DOWN);
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
        withBlockEntityDo(originLevel, oldPos, PoweredZiplineBlockEntity::beginAssemblyTransfer);
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos, PoweredZiplineBlockEntity::endAssemblyTransfer);
        RopeHolderBlock.super.afterMove(originLevel, resultingLevel, newState, oldPos, newPos);
    }

    // Get the block entity class
    @Override
    public Class<PoweredZiplineBlockEntity> getBlockEntityClass() {
        return PoweredZiplineBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends PoweredZiplineBlockEntity> getBlockEntityType() {
        return com.rieno.gadgetsandgizmos.registry.CTBlockEntities.POWERED_ZIPLINE.get();
    }

    // Get the center of mass
    @Override
    public Vector3dc getCenterOfMass(BlockGetter blockGetter, BlockState state) {
        return new Vector3d(0.5, 0.5, 0.5);
    }

    // Handle powered zipline block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!level.isClientSide() && stack.is(dev.simulated_team.simulated.index.SimTags.Items.DESTROYS_ROPE)) {
            PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos, PoweredZiplineBlockEntity.class);
            if (zipline != null && zipline.getRopeHolder() != null) {
                zipline.destroyNearestHangingRope((ServerPlayer) player, hitResult.getLocation());
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide() && stack.getItem() instanceof RopeItem) {
            PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos, PoweredZiplineBlockEntity.class);
            if (zipline != null && zipline.hasPathAttachment()) {
                if (stack.has(SimDataComponents.ROPE_FIRST_CONNECTION)) {
                    if (zipline.tryAttachHangingRope(player, stack)) {
                        return ItemInteractionResult.SUCCESS;
                    }
                    return ItemInteractionResult.FAIL;
                }
                stack.set(SimDataComponents.ROPE_FIRST_CONNECTION, zipline.getBlockPos());
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOL_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                return ItemInteractionResult.SUCCESS;
            }
            if (zipline != null && zipline.tryAutoAttachToChainOrRope(player)) {
                return ItemInteractionResult.SUCCESS;
            }
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    // Handle powered zipline block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos, PoweredZiplineBlockEntity.class);
                if (zipline != null) {
                    serverPlayer.openMenu(zipline, zipline::sendToMenu);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (level.isClientSide()) {
            ZiplineRidingController controller = ZiplineRidingController.getInstance();
            if (controller != null) {
                controller.tryMount(player, pos);
            }
        } else {
            PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos, PoweredZiplineBlockEntity.class);
            if (zipline != null) {
                zipline.setRidingPlayer(player.getUUID());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
