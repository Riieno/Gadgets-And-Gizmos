package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

// Mount and configure a swivel bearing specialized for attached thrusters
public class ThrusterBearingBlock extends SwivelBearingBlock implements IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE =
            DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing block
    public ThrusterBearingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS_ALONG_FIRST_COORDINATE, false));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS_ALONG_FIRST_COORDINATE);
        super.createBlockStateDefinition(builder);
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) {
            return null;
        }
        Direction facing = state.getValue(FACING);
        boolean alongFirst = facing.getAxis().isVertical()
                && ctx.getHorizontalDirection().getAxis() == Direction.Axis.X;
        return state.setValue(AXIS_ALONG_FIRST_COORDINATE, alongFirst);
    }

    // Get the block entity class
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<SwivelBearingBlockEntity> getBlockEntityClass() {
        return (Class) ThrusterBearingBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends SwivelBearingBlockEntity> getBlockEntityType() {
        return CTBlockEntities.THRUSTER_BEARING.get();
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return false;
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
        Direction clickedFace = ctx.getClickedFace();
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, SwivelBearingBlockEntity::disassemble);
        }

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof ThrusterBearingBlock)) {
            return InteractionResult.SUCCESS;
        }

        BlockState rotated = getBearingWrenchRotation(currentState, clickedFace);
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
        if (!level.isClientSide) {

            withBlockEntityDo(level, pos, SwivelBearingBlockEntity::disassemble);
        }

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof ThrusterBearingBlock)) {
            return InteractionResult.SUCCESS;
        }

        return super.onSneakWrenched(currentState, ctx);
    }

    // Get the bearing wrench rotation
    private static BlockState getBearingWrenchRotation(BlockState state, Direction clickedFace) {
        Direction facing = state.getValue(FACING);
        if (clickedFace.getAxis() == facing.getAxis()) {
            return state.cycle(AXIS_ALONG_FIRST_COORDINATE);
        }
        return state.setValue(FACING, clickedFace);
    }

    // Handle thruster bearing block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof NameTagItem && stack.has(DataComponents.CUSTOM_NAME)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof INamedBlockEntity named) {
                if (!level.isClientSide) {
                    net.minecraft.network.chat.Component name = stack.get(DataComponents.CUSTOM_NAME);
                    named.setCustomName(name != null ? name.getString() : null);
                    if (!player.isCreative()) stack.shrink(1);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Renamed to: " + (name != null ? name.getString() : "")), true);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof INamedBlockEntity named) {
                net.minecraft.network.chat.Component name = stack.get(DataComponents.CUSTOM_NAME);
                named.setCustomName(name != null ? name.getString() : null);
            }
        }
    }

}
