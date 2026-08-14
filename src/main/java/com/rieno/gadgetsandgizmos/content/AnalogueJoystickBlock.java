package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import net.minecraft.core.component.DataComponents;
import com.rieno.gadgetsandgizmos.util.QuietUse;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

// Mount an analogue joystick and expose its live axes through redstone and direct use
public class AnalogueJoystickBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, IWrenchable, QuietUse {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final MapCodec<AnalogueJoystickBlock> CODEC = simpleCodec(AnalogueJoystickBlock::new);
    public static final DirectionProperty FACING = FaceAttachedHorizontalDirectionalBlock.FACING;
    public static final EnumProperty<AttachFace> FACE = FaceAttachedHorizontalDirectionalBlock.FACE;
    private static final VoxelShape FLOOR_SHAPE = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 5.0D, 14.0D),
            Block.box(6.5D, 5.0D, 6.5D, 9.5D, 11.0D, 9.5D),
            Block.box(5.0D, 10.5D, 5.0D, 11.0D, 13.5D, 11.0D));
    private static final VoxelShape CEILING_SHAPE = Shapes.or(
            Block.box(2.0D, 11.0D, 2.0D, 14.0D, 16.0D, 14.0D),
            Block.box(6.5D, 5.0D, 6.5D, 9.5D, 11.0D, 9.5D),
            Block.box(5.0D, 2.5D, 5.0D, 11.0D, 5.5D, 11.0D));
    private static final VoxelShape NORTH_WALL_SHAPE = Shapes.or(
            Block.box(2.0D, 2.0D, 11.0D, 14.0D, 14.0D, 16.0D),
            Block.box(6.5D, 6.5D, 5.0D, 9.5D, 9.5D, 11.0D),
            Block.box(5.0D, 5.0D, 2.5D, 11.0D, 11.0D, 5.5D));
    private static final VoxelShape SOUTH_WALL_SHAPE = Shapes.or(
            Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 5.0D),
            Block.box(6.5D, 6.5D, 5.0D, 9.5D, 9.5D, 11.0D),
            Block.box(5.0D, 5.0D, 10.5D, 11.0D, 11.0D, 13.5D));
    private static final VoxelShape EAST_WALL_SHAPE = Shapes.or(
            Block.box(11.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D),
            Block.box(5.0D, 6.5D, 6.5D, 11.0D, 9.5D, 9.5D),
            Block.box(2.5D, 5.0D, 5.0D, 5.5D, 11.0D, 11.0D));
    private static final VoxelShape WEST_WALL_SHAPE = Shapes.or(
            Block.box(0.0D, 2.0D, 2.0D, 5.0D, 14.0D, 14.0D),
            Block.box(5.0D, 6.5D, 6.5D, 11.0D, 9.5D, 9.5D),
            Block.box(10.5D, 5.0D, 5.0D, 13.5D, 11.0D, 11.0D));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick block
    public AnalogueJoystickBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACE, FACING));
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {

        if (ctx.getClickedFace() != Direction.UP) {
            return null;
        }
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) {
            return null;
        }
        return state.getValue(FACE) == AttachFace.FLOOR ? state : null;
    }

    // Check if this can survive
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {

        if (state.getValue(FACE) != AttachFace.FLOOR) {
            return false;
        }
        return super.canSurvive(state, level, pos);
    }

    // Get the rotated block state
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {

        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    // Rotate the analogue joystick block
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    // Mirror the analogue joystick block
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {

        return FLOOR_SHAPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle analogue joystick block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueJoystickBlockEntity joystick)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.openMenu(joystick, joystick::sendToMenu);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ct$startClientDrag(joystick);
        return InteractionResult.SUCCESS;
    }

    // Get the quiet use
    @Override
    public @Nullable InteractionResult quietUse(Player player, InteractionHand hand, BlockPos pos, BlockState state) {
        if (player.getItemInHand(hand).getItem() instanceof ContraptionNetworkLinkerItem) {
            return null;
        }
        if (player.isShiftKeyDown() || player.getItemInHand(hand).is(Tags.Items.TOOLS_WRENCH)) {
            return null;
        }

        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueJoystickBlockEntity joystick)) {
            return null;
        }

        if (ct$isClientDragging(pos)) {
            return InteractionResult.FAIL;
        }

        ct$startClientDrag(joystick);
        return InteractionResult.SUCCESS;
    }

    // Handle analogue joystick block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {

        if (player.isCrouching() && stack.getItem() instanceof NameTagItem && stack.has(DataComponents.CUSTOM_NAME)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof INamedBlockEntity named) {
                if (!level.isClientSide) {
                    net.minecraft.network.chat.Component name = stack.get(DataComponents.CUSTOM_NAME);
                    named.setCustomName(name != null ? name.getString() : null);
                    if (!player.isCreative()) stack.shrink(1);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Renamed to: " + (name != null ? name.getString() : "")), true);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        if (stack.is(Tags.Items.TOOLS_WRENCH)) {

            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        if (!player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueJoystickBlockEntity joystick)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            player.openMenu(joystick, joystick::sendToMenu);
        }
        return ItemInteractionResult.SUCCESS;
    }

    // Handle block attack
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!player.isShiftKeyDown()) {
            super.attack(state, level, pos, player);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueJoystickBlockEntity joystick)) {
            super.attack(state, level, pos, player);
            return;
        }

        if (level.isClientSide) {
            return;
        }

        joystick.resetInput();
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof INamedBlockEntity named) {
                net.minecraft.network.chat.Component name = stack.get(DataComponents.CUSTOM_NAME);
                named.setCustomName(name != null ? name.getString() : null);
            }
        }
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnalogueJoystickBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (blockEntityType == CTBlockEntities.ANALOGUE_JOYSTICK.get()) {

            return (BlockEntityTicker<T>) (BlockEntityTicker<AnalogueJoystickBlockEntity>) AnalogueJoystickBlockEntity::tick;
        }
        return null;
    }

    // Check if this has analog output signal
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    // Get the analog output signal
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueJoystickBlockEntity joystick)) {
            return 0;
        }
        int max = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            max = Math.max(max, joystick.getSignal(dir));
        }
        return max;
    }

    // Check if this is a signal source
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    // Get the signal
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AnalogueJoystickBlockEntity joystick) {
            return joystick.getSignal(dir);
        }
        return 0;
    }

    // Handle the remove event
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AnalogueJoystickBlockEntity joystick) {
                joystick.onDestroyed();
            }
            level.updateNeighborsAt(pos, state.getBlock());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                level.updateNeighborsAt(pos.relative(dir), state.getBlock());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Start the client drag
    private static void ct$startClientDrag(AnalogueJoystickBlockEntity joystick) {
        try {
            Class<?> handlerClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AnalogueJoystickClientHandler");
            handlerClass.getMethod("startDragging", AnalogueJoystickBlockEntity.class)
                    .invoke(null, joystick);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Check if this is client dragging
    private static boolean ct$isClientDragging(BlockPos pos) {
        try {
            Class<?> handlerClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AnalogueJoystickClientHandler");
            Object res = handlerClass.getMethod("isDragging", BlockPos.class).invoke(null, pos);
            return res instanceof Boolean b && b;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Get the x rotation degrees
    public static float getXRotationDegrees(BlockState state) {
        return switch (state.getValue(FACE)) {
            case WALL -> 90.0f;
            case CEILING -> 180.0f;
            default -> 0.0f;
        };
    }

    // Get the y rotation degrees
    public static float getYRotationDegrees(BlockState state) {
        return getYRotationDegrees(state.getValue(FACING));
    }

    // Get the y rotation degrees
    static float getYRotationDegrees(Direction facing) {
        return AnalogueJoystickOrientation.yRotationDegrees(facing);
    }

    // Get the logical facing
    public static Direction getLogicalFacing(BlockState state) {
        return getLogicalFacing(state.getValue(FACING));
    }

    // Get the logical facing
    static Direction getLogicalFacing(Direction facing) {
        return AnalogueJoystickOrientation.logicalFacing(facing);
    }

    // Get the codec
    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
