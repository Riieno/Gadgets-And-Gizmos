package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Define one named cargo endpoint and expose its live contents to schedules and graphs
public class ShippingManifestBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, AdvancedGraphDataProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final int COMPACT_ITEM_TYPES = 4;
    private static final int EXPANDED_ITEM_TYPES = 12;
    public static final int DISPLAY_ROWS = 7;
    private static final String TAG_ITEM_TYPES = "ItemTypes";
    private static final String TAG_FLUID_TYPES = "FluidTypes";
    private static final String TAG_HAS_FLUID_HANDLER = "HasFluidHandler";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ENERGY_CAPACITY = "EnergyCapacity";
    private static final String TAG_COMBINED_MANIFEST = "CombinedManifest";
    private static final String TAG_MANUAL_GRAPH_TEXT = "ManualGraphText";
    private static final String TAG_MANIFEST_COLOR = "ManifestColor";
    private static final String TAG_MANIFEST_GLOWING = "ManifestGlowing";
    private static final String TAG_RESOURCE_USES = "ResourceUses";
    private static final String TAG_RESOURCE_USES_CONFIGURED = "ResourceUsesConfigured";
    private static final String TAG_CONTAINER_LOCKED = "ContainerLocked";
    private static final String TAG_CONTAINER_LOCK_FILTERS = "ContainerLockFilters";
    private static final String TAG_PREVIEW = "Preview";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_TEXT = "Text";
    public static final int DEFAULT_MANIFEST_COLOR = 0x2F8F4E;
    public static final int USE_ITEMS = 1;
    public static final int USE_FLUIDS = 1 << 1;
    public static final int USE_FUEL = 1 << 2;
    public static final int USE_ENERGY = 1 << 3;
    private static final int ALL_RESOURCE_USES = USE_ITEMS | USE_FLUIDS | USE_FUEL | USE_ENERGY;
    private static final int MAXIMUM_CONTAINER_LOCK_FILTERS = 9;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked display entries
    private List<DisplayEntry> displayEntries = List.of();
    // Current item types
    private int itemTypes;
    // Current fluid types
    private int fluidTypes;
    // Tracks whether fluid handler is available
    private boolean hasFluidHandler;
    // Current energy
    private long energy;
    // Current energy capacity
    private long energyCapacity;
    // Tracks whether combined manifest is set
    private boolean combinedManifest;
    // Current manual graph text
    private String manualGraphText = "";
    // Client scroll offset
    private int clientScrollOffset;
    // Current manifest color
    private int manifestColor = DEFAULT_MANIFEST_COLOR;
    // Tracks whether manifest glowing is set
    private boolean manifestGlowing;
    // Selected worker resource uses
    private int resourceUses;
    // Tracks whether resource uses were selected by the player
    private boolean resourceUsesConfigured;
    // Tracks whether the attached container accepts only the configured item filters
    private boolean containerLocked;
    // Item filters accepted by the attached locked container
    private List<ItemStack> containerLockFilters = List.of();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping manifest
    public ShippingManifestBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SHIPPING_MANIFEST.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the shipping manifest
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        syncBlockStateColor();
        if (level.getGameTime() % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }
        refreshPreview();
    }

    // Refresh the preview
    private void refreshPreview() {
        if (!manualGraphText.isBlank()) {
            applyManualGraphText(manualGraphText);
            return;
        }
        BlockState state = getBlockState();
        BlockPos targetPos = ShippingManifestBlock.attachedTargetPos(worldPosition, state);
        Direction targetSide = ShippingManifestBlock.attachedTargetSide(state);
        IItemHandler itemHandler = ShippingManifestBlock.findItemHandler(level, targetPos, targetSide);
        IFluidHandler fluidHandler = ShippingManifestBlock.resolveFluidHandler(level, targetPos, targetSide,
                combinedManifest);
        IEnergyStorage energyHandler = ShippingManifestBlock.findEnergyHandler(
                level, targetPos, targetSide);
        LongEnergyStorage longEnergyHandler = ShippingManifestBlock.findLongEnergyHandler(level, targetPos);
        if ((fluidHandler != null || energyHandler != null)
                && !state.getValue(ShippingManifestBlock.SHOW_BLANK)) {
            level.setBlock(worldPosition, state.setValue(ShippingManifestBlock.SHOW_BLANK, true),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        List<ShippingManifestBlock.ManifestEntry> items = itemHandler == null
                ? List.of()
                : ShippingManifestBlock.collectItems(itemHandler);
        ShippingManifestBlock.FluidContents fluids = fluidHandler == null
                ? ShippingManifestBlock.FluidContents.EMPTY
                : ShippingManifestBlock.collectFluids(fluidHandler);
        long nextEnergy = longEnergyHandler != null ? longEnergyHandler.getEnergyStored()
                : energyHandler == null ? 0L : energyHandler.getEnergyStored();
        long nextEnergyCapacity = longEnergyHandler != null ? longEnergyHandler.getMaxEnergyStored()
                : energyHandler == null ? 0L : energyHandler.getMaxEnergyStored();
        int nextResourceUses = resourceUsesConfigured ? resourceUses
                : detectedResourceUses(itemHandler, fluidHandler, energyHandler, longEnergyHandler);

        int nextItemTypes = items.size();
        int nextFluidTypes = fluids.entries.size();
        List<DisplayEntry> nextDisplayEntries = new ArrayList<>(nextItemTypes + Math.max(1, nextFluidTypes));
        for (int idx = 0; idx < nextItemTypes; idx++) {
            ShippingManifestBlock.ManifestEntry entry = items.get(idx);
            nextDisplayEntries.add(new DisplayEntry(entry.stack.copy(), entry.amount, ""));
        }
        for (ShippingManifestBlock.FluidEntry fluid : fluids.entries) {
            if (itemHandler == null) {
                addDetailedFluidDisplay(nextDisplayEntries, fluid, fluids.totalCapacity);
            } else {
                nextDisplayEntries.add(new DisplayEntry(ItemStack.EMPTY, 0L,
                        fluid.stack.getHoverName().getString() + " "
                                + ShippingManifestBlock.buildFillGraph(fluid.amount, fluids.totalCapacity, 10) + " "
                                + ShippingManifestBlock.formatPercent(fluid.amount, fluids.totalCapacity)));
            }
        }
        if (fluidHandler != null && fluids.entries.isEmpty()) {
            nextDisplayEntries.add(new DisplayEntry(ItemStack.EMPTY, 0L,
                    Component.translatable("gui.createthrusters.shipping_manifest.empty_fluids").getString() + " "
                            + compactFluidFill(0L, fluids.totalCapacity)));
        }
        if (energyHandler != null || longEnergyHandler != null) {
            nextDisplayEntries.add(textDisplay(Component.translatable(
                    "gui.createthrusters.shipping_manifest.energy",
                    nextEnergy, nextEnergyCapacity,
                    ShippingManifestBlock.formatPercent(nextEnergy, nextEnergyCapacity))));
        }
        boolean nextHasFluidHandler = hasFluidHandler || fluidHandler != null;
        if (itemTypes == nextItemTypes && fluidTypes == nextFluidTypes
                && hasFluidHandler == nextHasFluidHandler
                && energy == nextEnergy && energyCapacity == nextEnergyCapacity
                && resourceUses == nextResourceUses
                && entriesMatch(displayEntries, nextDisplayEntries)) {
            return;
        }

        itemTypes = nextItemTypes;
        fluidTypes = nextFluidTypes;
        hasFluidHandler = nextHasFluidHandler;
        energy = nextEnergy;
        energyCapacity = nextEnergyCapacity;
        resourceUses = nextResourceUses;
        displayEntries = List.copyOf(nextDisplayEntries);
        setChanged();
        sendData();
    }

    // Add the detailed fluid display
    private static void addDetailedFluidDisplay(List<DisplayEntry> entries, ShippingManifestBlock.FluidEntry fluid,
            long totalCapacity) {
        entries.add(textDisplay(Component.translatable(
                "gui.createthrusters.shipping_manifest.fluid_name", fluid.stack.getHoverName())));
        entries.add(textDisplay(Component.literal(compactFluidFill(fluid.amount, totalCapacity))));
    }

    // Get the compact fluid fill
    private static String compactFluidFill(long amount, long capacity) {
        String pct = ShippingManifestBlock.formatPercent(amount, capacity);
        int graphWidth = Math.max(3, 11 - pct.length());
        return ShippingManifestBlock.buildFillGraph(amount, capacity, graphWidth) + " " + pct;
    }

    // Get the text display
    private static DisplayEntry textDisplay(Component text) {
        return new DisplayEntry(ItemStack.EMPTY, 0L, text.getString());
    }

    // Apply the manual graph text
    private void applyManualGraphText(String text) {
        String normalized = text == null ? "" : text;
        List<DisplayEntry> entries = new ArrayList<>();
        for (String line : normalized.split("\\R", -1)) {
            if (!line.isEmpty()) {
                entries.add(new DisplayEntry(ItemStack.EMPTY, 0L, line));
            }
        }
        itemTypes = 0;
        fluidTypes = 0;
        energy = 0;
        energyCapacity = 0;
        displayEntries = List.copyOf(entries);
        setChanged();
        sendData();
    }

    // Refresh the now
    public void refreshNow() {
        if (level != null && !level.isClientSide) {
            refreshPreview();
        }
    }

    // Toggle combined manifest mode
    public boolean toggleCombinedManifest() {
        combinedManifest = !combinedManifest;
        setChanged();
        sendData();
        refreshNow();
        return combinedManifest;
    }

    // Check if this is combined manifest
    public boolean isCombinedManifest() {
        return combinedManifest;
    }

    // Get the selected worker resource uses
    public int resourceUses() {
        return (resourceUsesConfigured ? resourceUses : detectedDefaultResourceUses())
                & ALL_RESOURCE_USES;
    }

    // Check whether workers may use items through this manifest
    public boolean usesItems() {
        return (resourceUses() & USE_ITEMS) != 0;
    }

    // Check whether workers may use ordinary fluids through this manifest
    public boolean usesFluids() {
        return (resourceUses() & USE_FLUIDS) != 0;
    }

    // Check whether workers may use fuel through this manifest
    public boolean usesFuel() {
        return (resourceUses() & USE_FUEL) != 0;
    }

    // Check whether workers may use FE through this manifest
    public boolean usesEnergy() {
        return (resourceUses() & USE_ENERGY) != 0;
    }

    // Get the resource uses available from the attached storage
    public int availableResourceUses() {
        if (level == null) return 0;
        BlockState state = getBlockState();
        BlockPos targetPos = ShippingManifestBlock.attachedTargetPos(worldPosition, state);
        Direction targetSide = ShippingManifestBlock.attachedTargetSide(state);
        IFluidHandler fluids = ShippingManifestBlock.resolveFluidHandler(
                level, targetPos, targetSide, combinedManifest);
        int uses = detectedResourceUses(
                ShippingManifestBlock.findItemHandler(level, targetPos, targetSide), fluids,
                ShippingManifestBlock.findEnergyHandler(level, targetPos, targetSide),
                ShippingManifestBlock.findLongEnergyHandler(level, targetPos));
        return fluids == null ? uses : uses | USE_FUEL;
    }

    // Detect the default worker roles without automatically treating every fluid as fuel
    private int detectedDefaultResourceUses() {
        if (level == null) return resourceUses;
        BlockState state = getBlockState();
        BlockPos targetPos = ShippingManifestBlock.attachedTargetPos(worldPosition, state);
        Direction targetSide = ShippingManifestBlock.attachedTargetSide(state);
        return detectedResourceUses(
                ShippingManifestBlock.findItemHandler(level, targetPos, targetSide),
                ShippingManifestBlock.resolveFluidHandler(level, targetPos, targetSide, combinedManifest),
                ShippingManifestBlock.findEnergyHandler(level, targetPos, targetSide),
                ShippingManifestBlock.findLongEnergyHandler(level, targetPos));
    }

    // Update the worker resource uses selected by the player
    public boolean setResourceUses(int uses) {
        int allowed = availableResourceUses();
        int nextUses = uses & allowed & ALL_RESOURCE_USES;
        if (resourceUsesConfigured && resourceUses == nextUses) return false;
        resourceUses = nextUses;
        resourceUsesConfigured = true;
        setChanged();
        sendData();
        return true;
    }

    // Get the selected resource uses for a manifested storage target
    public static int attachedResourceUses(Level level, BlockPos targetPos) {
        if (level == null || targetPos == null) return -1;
        int uses = 0;
        boolean found = false;
        for (Direction direction : Direction.values()) {
            BlockPos manifestPos = targetPos.relative(direction);
            if (!(level.getBlockEntity(manifestPos) instanceof ShippingManifestBlockEntity manifest)
                    || !(manifest.getBlockState().getBlock() instanceof ShippingManifestBlock)) continue;
            if (!ShippingManifestBlock.attachedTargetPos(manifestPos,
                    manifest.getBlockState()).equals(targetPos)) continue;
            uses |= manifest.resourceUses();
            found = true;
        }
        return found ? uses & ALL_RESOURCE_USES : -1;
    }

    // Check whether a manifested container may receive this exact item stack
    public static boolean allowsAttachedContainerItem(Level level, BlockPos targetPos, ItemStack stack) {
        if (level == null || targetPos == null || stack == null || stack.isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            BlockPos manifestPos = targetPos.relative(direction);
            if (!(level.getBlockEntity(manifestPos) instanceof ShippingManifestBlockEntity manifest)
                    || !(manifest.getBlockState().getBlock() instanceof ShippingManifestBlock)) continue;
            if (!ShippingManifestBlock.attachedTargetPos(manifestPos,
                    manifest.getBlockState()).equals(targetPos)) continue;
            if (!manifest.acceptsContainerItem(stack)) return false;
        }
        return true;
    }

    // Check whether this manifest permits an item to enter its attached container
    private boolean acceptsContainerItem(ItemStack stack) {
        if (!containerLocked) return true;
        return containerLockFilters.stream().anyMatch(filter -> com.simibubi.create.content.logistics.filter.FilterItemStack.of(filter).test(level, stack));
    }

    // Check whether this manifest locks its attached container
    public boolean isContainerLocked() {
        return containerLocked;
    }

    // Get copies of the item filters accepted by this locked container
    public List<ItemStack> containerLockFilters() {
        return containerLockFilters.stream().map(ItemStack::copy).toList();
    }

    // Update the container lock and its accepted item filters
    public boolean setContainerLock(boolean locked, List<ItemStack> filters) {
        List<ItemStack> normalized = normalizeContainerLockFilters(filters);
        if (containerLocked == locked && filtersMatch(containerLockFilters, normalized)) return false;
        containerLocked = locked;
        containerLockFilters = normalized;
        setChanged();
        sendData();
        return true;
    }

    // Expose the attached item storage through this Manifest with its lock enforced
    public IItemHandler getContainerItemHandler(Direction side) {
        if (level == null) return null;
        BlockState state = getBlockState();
        IItemHandler handler = ShippingManifestBlock.findItemHandler(level,
                ShippingManifestBlock.attachedTargetPos(worldPosition, state),
                ShippingManifestBlock.attachedTargetSide(state));
        return handler == null ? null : new ContainerLockItemHandler(handler);
    }

    // Keep only distinct non-empty ghost stacks suitable for the container lock
    private static List<ItemStack> normalizeContainerLockFilters(List<ItemStack> filters) {
        if (filters == null || filters.isEmpty()) return List.of();
        List<ItemStack> normalized = new ArrayList<>();
        for (ItemStack filter : filters) {
            if (filter == null || filter.isEmpty()) continue;
            ItemStack copy = filter.copyWithCount(1);
            if (normalized.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, copy))) continue;
            normalized.add(copy);
            if (normalized.size() >= MAXIMUM_CONTAINER_LOCK_FILTERS) break;
        }
        return List.copyOf(normalized);
    }

    // Check whether two lock filter lists contain the same exact ghost stacks
    private static boolean filtersMatch(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.isSameItemSameComponents(first.get(index), second.get(index))) return false;
        }
        return true;
    }

    // Apply this Manifest's item lock to every capability insertion made through it
    private final class ContainerLockItemHandler implements IItemHandler {
        private final IItemHandler delegate;

        // Initialize the locking item-handler view
        private ContainerLockItemHandler(IItemHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return acceptsContainerItem(stack) ? delegate.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return acceptsContainerItem(stack) && delegate.isItemValid(slot, stack);
        }
    }

    // Detect the default resource uses from the attached storage capabilities
    private static int detectedResourceUses(
            IItemHandler items,
            IFluidHandler fluids,
            IEnergyStorage energy,
            LongEnergyStorage longEnergy
    ) {
        int uses = items == null ? 0 : USE_ITEMS;
        if (fluids != null) uses |= USE_FLUIDS;
        if (energy != null || longEnergy != null) uses |= USE_ENERGY;
        return uses;
    }

    // Get the manifest color
    public int getManifestColor() {
        return manifestColor;
    }

    // Set the manifest color
    public boolean setManifestColor(int col) {
        int nextColor = col & 0xFFFFFF;
        if (manifestColor == nextColor) {
            return false;
        }
        manifestColor = nextColor;
        setChanged();
        sendData();
        return true;
    }

    // Check if this is manifest glowing
    public boolean isManifestGlowing() {
        return manifestGlowing;
    }

    // Set the manifest glowing
    public boolean setManifestGlowing(boolean glowing) {
        if (manifestGlowing == glowing) {
            return false;
        }
        manifestGlowing = glowing;
        setChanged();
        sendData();
        return true;
    }

    // Get the world text color
    public int getWorldTextColor() {
        return isLightManifestColor(manifestColor) ? 0xFF101010 : 0xFFFFFFFF;
    }

    // Check if this is light manifest color
    private static boolean isLightManifestColor(int col) {
        int red = (col >> 16) & 0xFF;
        int green = (col >> 8) & 0xFF;
        int blue = col & 0xFF;
        double luminance = 0.2126D * red + 0.7152D * green + 0.0722D * blue;
        return luminance >= 150.0D;
    }

    // Sync the block state color
    private void syncBlockStateColor() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ShippingManifestBlock.MANIFEST_COLOR)) {
            return;
        }
        DyeColor targetColor = nearestDyeColor(manifestColor);
        if (state.getValue(ShippingManifestBlock.MANIFEST_COLOR) == targetColor) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(ShippingManifestBlock.MANIFEST_COLOR, targetColor),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    // Get the state manifest color
    private int getStateManifestColor() {
        BlockState state = getBlockState();
        return state.hasProperty(ShippingManifestBlock.MANIFEST_COLOR)
                ? state.getValue(ShippingManifestBlock.MANIFEST_COLOR).getTextColor()
                : DEFAULT_MANIFEST_COLOR;
    }

    // Get the nearest dye color
    private static DyeColor nearestDyeColor(int col) {
        DyeColor nearest = DyeColor.GREEN;
        double bestDistance = Double.MAX_VALUE;
        int red = (col >> 16) & 0xFF;
        int green = (col >> 8) & 0xFF;
        int blue = col & 0xFF;
        for (DyeColor dyeColor : DyeColor.values()) {
            int dye = dyeColor.getTextColor();
            int dyeRed = (dye >> 16) & 0xFF;
            int dyeGreen = (dye >> 8) & 0xFF;
            int dyeBlue = dye & 0xFF;
            double distance = square(red - dyeRed) + square(green - dyeGreen) + square(blue - dyeBlue);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = dyeColor;
            }
        }
        return nearest;
    }

    // Get the square
    private static int square(int val) {
        return val * val;
    }

    // Check if the manifest entries match
    private static boolean entriesMatch(List<DisplayEntry> first, List<DisplayEntry> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int idx = 0; idx < first.size(); idx++) {
            DisplayEntry left = first.get(idx);
            DisplayEntry right = second.get(idx);
            if (left.amount != right.amount
                    || !left.text.equals(right.text)
                    || !ItemStack.isSameItemSameComponents(left.stack, right.stack)) {
                return false;
            }
        }
        return true;
    }

    // Write the shipping manifest safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putBoolean(TAG_COMBINED_MANIFEST, combinedManifest);
        tag.putInt(TAG_MANIFEST_COLOR, manifestColor);
        tag.putBoolean(TAG_MANIFEST_GLOWING, manifestGlowing);
        tag.putInt(TAG_RESOURCE_USES, resourceUses());
        tag.putBoolean(TAG_RESOURCE_USES_CONFIGURED, resourceUsesConfigured);
        tag.putBoolean(TAG_CONTAINER_LOCKED, containerLocked);
        writeContainerLockFilters(tag, provider);
        tag.putString(TAG_MANUAL_GRAPH_TEXT, manualGraphText);
    }

    // Write the shipping manifest
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putBoolean(TAG_COMBINED_MANIFEST, combinedManifest);
        tag.putInt(TAG_MANIFEST_COLOR, manifestColor);
        tag.putBoolean(TAG_MANIFEST_GLOWING, manifestGlowing);
        tag.putInt(TAG_RESOURCE_USES, resourceUses());
        tag.putBoolean(TAG_RESOURCE_USES_CONFIGURED, resourceUsesConfigured);
        tag.putBoolean(TAG_CONTAINER_LOCKED, containerLocked);
        writeContainerLockFilters(tag, provider);
        if (!manualGraphText.isBlank()) {
            tag.putString(TAG_MANUAL_GRAPH_TEXT, manualGraphText);
        }
        if (!clientPacket) {
            return;
        }
        tag.putInt(TAG_ITEM_TYPES, itemTypes);
        tag.putInt(TAG_FLUID_TYPES, fluidTypes);
        tag.putLong(TAG_ENERGY, energy);
        tag.putLong(TAG_ENERGY_CAPACITY, energyCapacity);
        tag.putBoolean(TAG_HAS_FLUID_HANDLER, hasFluidHandler);
        ListTag previewTag = new ListTag();
        for (DisplayEntry entry : displayEntries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put(TAG_STACK, entry.stack.saveOptional(provider));
            entryTag.putLong(TAG_AMOUNT, entry.amount);
            entryTag.putString(TAG_TEXT, entry.text);
            previewTag.add(entryTag);
        }
        tag.put(TAG_PREVIEW, previewTag);
    }

    // Read the shipping manifest
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        combinedManifest = tag.getBoolean(TAG_COMBINED_MANIFEST);
        manifestColor = tag.contains(TAG_MANIFEST_COLOR)
                ? tag.getInt(TAG_MANIFEST_COLOR) & 0xFFFFFF
                : getStateManifestColor();
        manifestGlowing = tag.getBoolean(TAG_MANIFEST_GLOWING);
        resourceUses = tag.getInt(TAG_RESOURCE_USES) & ALL_RESOURCE_USES;
        resourceUsesConfigured = tag.getBoolean(TAG_RESOURCE_USES_CONFIGURED);
        containerLocked = tag.getBoolean(TAG_CONTAINER_LOCKED);
        containerLockFilters = readContainerLockFilters(tag, provider);
        manualGraphText = tag.getString(TAG_MANUAL_GRAPH_TEXT);
        if (!clientPacket) {
            return;
        }
        itemTypes = tag.getInt(TAG_ITEM_TYPES);
        fluidTypes = tag.getInt(TAG_FLUID_TYPES);
        energy = tag.getLong(TAG_ENERGY);
        energyCapacity = tag.getLong(TAG_ENERGY_CAPACITY);
        hasFluidHandler = tag.getBoolean(TAG_HAS_FLUID_HANDLER);
        ListTag previewTag = tag.getList(TAG_PREVIEW, Tag.TAG_COMPOUND);
        List<DisplayEntry> loadedEntries = new ArrayList<>(previewTag.size());
        for (int idx = 0; idx < previewTag.size(); idx++) {
            CompoundTag entryTag = previewTag.getCompound(idx);
            ItemStack stack = ItemStack.parseOptional(provider, entryTag.getCompound(TAG_STACK));
            String text = entryTag.getString(TAG_TEXT);
            if (!stack.isEmpty() || !text.isEmpty()) {
                loadedEntries.add(new DisplayEntry(stack, entryTag.getLong(TAG_AMOUNT), text));
            }
        }
        displayEntries = List.copyOf(loadedEntries);
        clientScrollOffset = normalizeScrollOffset(clientScrollOffset);
    }

    // Write the configured container lock filters
    private void writeContainerLockFilters(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag filters = new ListTag();
        for (ItemStack filter : containerLockFilters) filters.add(filter.saveOptional(provider));
        tag.put(TAG_CONTAINER_LOCK_FILTERS, filters);
    }

    // Read and normalize the configured container lock filters
    private static List<ItemStack> readContainerLockFilters(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag filters = tag.getList(TAG_CONTAINER_LOCK_FILTERS, Tag.TAG_COMPOUND);
        List<ItemStack> loaded = new ArrayList<>(filters.size());
        for (int index = 0; index < filters.size(); index++) {
            loaded.add(ItemStack.parseOptional(provider, filters.getCompound(index)));
        }
        return normalizeContainerLockFilters(loaded);
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!CTConfigs.CLIENT.showShippingManifestGoggleTooltip.get()) {
            return false;
        }
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.shipping_manifest")));
        if (itemTypes == 0 && fluidTypes == 0 && energyCapacity <= 0) {
            tooltip.add(Component.translatable("gui.createthrusters.shipping_manifest.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return true;
        }

        tooltip.add(Component.translatable("createthrusters.goggle.shipping_manifest.contents", itemTypes, fluidTypes)
                .withStyle(ChatFormatting.GRAY));
        if (energyCapacity > 0) {
            tooltip.add(Component.translatable(
                    "createthrusters.goggle.shipping_manifest.energy", energy, energyCapacity)
                    .withStyle(ChatFormatting.AQUA));
        }
        int displayLimit = isPlayerSneaking ? EXPANDED_ITEM_TYPES : COMPACT_ITEM_TYPES;
        for (int idx = 0; idx < Math.min(displayLimit, displayEntries.size()); idx++) {
            DisplayEntry entry = displayEntries.get(idx);
            if (entry.stack.isEmpty()) {
                tooltip.add(Component.literal("  " + entry.text).withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable("createthrusters.goggle.shipping_manifest.entry",
                        entry.stack.getHoverName(), entry.amount).withStyle(ChatFormatting.GRAY));
            }
        }

        int totalEntries = displayEntries.size();
        if (!isPlayerSneaking && totalEntries > COMPACT_ITEM_TYPES) {
            tooltip.add(Component.translatable("createthrusters.goggle.shipping_manifest.expand",
                    Component.keybind("key.sneak")).withStyle(ChatFormatting.DARK_GRAY));
        } else if (totalEntries > EXPANDED_ITEM_TYPES) {
            tooltip.add(Component.translatable("gui.createthrusters.shipping_manifest.overflow",
                    totalEntries - EXPANDED_ITEM_TYPES).withStyle(ChatFormatting.DARK_GRAY));
        }
        return true;
    }

    // Get the display entries
    public List<DisplayEntry> getDisplayEntries() {
        return displayEntries;
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        return Map.of(
                "display_text", "string",
                "item_types", "number",
                "fluid_types", "number",
                "energy", "number",
                "energy_capacity", "number",
                "energy_fill", "number",
                "combined_manifest", "boolean");
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        return Map.of(
                "display_text", "string",
                "combined_manifest", "boolean",
                "clear_manual", "boolean");
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        return switch (field) {
            case "display_text" -> AdvancedGraphDocument.Value.string(displayText());
            case "item_types" -> AdvancedGraphDocument.Value.number(itemTypes);
            case "fluid_types" -> AdvancedGraphDocument.Value.number(fluidTypes);
            case "energy" -> AdvancedGraphDocument.Value.number(energy);
            case "energy_capacity" -> AdvancedGraphDocument.Value.number(energyCapacity);
            case "energy_fill" -> AdvancedGraphDocument.Value.number(
                    energyCapacity <= 0 ? 0.0D : (double) energy / energyCapacity);
            case "combined_manifest" -> AdvancedGraphDocument.Value.bool(combinedManifest);
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value val) {
        switch (field) {
            case "display_text" -> {
                manualGraphText = val.asString();
                applyManualGraphText(manualGraphText);
                return true;
            }
            case "combined_manifest" -> {
                boolean next = val.asBoolean();
                if (combinedManifest == next) {
                    return false;
                }
                combinedManifest = next;
                setChanged();
                sendData();
                refreshNow();
                return true;
            }
            case "clear_manual" -> {
                if (!val.asBoolean() || manualGraphText.isBlank()) {
                    return false;
                }
                manualGraphText = "";
                refreshNow();
                setChanged();
                sendData();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // Get the display text
    private String displayText() {
        if (!manualGraphText.isBlank()) {
            return manualGraphText;
        }
        StringBuilder builder = new StringBuilder();
        for (DisplayEntry entry : displayEntries) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            if (!entry.text.isEmpty()) {
                builder.append(entry.text);
            } else if (!entry.stack.isEmpty()) {
                builder.append(entry.amount).append("x ").append(entry.stack.getHoverName().getString());
            }
        }
        return builder.toString();
    }

    // Check if this has fluid handler
    public boolean hasFluidHandler() {
        return hasFluidHandler;
    }

    // Get the client scroll offset
    public int getClientScrollOffset() {
        clientScrollOffset = normalizeScrollOffset(clientScrollOffset);
        return clientScrollOffset;
    }

    // Scroll the client display
    public void scrollClientDisplay(int dir) {
        if (displayEntries.size() <= DISPLAY_ROWS || dir == 0) {
            clientScrollOffset = 0;
            return;
        }
        clientScrollOffset = normalizeScrollOffset(clientScrollOffset + dir);
    }

    // Normalize the scroll offset
    private int normalizeScrollOffset(int offset) {
        int maxOffset = Math.max(0, displayEntries.size() - DISPLAY_ROWS);
        if (maxOffset == 0) {
            return 0;
        }
        return Math.floorMod(offset, maxOffset + 1);
    }

    // Get the icon
    @Override
    public ItemStack getIcon(boolean isPlayerSneaking) {
        return CTItems.SHIPPING_MANIFEST == null
                ? ItemStack.EMPTY
                : CTItems.SHIPPING_MANIFEST.get().getDefaultInstance();
    }

    // Store the display entry
    public record DisplayEntry(ItemStack stack, long amount, String text) {
    }
}
