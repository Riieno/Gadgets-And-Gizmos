package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Mount, assemble and drive physical ailerons from a kinetic shaft
public class AileronBearingBlock extends DirectionalAxisKineticBlock
        implements IBE<AileronBearingBlockEntity>, IWrenchable, BlockSubLevelCollisionShape,
        BlockSubLevelAssemblyListener {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final VoxelShape SHAPE = Shapes.block();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing block
    public AileronBearingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(AXIS_ALONG_FIRST_COORDINATE, false));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return getShaftAxis(state);
    }

    // Get the minimum required speed level
    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.SLOW;
    }

    // Get the facing for placement
    @Override
    protected Direction getFacingForPlacement(BlockPlaceContext ctx) {
        return ctx.getClickedFace();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, bearing -> {
                bearing.disassembleMountedBlock(BearingHead.PRIMARY);
                bearing.disassembleMountedBlock(BearingHead.SECONDARY);
            });
        }

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof AileronBearingBlock)) {
            return InteractionResult.SUCCESS;
        }

        Direction clickedFace = ctx.getClickedFace();
        Direction facing = currentState.getValue(BlockStateProperties.FACING);
        BlockState rotated = clickedFace.getAxis() == facing.getAxis()
                ? currentState.cycle(AXIS_ALONG_FIRST_COORDINATE)
                : currentState.setValue(BlockStateProperties.FACING, clickedFace);
        if (!rotated.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }

        KineticBlockEntity.switchToBlockState(level, pos, updateAfterWrenched(rotated, ctx));
        if (level.getBlockState(pos) != state) {
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // Get the block support shape
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return SHAPE;
    }

    // Handle aileron bearing block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof AileronBearingBlockEntity bearing)) {
            return InteractionResult.PASS;
        }

        if (player.isCrouching()) {
            if (!level.isClientSide) {
                player.openMenu(bearing, bearing::sendToMenu);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BearingHead clickedHead = bearing.getHeadForDirection(hitResult.getDirection());
        if (clickedHead == null) {
            clickedHead = bearing.getHeadForHit(hitResult);
        }
        if (clickedHead == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            if (bearing.isMountedAssemblyPresent(clickedHead)) {
                bearing.disassembleMountedBlock(clickedHead);
            } else {
                bearing.tryAssembleMountedBlock(clickedHead);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Handle aileron bearing block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.isCrouching()) {
            return stack.isEmpty()
                    ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof AileronBearingBlockEntity bearing)) {
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
                || !(level.getBlockEntity(pos) instanceof AileronBearingBlockEntity bearing)) {
            return;
        }

        for (BearingHead head : BearingHead.values()) {
            if (fromPos.equals(bearing.getMountedBlockPos(head))
                    && bearing.absorbPlacedBlockOnAssembledHead(head, fromPos)) {
                return;
            }
        }
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = originLevel.getBlockEntity(oldPos);
        if (blockEntity instanceof AileronBearingBlockEntity bearing) {
            bearing.beginAssemblyTransfer();
        }
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = resultingLevel.getBlockEntity(newPos);
        if (blockEntity instanceof AileronBearingBlockEntity bearing) {
            bearing.finishAssemblyTransfer();
        }
    }

    // Get the block entity class
    @Override
    public Class<AileronBearingBlockEntity> getBlockEntityClass() {
        return AileronBearingBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends AileronBearingBlockEntity> getBlockEntityType() {
        return CTBlockEntities.AILERON_BEARING.get();
    }

    // Get the shaft axis
    public static Direction.Axis getShaftAxis(BlockState state) {
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.UP;
        boolean alongFirst = state.hasProperty(AXIS_ALONG_FIRST_COORDINATE)
                && state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        if (facing.getAxis() == Direction.Axis.X) {
            return alongFirst ? Direction.Axis.Y : Direction.Axis.Z;
        }
        if (facing.getAxis() == Direction.Axis.Y) {
            return alongFirst ? Direction.Axis.X : Direction.Axis.Z;
        }
        return alongFirst ? Direction.Axis.X : Direction.Axis.Y;
    }
}
