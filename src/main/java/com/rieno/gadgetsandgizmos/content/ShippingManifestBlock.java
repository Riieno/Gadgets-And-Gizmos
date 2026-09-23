package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.compat.createdieselgenerators.CreateDieselGeneratorsManifestCompat;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingManifestOpenPayload;
import com.rieno.gadgetsandgizmos.util.ThrusterFuelData;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import com.simibubi.create.foundation.block.IBE;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// Place and configure face-mounted cargo manifests without losing their container address
public class ShippingManifestBlock extends FaceAttachedHorizontalDirectionalBlock
        implements IBE<ShippingManifestBlockEntity>, IWrenchable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int ENTRIES_PER_PAGE = 7;
    private static final int MAX_MIXED_FLUID_ROWS = 4;
    private static final int GRAPH_WIDTH = 16;
    private static final int COMPACT_GRAPH_WIDTH = 10;
    private static final int MAX_PAGES = 51;
    public static final MapCodec<ShippingManifestBlock> CODEC = simpleCodec(ShippingManifestBlock::new);
    public static final BooleanProperty SHOW_BLANK = BooleanProperty.create("show_blank");
    public static final EnumProperty<DyeColor> MANIFEST_COLOR = EnumProperty.create("manifest_color", DyeColor.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping manifest block
    public ShippingManifestBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(SHOW_BLANK, false)
                .setValue(MANIFEST_COLOR, DyeColor.GREEN));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, SHOW_BLANK, MANIFEST_COLOR);
    }

    // Get the state for placement
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) {
            return null;
        }
        if (state.getValue(FACE) != AttachFace.WALL) {
            state = state.setValue(FACING, state.getValue(FACING).getOpposite());
        }
        BlockPos targetPos = attachedTargetPos(ctx.getClickedPos(), state);
        Direction targetSide = getConnectedDirection(state);
        IItemHandler itemHandler = findItemHandler(ctx.getLevel(), targetPos, targetSide);
        IFluidHandler fluidHandler = findFluidHandler(ctx.getLevel(), targetPos, targetSide);
        IEnergyStorage energyHandler = findEnergyHandler(ctx.getLevel(), targetPos, targetSide);
        LongEnergyStorage longEnergyHandler = findLongEnergyHandler(ctx.getLevel(), targetPos);
        if (itemHandler == null && fluidHandler == null && energyHandler == null && longEnergyHandler == null) {
            return null;
        }
        return state.setValue(SHOW_BLANK, fluidHandler != null || energyHandler != null);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle shipping manifest block use without an item
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos targetPos = attachedTargetPos(pos, state);
        Direction targetSide = getConnectedDirection(state);
        IItemHandler itemHandler = findItemHandler(level, targetPos, targetSide);
        ShippingManifestBlockEntity manifest = level.getBlockEntity(pos) instanceof ShippingManifestBlockEntity blockEntity
                ? blockEntity
                : null;
        IFluidHandler fluidHandler = resolveFluidHandler(level, targetPos, targetSide,
                manifest != null && manifest.isCombinedManifest());
        IEnergyStorage energyHandler = findEnergyHandler(level, targetPos, targetSide);
        LongEnergyStorage longEnergyHandler = findLongEnergyHandler(level, targetPos);
        if (itemHandler == null && fluidHandler == null && energyHandler == null && longEnergyHandler == null) {
            return InteractionResult.PASS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new ShippingManifestOpenPayload(pos, manifest == null ? 0 : manifest.resourceUses(),
                            manifest == null ? 0 : manifest.availableResourceUses(), createClipboardContent(
                            itemHandler, fluidHandler, energyHandler, longEnergyHandler)));
        }
        return InteractionResult.CONSUME;
    }

    // Handle shipping manifest block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ShippingManifestBlockEntity manifest)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        if (stack.is(Items.GLOW_INK_SAC)) {
            if (!level.isClientSide && manifest.setManifestGlowing(true) && !player.isCreative()) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.getItem() instanceof DyeItem dyeItem) {
            DyeColor dyeColor = dyeItem.getDyeColor();
            if (!level.isClientSide) {
                boolean stateChanged = state.getValue(MANIFEST_COLOR) != dyeColor;
                boolean colorChanged = manifest.setManifestColor(dyeColor.getTextColor());
                if (stateChanged) {
                    level.setBlock(pos, state.setValue(MANIFEST_COLOR, dyeColor), Block.UPDATE_CLIENTS);
                }
                if ((stateChanged || colorChanged) && !player.isCreative()) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos targetPos = attachedTargetPos(pos, state);
        if (!CreateDieselGeneratorsManifestCompat.isDistillationTank(level, targetPos)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ShippingManifestBlockEntity manifest) {
            boolean combined = manifest.toggleCombinedManifest();
            Player player = ctx.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.translatable(combined
                        ? "createthrusters.shipping_manifest.combined.enabled"
                        : "createthrusters.shipping_manifest.combined.disabled"), true);
            }
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // Handle crouching wrench use
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext ctx) {
        return IWrenchable.super.onSneakWrenched(state, ctx);
    }

    // Create the clipboard content
    private static ClipboardContent createClipboardContent(@Nullable IItemHandler itemHandler,
            @Nullable IFluidHandler fluidHandler, @Nullable IEnergyStorage energyHandler,
            @Nullable LongEnergyStorage longEnergyHandler) {
        List<ManifestEntry> items = itemHandler == null ? List.of() : collectItems(itemHandler);
        FluidContents fluids = fluidHandler == null ? FluidContents.EMPTY : collectFluids(fluidHandler);
        List<List<ClipboardEntry>> pages;
        if (itemHandler != null && fluidHandler != null) {
            pages = createMixedPages(items, fluids);
        } else if (fluidHandler != null) {
            pages = createFluidPages(fluids);
        } else {
            pages = createItemPages(items);
        }
        if (energyHandler != null || longEnergyHandler != null) {
            long stored = longEnergyHandler == null ? energyHandler.getEnergyStored()
                    : longEnergyHandler.getEnergyStored();
            long capacity = longEnergyHandler == null ? energyHandler.getMaxEnergyStored()
                    : longEnergyHandler.getMaxEnergyStored();
            pages = new ArrayList<>(pages);
            pages.add(List.of(new ClipboardEntry(false, Component.translatable(
                    "gui.createthrusters.shipping_manifest.energy",
                    stored, capacity, formatPercent(stored, capacity)))));
        }
        return new ClipboardContent(ClipboardOverrides.ClipboardType.WRITTEN, pages, true);
    }

    // Create the item pages
    private static List<List<ClipboardEntry>> createItemPages(List<ManifestEntry> items) {
        if (items.isEmpty()) {
            return List.of(List.of(textEntry("gui.createthrusters.shipping_manifest.empty_items")));
        }
        int maxItemTypes = ENTRIES_PER_PAGE * MAX_PAGES;
        int visibleTypes = items.size() > maxItemTypes ? maxItemTypes - 1 : items.size();
        List<List<ClipboardEntry>> pages = new ArrayList<>();
        List<ClipboardEntry> page = new ArrayList<>(ENTRIES_PER_PAGE);
        for (int idx = 0; idx < visibleTypes; idx++) {
            page.add(itemEntry(items.get(idx)));
            if (page.size() == ENTRIES_PER_PAGE) {
                pages.add(page);
                page = new ArrayList<>(ENTRIES_PER_PAGE);
            }
        }
        if (!page.isEmpty()) {
            pages.add(page);
        }
        if (items.size() > visibleTypes) {
            List<ClipboardEntry> finalPage = pages.get(pages.size() - 1);
            if (finalPage.size() == ENTRIES_PER_PAGE) {
                finalPage = new ArrayList<>(ENTRIES_PER_PAGE);
                pages.add(finalPage);
            }
            finalPage.add(new ClipboardEntry(false, Component.translatable(
                    "gui.createthrusters.shipping_manifest.overflow", items.size() - visibleTypes)));
        }
        return pages;
    }

    // Create the mixed pages
    private static List<List<ClipboardEntry>> createMixedPages(List<ManifestEntry> items, FluidContents fluids) {
        List<ClipboardEntry> fluidFooter = compactFluidEntries(fluids);
        int itemsPerPage = Math.max(1, ENTRIES_PER_PAGE - fluidFooter.size());
        int maxItemTypes = itemsPerPage * MAX_PAGES;
        int visibleTypes = items.size() > maxItemTypes ? maxItemTypes - 1 : items.size();
        int pageCount = Math.max(1, (visibleTypes + itemsPerPage - 1) / itemsPerPage);
        List<List<ClipboardEntry>> pages = new ArrayList<>(pageCount);

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            List<ClipboardEntry> page = new ArrayList<>(ENTRIES_PER_PAGE);
            int start = pageIndex * itemsPerPage;
            int end = Math.min(visibleTypes, start + itemsPerPage);
            for (int itemIndex = start; itemIndex < end; itemIndex++) {
                page.add(itemEntry(items.get(itemIndex)));
            }
            if (pageIndex == pageCount - 1 && items.size() > visibleTypes) {
                page.add(new ClipboardEntry(false, Component.translatable(
                        "gui.createthrusters.shipping_manifest.overflow", items.size() - visibleTypes)));
            }
            page.addAll(fluidFooter);
            pages.add(page);
        }
        return pages;
    }

    // Get the compact fluid entries
    private static List<ClipboardEntry> compactFluidEntries(FluidContents fluids) {
        if (fluids.entries.isEmpty()) {
            return List.of(textEntry("gui.createthrusters.shipping_manifest.empty_fluids"));
        }
        int visibleFluids = Math.min(fluids.entries.size(), MAX_MIXED_FLUID_ROWS);
        if (fluids.entries.size() > MAX_MIXED_FLUID_ROWS) {
            visibleFluids--;
        }
        List<ClipboardEntry> entries = new ArrayList<>(MAX_MIXED_FLUID_ROWS);
        for (int idx = 0; idx < visibleFluids; idx++) {
            FluidEntry fluid = fluids.entries.get(idx);
            entries.add(new ClipboardEntry(false, Component.translatable(
                    "gui.createthrusters.shipping_manifest.fluid_compact",
                    fluid.stack.getHoverName(),
                    buildFillGraph(fluid.amount, fluids.totalCapacity, COMPACT_GRAPH_WIDTH),
                    formatPercent(fluid.amount, fluids.totalCapacity))));
        }
        if (fluids.entries.size() > visibleFluids) {
            entries.add(new ClipboardEntry(false, Component.translatable(
                    "gui.createthrusters.shipping_manifest.fluid_overflow", fluids.entries.size() - visibleFluids)));
        }
        return entries;
    }

    // Create the fluid pages
    private static List<List<ClipboardEntry>> createFluidPages(FluidContents fluids) {
        if (fluids.entries.isEmpty()) {
            return List.of(List.of(
                    textEntry("gui.createthrusters.shipping_manifest.empty_fluids"),
                    new ClipboardEntry(false, Component.literal(buildFillGraph(0L, fluids.totalCapacity, GRAPH_WIDTH)
                            + " " + formatPercent(0L, fluids.totalCapacity)))));
        }

        int visibleFluids = Math.min(fluids.entries.size(), MAX_PAGES);
        List<List<ClipboardEntry>> pages = new ArrayList<>(visibleFluids);
        for (int idx = 0; idx < visibleFluids; idx++) {
            FluidEntry fluid = fluids.entries.get(idx);
            ThrusterFuelData.FuelProfile profile = ThrusterFuelData.getProfile(fluid.stack.getFluid());
            List<ClipboardEntry> page = new ArrayList<>(ENTRIES_PER_PAGE);
            page.add(new ClipboardEntry(false, Component.translatable(
                    "gui.createthrusters.shipping_manifest.fluid_name", fluid.stack.getHoverName())));
            page.add(new ClipboardEntry(false, Component.translatable(
                    "gui.createthrusters.shipping_manifest.fluid_fill",
                    buildFillGraph(fluid.amount, fluids.totalCapacity, GRAPH_WIDTH),
                    formatPercent(fluid.amount, fluids.totalCapacity))));
            page.add(new ClipboardEntry(false, Component.translatable(
                    "gui.createthrusters.shipping_manifest.fluid_amount", fluid.amount, fluids.totalCapacity)));
            if (profile == null) {
                page.add(textEntry("gui.createthrusters.shipping_manifest.not_thruster_fuel"));
            } else {
                page.add(new ClipboardEntry(false, Component.translatable(
                        "gui.createthrusters.shipping_manifest.fluid_burn_time",
                        formatNumber(profile.burnTimeTicksPerBucket()))));
                page.add(new ClipboardEntry(false, Component.translatable(
                        "gui.createthrusters.shipping_manifest.fluid_weight", formatNumber(profile.weight()))));
                page.add(new ClipboardEntry(false, Component.translatable(
                        "gui.createthrusters.shipping_manifest.fluid_propulsion",
                        formatNumber(profile.propulsionMultiplier()))));
            }
            pages.add(page);
        }
        return pages;
    }

    // Get the item entry
    private static ClipboardEntry itemEntry(ManifestEntry item) {
        int displayAmount = (int) Math.min(Integer.MAX_VALUE, item.amount);
        return new ClipboardEntry(false, Component.translatable(
                "gui.createthrusters.shipping_manifest.entry", item.amount, item.stack.getHoverName()))
                .displayItem(item.stack, displayAmount);
    }

    // Get the text entry
    private static ClipboardEntry textEntry(String translationKey) {
        return new ClipboardEntry(false, Component.translatable(translationKey));
    }

    // Build the fill graph
    static String buildFillGraph(long amount, long capacity, int width) {
        double ratio = capacity <= 0L ? 0.0D : Math.min(1.0D, Math.max(0.0D, (double) amount / capacity));
        int filled = (int) Math.round(ratio * width);
        return "[" + "█".repeat(filled) + "░".repeat(width - filled) + "]";
    }

    // Format the percent
    static String formatPercent(long amount, long capacity) {
        double percent = capacity <= 0L ? 0.0D : Math.min(100.0D, Math.max(0.0D, amount * 100.0D / capacity));
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    // Format the number
    static String formatNumber(double val) {
        return String.format(Locale.ROOT, "%.3f", val).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // Get the inspect items
    static ItemContents inspectItems(@Nullable IItemHandler handler) {
        if (handler == null) {
            return ItemContents.EMPTY;
        }
        List<ManifestEntry> items = new ArrayList<>();
        long totalAmount = 0L;
        long totalCapacity = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            totalCapacity += Math.max(0, handler.getSlotLimit(slot));
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            totalAmount += stack.getCount();
            ManifestEntry matching = null;
            for (ManifestEntry existing : items) {
                if (ItemStack.isSameItemSameComponents(existing.stack, stack)) {
                    matching = existing;
                    break;
                }
            }
            if (matching == null) {
                ItemStack icon = stack.copy();
                icon.setCount(1);
                items.add(new ManifestEntry(icon, stack.getCount()));
            } else {
                matching.amount += stack.getCount();
            }
        }
        items.sort(Comparator.comparing(entry -> entry.stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return new ItemContents(List.copyOf(items), totalAmount, totalCapacity);
    }

    // Find the energy handler
    public static @Nullable IEnergyStorage findEnergyHandler(
            Level level, BlockPos pos, Direction side
    ) {
        return level == null ? null : level.getCapability(
                Capabilities.EnergyStorage.BLOCK, pos, side);
    }

    // Find exact FE storage exposed by the target block entity
    public static @Nullable LongEnergyStorage findLongEnergyHandler(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof LongEnergyStorage storage ? storage : null;
    }

    // Collect the items
    static List<ManifestEntry> collectItems(IItemHandler handler) {
        return inspectItems(handler).entries();
    }

    // Collect the fluids
    static FluidContents collectFluids(IFluidHandler handler) {
        List<FluidEntry> fluids = new ArrayList<>();
        long totalCapacity = 0L;
        long totalAmount = 0L;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            totalCapacity += Math.max(0, handler.getTankCapacity(tank));
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }
            totalAmount += stack.getAmount();
            FluidEntry matching = null;
            for (FluidEntry existing : fluids) {
                if (FluidStack.isSameFluidSameComponents(existing.stack, stack)) {
                    matching = existing;
                    break;
                }
            }
            if (matching == null) {
                fluids.add(new FluidEntry(stack.copyWithAmount(1), stack.getAmount()));
            } else {
                matching.amount += stack.getAmount();
            }
        }
        fluids.sort(Comparator.comparing(entry -> entry.stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return new FluidContents(List.copyOf(fluids), totalAmount, totalCapacity);
    }

    // Handle the manifest entry
    static final class ManifestEntry {
        // Stack
        final ItemStack stack;
        // Current amount
        long amount;

        // Initialize the manifest entry
        private ManifestEntry(ItemStack stack, long amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    // Store the item contents
    record ItemContents(List<ManifestEntry> entries, long totalAmount, long totalCapacity) {
        static final ItemContents EMPTY = new ItemContents(List.of(), 0L, 0L);
    }

    // Handle the fluid contents
    static final class FluidContents {
        static final FluidContents EMPTY = new FluidContents(List.of(), 0L, 0L);
        // Tracked entries
        final List<FluidEntry> entries;
        // Total amount
        final long totalAmount;
        // Total capacity
        final long totalCapacity;

        // Initialize the fluid contents
        private FluidContents(List<FluidEntry> entries, long totalAmount, long totalCapacity) {
            this.entries = entries;
            this.totalAmount = totalAmount;
            this.totalCapacity = totalCapacity;
        }
    }

    // Handle the fluid entry
    static final class FluidEntry {
        // Stack
        final FluidStack stack;
        // Current amount
        long amount;

        // Initialize the fluid entry
        private FluidEntry(FluidStack stack, long amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    // Check if this can survive
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.getBlockState(attachedTargetPos(pos, state)).canBeReplaced();
    }

    // Get the shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> AllShapes.CLIPBOARD_FLOOR.get(state.getValue(FACING));
            case CEILING -> AllShapes.CLIPBOARD_CEILING.get(state.getValue(FACING));
            case WALL -> AllShapes.CLIPBOARD_WALL.get(state.getValue(FACING));
        };
    }

    // Get the attached target pos
    public static BlockPos attachedTargetPos(BlockPos manifestPos, BlockState state) {
        return manifestPos.relative(getConnectedDirection(state).getOpposite());
    }

    // Get the attached target side
    public static Direction attachedTargetSide(BlockState state) {
        return getConnectedDirection(state);
    }

    // Find the item handler
    @Nullable
    public static IItemHandler findItemHandler(Level level, BlockPos targetPos, Direction targetSide) {
        BlockState targetState = level.getBlockState(targetPos);
        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState,
                targetBlockEntity, targetSide);
        if (handler == null) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetState,
                    targetBlockEntity, null);
        }
        return handler;
    }

    // Find the fluid handler
    @Nullable
    public static IFluidHandler findFluidHandler(Level level, BlockPos targetPos, Direction targetSide) {
        BlockState targetState = level.getBlockState(targetPos);
        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetState,
                targetBlockEntity, targetSide);
        if (handler == null) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetState,
                    targetBlockEntity, null);
        }
        return handler;
    }

    // Resolve the fluid handler
    @Nullable
    public static IFluidHandler resolveFluidHandler(Level level, BlockPos targetPos, Direction targetSide,
            boolean combinedManifest) {
        if (combinedManifest) {
            IFluidHandler combinedHandler =
                    CreateDieselGeneratorsManifestCompat.findCombinedHandler(level, targetPos);
            if (combinedHandler != null) {
                return combinedHandler;
            }
        }
        return findFluidHandler(level, targetPos, targetSide);
    }

    // Get the block entity class
    @Override
    public Class<ShippingManifestBlockEntity> getBlockEntityClass() {
        return ShippingManifestBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends ShippingManifestBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SHIPPING_MANIFEST.get();
    }

    // Get the codec
    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
