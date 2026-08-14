package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
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
    private static final String TAG_PREVIEW = "Preview";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_TEXT = "Text";
    public static final int DEFAULT_MANIFEST_COLOR = 0x2F8F4E;

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
    private int energy;
    // Current energy capacity
    private int energyCapacity;
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
        int nextEnergy = energyHandler == null ? 0 : energyHandler.getEnergyStored();
        int nextEnergyCapacity = energyHandler == null ? 0 : energyHandler.getMaxEnergyStored();

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
        if (energyHandler != null) {
            nextDisplayEntries.add(textDisplay(Component.translatable(
                    "gui.createthrusters.shipping_manifest.energy",
                    nextEnergy, nextEnergyCapacity,
                    ShippingManifestBlock.formatPercent(nextEnergy, nextEnergyCapacity))));
        }
        boolean nextHasFluidHandler = hasFluidHandler || fluidHandler != null;
        if (itemTypes == nextItemTypes && fluidTypes == nextFluidTypes
                && hasFluidHandler == nextHasFluidHandler
                && energy == nextEnergy && energyCapacity == nextEnergyCapacity
                && entriesMatch(displayEntries, nextDisplayEntries)) {
            return;
        }

        itemTypes = nextItemTypes;
        fluidTypes = nextFluidTypes;
        hasFluidHandler = nextHasFluidHandler;
        energy = nextEnergy;
        energyCapacity = nextEnergyCapacity;
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
        tag.putString(TAG_MANUAL_GRAPH_TEXT, manualGraphText);
    }

    // Write the shipping manifest
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putBoolean(TAG_COMBINED_MANIFEST, combinedManifest);
        tag.putInt(TAG_MANIFEST_COLOR, manifestColor);
        tag.putBoolean(TAG_MANIFEST_GLOWING, manifestGlowing);
        if (!manualGraphText.isBlank()) {
            tag.putString(TAG_MANUAL_GRAPH_TEXT, manualGraphText);
        }
        if (!clientPacket) {
            return;
        }
        tag.putInt(TAG_ITEM_TYPES, itemTypes);
        tag.putInt(TAG_FLUID_TYPES, fluidTypes);
        tag.putInt(TAG_ENERGY, energy);
        tag.putInt(TAG_ENERGY_CAPACITY, energyCapacity);
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
        manualGraphText = tag.getString(TAG_MANUAL_GRAPH_TEXT);
        if (!clientPacket) {
            return;
        }
        itemTypes = tag.getInt(TAG_ITEM_TYPES);
        fluidTypes = tag.getInt(TAG_FLUID_TYPES);
        energy = tag.getInt(TAG_ENERGY);
        energyCapacity = tag.getInt(TAG_ENERGY_CAPACITY);
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
