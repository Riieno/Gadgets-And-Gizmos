package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// Place and configure fueled thrusters with focused and compact variants
public class ThrusterBlock extends CTDirectionalBlock implements EntityBlock, SpecialBlockItemRequirement {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty FOCUSED = BooleanProperty.create("focused");
    public static final BooleanProperty SMALL = BooleanProperty.create("small");
    private static final VoxelShape FOCUSED_SHAPE = Shapes.block();
    private static final Map<Direction, VoxelShape> NORMAL_SHAPES = buildNormalShapes();
    private static final Map<Direction, VoxelShape> SMALL_SHAPES = buildSmallShapes();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster block
    public ThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, getStateDefinition().any().getValue(FACING))
                .setValue(POWERED, false)
                .setValue(FOCUSED, false)
                .setValue(SMALL, false));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (state.getValue(FOCUSED)) {
            return FOCUSED_SHAPE;
        }
        if (state.getValue(SMALL)) {
            return SMALL_SHAPES.getOrDefault(state.getValue(FACING), FOCUSED_SHAPE);
        }
        return NORMAL_SHAPES.getOrDefault(state.getValue(FACING), FOCUSED_SHAPE);
    }

    // Get the block support shape
    public VoxelShape getBlockSupportShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {

        return Shapes.block();
    }

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, FOCUSED, SMALL);
    }

    // Get the clone item stack
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (state.hasProperty(SMALL) && state.getValue(SMALL) && CTItems.SMALL_THRUSTER != null) {
            return new ItemStack(CTItems.SMALL_THRUSTER.get());
        }
        return CTItems.THRUSTER == null ? ItemStack.EMPTY : new ItemStack(CTItems.THRUSTER.get());
    }

    // Get the required items
    @Override
    public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
        ItemStack required = state.getValue(SMALL)
                ? new ItemStack(CTItems.SMALL_THRUSTER.get())
                : new ItemStack(CTItems.THRUSTER.get());
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, required);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return onSneakWrenched(state, ctx);
        }

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState rotated = getRotatedBlockState(state, ctx.getClickedFace());
        if (!rotated.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }

        KineticBlockEntity.switchToBlockState(level, pos, updateAfterWrenched(rotated, ctx));
        if (level.getBlockState(pos) != state) {
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // Get the state for placement
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) {
            return null;
        }
        boolean small = CTItems.SMALL_THRUSTER != null && ctx.getItemInHand().is(CTItems.SMALL_THRUSTER.get());
        state = state.setValue(SMALL, small);
        BlockPos placePos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        for (Direction dir : Direction.values()) {
            BlockState neighbor = level.getBlockState(placePos.relative(dir));
            if (neighbor.getBlock() instanceof ThrusterBearingBlock) {
                return state.setValue(FACING, dir.getOpposite());
            }
        }
        return state;
    }

    // Handle thruster block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ThrusterBlockEntity thruster)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.openMenu(thruster, thruster::sendToMenu);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    // Handle thruster block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ThrusterBlockEntity thruster)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)) {
            if (player.isShiftKeyDown()) {
                InteractionResult res = onSneakWrenched(state, new UseOnContext(player, hand, hit));
                return res.consumesAction()
                        ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.getItem() instanceof NameTagItem && stack.has(DataComponents.CUSTOM_NAME)) {
            if (!level.isClientSide) {
                net.minecraft.network.chat.Component name = stack.get(DataComponents.CUSTOM_NAME);
                ((INamedBlockEntity) thruster).setCustomName(name != null ? name.getString() : null);
                if (!player.isCreative()) stack.shrink(1);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Renamed to: " + (name != null ? name.getString() : "")), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.openMenu(thruster, thruster::sendToMenu);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (isCreateFilter(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (CTItems.THRUSTER_LENSE != null && stack.is(CTItems.THRUSTER_LENSE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.getItem() instanceof ProcessingUpgradeItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.getItem() instanceof DyeItem dyeItem) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            thruster.setBeamColor(dyeItem.getDyeColor().getTextColor());
            return ItemInteractionResult.SUCCESS;
        }

        if (thruster.isFocusedMode()) {
            if (FluidUtil.getFluidContained(stack).filter(fluid -> !fluid.isEmpty()).isPresent()
                    || ThrusterBlockEntity.canUseAsSolidFuel(stack)) {
                return ItemInteractionResult.SUCCESS;
            }
        }

        if (tryInsertFluidFuel(level, player, hand, stack, thruster)) {
            return ItemInteractionResult.SUCCESS;
        }

        if (ThrusterBlockEntity.canUseAsSolidFuel(stack)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            return thruster.tryInsertSolidFuel(player, stack)
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // Check if this is create filter
    public static boolean isCreateFilter(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"create".equals(itemId.getNamespace())) {
            return false;
        }
        String path = itemId.getPath();
        return "filter".equals(path) || "attribute_filter".equals(path) || "package_filter".equals(path);
    }

    // Build the normal shapes
    private static Map<Direction, VoxelShape> buildNormalShapes() {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, Shapes.or(
                box(2D, 2D, -8D, 14D, 14D, 8D),
                box(1D, 1D, 8D, 15D, 15D, 16D)).optimize());
        shapes.put(Direction.SOUTH, Shapes.or(
                box(2D, 2D, 8D, 14D, 14D, 24D),
                box(1D, 1D, 0D, 15D, 15D, 8D)).optimize());
        shapes.put(Direction.EAST, Shapes.or(
                box(8D, 2D, 2D, 24D, 14D, 14D),
                box(0D, 1D, 1D, 8D, 15D, 15D)).optimize());
        shapes.put(Direction.WEST, Shapes.or(
                box(-8D, 2D, 2D, 8D, 14D, 14D),
                box(8D, 1D, 1D, 16D, 15D, 15D)).optimize());
        shapes.put(Direction.UP, Shapes.or(
                box(2D, 8D, 2D, 14D, 24D, 14D),
                box(1D, 0D, 1D, 15D, 8D, 15D)).optimize());
        shapes.put(Direction.DOWN, Shapes.or(
                box(2D, -8D, 2D, 14D, 8D, 14D),
                box(1D, 8D, 1D, 15D, 16D, 15D)).optimize());
        return shapes;
    }

    // Build the small shapes
    private static Map<Direction, VoxelShape> buildSmallShapes() {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, Shapes.or(
                box(2D, 2D, 0D, 14D, 14D, 8D),
                box(1D, 1D, 8D, 15D, 15D, 16D)).optimize());
        shapes.put(Direction.SOUTH, Shapes.or(
                box(2D, 2D, 8D, 14D, 14D, 16D),
                box(1D, 1D, 0D, 15D, 15D, 8D)).optimize());
        shapes.put(Direction.EAST, Shapes.or(
                box(8D, 2D, 2D, 16D, 14D, 14D),
                box(0D, 1D, 1D, 8D, 15D, 15D)).optimize());
        shapes.put(Direction.WEST, Shapes.or(
                box(0D, 2D, 2D, 8D, 14D, 14D),
                box(8D, 1D, 1D, 16D, 15D, 15D)).optimize());
        shapes.put(Direction.UP, Shapes.or(
                box(2D, 8D, 2D, 14D, 16D, 14D),
                box(1D, 0D, 1D, 15D, 8D, 15D)).optimize());
        shapes.put(Direction.DOWN, Shapes.or(
                box(2D, 0D, 2D, 14D, 8D, 14D),
                box(1D, 8D, 1D, 15D, 16D, 15D)).optimize());
        return shapes;
    }

    // Try to insert fluid fuel
    private static boolean tryInsertFluidFuel(Level level, Player player, InteractionHand hand, ItemStack heldStack,
                                              ThrusterBlockEntity thruster) {
        if (!thruster.canAcceptFuel()) {
            return false;
        }

        java.util.Optional<FluidStack> maybeFluid = FluidUtil.getFluidContained(heldStack);
        if (maybeFluid.isEmpty() || maybeFluid.get().isEmpty()) {
            return false;
        }

        // Only drain fuel when the complete container fits
        FluidStack contained = maybeFluid.get();
        int possible = thruster.getFuelTank().fill(contained.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (possible < contained.getAmount()) {
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        java.util.Optional<IFluidHandlerItem> maybeHandler = FluidUtil.getFluidHandler(heldStack.copy());
        if (maybeHandler.isEmpty()) {
            return false;
        }

        IFluidHandlerItem handler = maybeHandler.get();
        FluidStack drained = handler.drain(contained.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < contained.getAmount()) {
            return false;
        }

        int filled = thruster.getFuelTank().fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled < drained.getAmount()) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, handler.getContainer());
        }

        thruster.setChanged();
        level.sendBlockUpdated(thruster.getBlockPos(), thruster.getBlockState(), thruster.getBlockState(), 3);
        return true;
    }

    // Handle the neighboring block change
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ThrusterBlockEntity thrusterBE) {
            thrusterBE.updateSignal();
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != powered) {
                level.setBlock(pos, state.setValue(POWERED, powered), 2);
            }
        }
    }

    // Handle the remove event
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ThrusterBlockEntity thruster) {

                for (int slot = ThrusterBlockEntity.SLOT_UPGRADE; slot <= ThrusterBlockEntity.SLOT_LENS; slot++) {
                    ItemStack extracted = thruster.extractInventorySlotForBlockRemoval(slot);
                    if (!extracted.isEmpty()) {
                        popResource(level, pos, extracted);
                    }
                }
            }
            level.updateNeighborsAt(pos, state.getBlock());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Handle block placement
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
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
        return new ThrusterBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (blockEntityType == CTBlockEntities.THRUSTER.get()) {

            return (BlockEntityTicker<T>) (BlockEntityTicker<ThrusterBlockEntity>) ThrusterBlockEntity::tickServer;
        }
        return null;
    }
}
