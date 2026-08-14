package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Handle Analogue Contraption Controller
public class AnalogueContraptionControllerBlock extends CTDirectionalBlock
        implements EntityBlock, BlockSubLevelCollisionShape, BlockSubLevelAssemblyListener {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DirectionProperty HORIZONTAL_FACING = DirectionProperty.create("horizontal_facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty EMBEDDED_SLAB = BooleanProperty.create("embedded_slab");
    public static final IntegerProperty MOUNT_OFFSET = IntegerProperty.create("mount_offset", 0, 15);
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 8.0D),
            box(0.0D, 0.0D, 8.0D, 16.0D, 10.0D, 16.0D),
            box(0.0D, 1.5201D, 6.9343D, 16.0D, 10.0192D, 15.4334D),
            box(9.0D, 3.0D, 4.0D, 13.0D, 8.0D, 13.0D),
            box(0.5D, 5.0D, 5.0D, 5.5D, 7.0D, 7.0D),
            box(9.5858D, 6.5858D, 3.0D, 12.4142D, 9.4142D, 14.0D),
            box(9.0D, 4.3806D, 0.2388D, 13.0D, 10.6892D, 4.3827D),
            box(8.0D, 4.11D, 0.5094D, 14.0D, 6.3404D, 2.1987D),
            box(14.0D, 4.11D, 0.5094D, 16.0D, 10.036D, 3.7294D),
            box(6.0D, 4.11D, 0.5094D, 8.0D, 10.036D, 3.7294D));
    private static final Map<Direction, Map<Direction, VoxelShape>> SHAPES_BY_ORIENTATION =
            ControllerShapeRotation.createOrientations(SHAPE_NORTH);
    // Cache each mounted collision shape for embedded slabs
    private static final Map<ControllerShapeKey, VoxelShape> CONTROLLER_SHAPES = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue contraption controller block
    public AnalogueContraptionControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(EMBEDDED_SLAB, false)
                .setValue(MOUNT_OFFSET, ControllerEmbeddedMount.PIXELS_PER_BLOCK / 2));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HORIZONTAL_FACING, EMBEDDED_SLAB, MOUNT_OFFSET);
    }

    // Get the current shape
    private static VoxelShape currentShape(BlockState state) {
        Direction surfaceNormal = state.getValue(FACING);
        Direction horizontalFacing = state.getValue(HORIZONTAL_FACING);
        boolean embedded = state.getValue(EMBEDDED_SLAB);
        int mountOffset = embedded ? state.getValue(MOUNT_OFFSET) : 0;
        ControllerShapeKey key = new ControllerShapeKey(
                surfaceNormal, horizontalFacing, embedded, mountOffset);
        return CONTROLLER_SHAPES.computeIfAbsent(key, AnalogueContraptionControllerBlock::createCurrentShape);
    }

    // Get the controller shape
    protected VoxelShape getControllerShape(BlockState state) {
        return currentShape(state);
    }

    // Create the current shape
    private static VoxelShape createCurrentShape(ControllerShapeKey key) {
        Direction surfaceNormal = key.surfaceNormal();
        Direction horizontalFacing = key.horizontalFacing();
        VoxelShape shape = SHAPES_BY_ORIENTATION
                .getOrDefault(surfaceNormal, SHAPES_BY_ORIENTATION.get(Direction.UP))
                .getOrDefault(horizontalFacing, SHAPE_NORTH);
        if (!key.embedded()) {
            return shape;
        }
        double offset = key.mountOffset() / (double) ControllerEmbeddedMount.PIXELS_PER_BLOCK;
        return ControllerShapeRotation.translate(
                shape,
                surfaceNormal.getStepX() * offset,
                surfaceNormal.getStepY() * offset,
                surfaceNormal.getStepZ() * offset);
    }

    // Store the controller shape key
    private record ControllerShapeKey(Direction surfaceNormal, Direction horizontalFacing,
                                      boolean embedded, int mountOffset) {
    }

    // Check if this is an embeddable half slab
    public static boolean isEmbeddableHalfSlab(BlockState state) {
        return state != null
                && (state.getBlock() instanceof ShipControlModuleBlock
                || state.hasProperty(BlockStateProperties.SLAB_TYPE)
                && state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE);
    }

    // Check if the missing slab half was clicked
    public static boolean isClickedMissingSlabHalf(BlockState state, Direction clickedFace) {
        if (!isEmbeddableHalfSlab(state)) {
            return false;
        }
        return missingSlabHalfFace(state) == clickedFace;
    }

    // Get the missing slab half face
    private static Direction missingSlabHalfFace(BlockState state) {
        if (state.getBlock() instanceof ShipControlModuleBlock) {
            return Direction.UP;
        }
        return state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM
                ? Direction.UP
                : Direction.DOWN;
    }

    // Get the controller
    private static @Nullable AnalogueContraptionControllerBlockEntity controller(BlockGetter level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller) {
            return controller;
        }
        return null;
    }

    // Get the combined shape
    private VoxelShape combinedShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        AnalogueContraptionControllerBlockEntity controller = controller(level, pos);
        VoxelShape controllerShape = getControllerShape(state);
        BlockState embeddedState = controller == null ? null : controller.getEmbeddedBlockState();
        if (embeddedState == null) {
            return controllerShape;
        }
        return Shapes.or(embeddedState.getShape(level, pos, ctx), controllerShape);
    }

    // Get the embedded support volume
    private static VoxelShape embeddedSupportVolume(BlockState state) {
        if (state == null || !state.hasProperty(EMBEDDED_SLAB) || !state.getValue(EMBEDDED_SLAB)) {
            return Shapes.empty();
        }
        Direction surfaceNormal = state.getValue(FACING);
        double offset = state.getValue(MOUNT_OFFSET) / (double) ControllerEmbeddedMount.PIXELS_PER_BLOCK;
        return switch (surfaceNormal) {
            case EAST -> Shapes.box(0.0D, 0.0D, 0.0D, offset, 1.0D, 1.0D);
            case WEST -> Shapes.box(1.0D - offset, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
            case UP -> Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, offset, 1.0D);
            case DOWN -> Shapes.box(0.0D, 1.0D - offset, 0.0D, 1.0D, 1.0D, 1.0D);
            case SOUTH -> Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, offset);
            case NORTH -> Shapes.box(0.0D, 0.0D, 1.0D - offset, 1.0D, 1.0D, 1.0D);
        };
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction surfaceNormal = ctx.getClickedFace();
        Direction horizontalFacing = surfaceNormal.getAxis().isHorizontal()
                ? surfaceNormal
                : ctx.getHorizontalDirection();
        int mountOffset = AnalogueContraptionControllerBlockItem.requestedEmbeddedMountOffset(ctx);
        return defaultBlockState()
                .setValue(FACING, surfaceNormal)
                .setValue(HORIZONTAL_FACING, horizontalFacing)
                .setValue(EMBEDDED_SLAB, mountOffset > 0)
                .setValue(MOUNT_OFFSET, mountOffset > 0
                        ? mountOffset
                        : ControllerEmbeddedMount.PIXELS_PER_BLOCK / 2);
    }

    // Rotate the analogue contraption controller block
    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state
                .setValue(FACING, rotation.rotate(state.getValue(FACING)))
                .setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    // Mirror the analogue contraption controller block
    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state
                .setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(HORIZONTAL_FACING, mirror.mirror(state.getValue(HORIZONTAL_FACING)));
    }

    // Get the rotated block state
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction surfaceNormal = originalState.getValue(FACING);
        if (surfaceNormal.getAxis().isVertical()) {
            Direction.Axis rotationAxis = surfaceNormal.getAxis();
            return originalState.setValue(HORIZONTAL_FACING,
                    originalState.getValue(HORIZONTAL_FACING).getClockWise(rotationAxis));
        }
        Direction rotatedSurface = surfaceNormal.getClockWise(Direction.Axis.Y);
        return originalState
                .setValue(FACING, rotatedSurface)
                .setValue(HORIZONTAL_FACING, rotatedSurface);
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {

        return combinedShape(state, level, pos, ctx);
    }

    // Get the collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return combinedShape(state, level, pos, ctx);
    }

    // Get the interaction shape
    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return combinedShape(state, level, pos, CollisionContext.empty());
    }

    // Get the sublevel collision shape
    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return Shapes.or(embeddedSupportVolume(state), getControllerShape(state));
    }

    // Get the drops
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller
                && !controller.isRestoringEmbeddedBlock()) {
            BlockState embeddedState = controller.getEmbeddedBlockState();
            if (embeddedState != null) {
                ItemStack embeddedDrop = new ItemStack(embeddedState.getBlock());
                if (!embeddedDrop.isEmpty() && !embeddedDrop.is(Items.AIR)) {
                    drops.add(embeddedDrop);
                }
                drops.addAll(controller.getEmbeddedConsumedItems());
            }
        }
        return drops;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle analogue contraption controller block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueContraptionControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.openMenu(controller, controller::sendToMenu);
            }
            return InteractionResult.SUCCESS;
        }

        if (level.isClientSide) {

            ct$toggleClientInteractMode(controller);
        }
        return InteractionResult.SUCCESS;
    }

    // Handle analogue contraption controller block use on the target
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

        if (stack.is(Items.SLIME_BALL)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueContraptionControllerBlockEntity controller)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.getItem() instanceof PortableContraptionControllerItem portable) {
            return portable.useOnControllerBlock(stack, state, level, pos, player, hand, controller);
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.openMenu(controller, controller::sendToMenu);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (level.isClientSide) {

            ct$toggleClientInteractMode(controller);
        }
        return ItemInteractionResult.SUCCESS;
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AnalogueContraptionControllerBlockEntity controller)) {
            return;
        }

        controller.setStoredTargets(AnalogueContraptionControllerBlockItem.getStoredTargets(stack, level));
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            net.minecraft.network.chat.Component name = stack.get(DataComponents.CUSTOM_NAME);
            controller.setCustomName(name != null ? name.getString() : null);
        }
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnalogueContraptionControllerBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (!level.isClientSide && blockEntityType == CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER.get()) {

            return (BlockEntityTicker<T>) (BlockEntityTicker<AnalogueContraptionControllerBlockEntity>) AnalogueContraptionControllerBlockEntity::tickServer;
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
        if (!(blockEntity instanceof AnalogueContraptionControllerBlockEntity controller)) {
            return 0;
        }
        int max = 0;
        for (Direction dir : Direction.values()) {
            max = Math.max(max, controller.getLocalOutputSignal(dir));
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
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller) {
            return controller.getLocalOutputSignal(dir);
        }
        return 0;
    }

    // Handle the player destroying the controller
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller
                && controller.getEmbeddedBlockState() != null) {
            return controller.restoreEmbeddedBlock();
        }
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller) {
            controller.markDestructiveRemoval();
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    // Handle the remove event
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller) {
                if (controller.isDestructiveRemovalPending()) {
                    controller.onDestroyed();
                } else {
                    controller.onExternalRelocation();
                }
            }
            level.updateNeighborsAt(pos, state.getBlock());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = originLevel.getBlockEntity(oldPos);
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller) {
            controller.beginAssemblyTransfer(oldPos, newPos);
        }
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        BlockEntity blockEntity = resultingLevel.getBlockEntity(newPos);
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller) {
            controller.finishAssemblyTransfer();
        }
    }

    // Toggle client interaction mode
    private static void ct$toggleClientInteractMode(AnalogueContraptionControllerBlockEntity controller) {
        try {
            Class<?> handlerClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
            handlerClass.getMethod("toggleInteractMode", AnalogueContraptionControllerBlockEntity.class)
                    .invoke(null, controller);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
