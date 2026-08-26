package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Give ComputerCraft safe control over one attached claw and its held target
@PeripheralTypeDoc("claw")
public class ClawPeripheral extends GadgetsPeripheral<ClawBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw peripheral
    public ClawPeripheral(ClawBlockEntity blockEntity) {
        super(blockEntity, "claw");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSignal", signature = "setSignal(signal: number)",
            description = "Sets the signal.")
    public final void setSignal(int signal) throws LuaException {
        if (signal < 0 || signal > 15) {
            throw new LuaException("signal must be between 0 and 15");
        }
        blockEntity.setComputerSignalOverride(signal);
    }

    // Clear the signal override
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSignalOverride", signature = "clearSignalOverride()",
            description = "Clears the signal override.")
    public final void clearSignalOverride() {
        blockEntity.clearComputerSignalOverride();
    }

    // Open the claw peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "open", signature = "open()",
            description = "Open the claw peripheral.")
    public final void open() {
        blockEntity.setComputerSignalOverride(0);
    }

    // Close the claw peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "close", signature = "close()",
            description = "Close the claw peripheral.")
    public final void close() {
        blockEntity.setComputerSignalOverride(15);
    }

    // Release the claw peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "release", signature = "release()",
            description = "Release the claw peripheral.")
    public final void release() {
        blockEntity.forceRelease();
        blockEntity.setComputerSignalOverride(0);
    }

    // Get the signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns the signal.")
    public final int getSignal() {
        return blockEntity.getSignalStrength();
    }

    // Get the computer signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getComputerSignal", signature = "getComputerSignal(): number",
            description = "Returns the computer signal.")
    public final int getComputerSignal() {
        return blockEntity.getComputerSignalOverride();
    }

    // Check if this is holding
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isHolding", signature = "isHolding(): boolean",
            description = "Returns whether this is holding.")
    public final boolean isHolding() {
        return blockEntity.isHoldingConnector();
    }

    // Get the held connector pos
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getHeldConnectorPos", signature = "getHeldConnectorPos(): table",
            description = "Returns the held connector pos.")
    public final @Nullable Map<String, Object> getHeldConnectorPos() {
        return toPosMap(blockEntity.getGrabbedConnectorReference());
    }

    // Get the selected connector pos
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedConnectorPos", signature = "getSelectedConnectorPos(): table",
            description = "Returns the selected connector pos.")
    public final @Nullable Map<String, Object> getSelectedConnectorPos() {
        return toPosMap(blockEntity.getPendingConnectorReference());
    }

    // Get the nearest connector
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnector", signature = "getNearestConnector(): table",
            description = "Returns the nearest connector.")
    public final @Nullable Map<String, Object> getNearestConnector() {
        return toPosMap(blockEntity.findNearestFreeConnectorReferenceInRange(3));
    }

    // Get the nearest connector in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnectorInRange", signature = "getNearestConnectorInRange(range: number): table",
            description = "Returns the nearest connector in range.")
    public final @Nullable Map<String, Object> getNearestConnectorInRange(int range) {
        return toPosMap(blockEntity.findNearestFreeConnectorReferenceInRange(range));
    }

    // Get the connectors in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRange", signature = "getConnectorsInRange(range: number): table",
            description = "Returns the connectors in range.")
    public final List<Map<String, Object>> getConnectorsInRange(int range) {
        return toPosList(blockEntity.getFreeConnectorReferencesInRange(range, 64));
    }

    // Get the connectors in range limited
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRangeLimited", signature = "getConnectorsInRangeLimited(range: number, limit: number): table",
            description = "Returns the connectors in range limited.")
    public final List<Map<String, Object>> getConnectorsInRangeLimited(int range, int limit) {
        return toPosList(blockEntity.getFreeConnectorReferencesInRange(range, limit));
    }

    // Check if the connector is in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRange", signature = "isConnectorInRange(x: number, y: number, z: number): boolean",
            description = "Returns whether the connector is in range.")
    public final boolean isConnectorInRange(int x, int y, int z) {
        return blockEntity.isConnectorInRange(new BlockPos(x, y, z), 3);
    }

    // Check if the connector is in the range with radius
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRangeWithRadius", signature = "isConnectorInRangeWithRadius(x: number, y: number, z: number, range: number): boolean",
            description = "Returns whether the connector is in the range with radius.")
    public final boolean isConnectorInRangeWithRadius(int x, int y, int z, int range) {
        return blockEntity.isConnectorInRange(new BlockPos(x, y, z), range);
    }

    // Select the connector
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "selectConnector", signature = "selectConnector(x: number, y: number, z: number): boolean",
            description = "Select the connector.")
    public final boolean selectConnector(int x, int y, int z) {
        return blockEntity.selectConnector(new BlockPos(x, y, z));
    }

    // Check if the connector reference is in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorReferenceInRange", signature = "isConnectorReferenceInRange(localX: number, localY: number, localZ: number, subLevelId: string, [range: number]): boolean",
            description = "Returns whether the connector reference is in range.")
    public final boolean isConnectorReferenceInRange(int localX, int localY, int localZ, String subLevelId,
                                                     Optional<Integer> range) throws LuaException {
        int checkedRange = Math.max(1, range.orElse(3));
        return blockEntity.isConnectorInRange(
                new BlockPos(localX, localY, localZ), parseSubLevelId(subLevelId), checkedRange);
    }

    // Select the connector reference
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "selectConnectorReference", signature = "selectConnectorReference(localX: number, localY: number, localZ: number, subLevelId: string): boolean",
            description = "Select the connector reference.")
    public final boolean selectConnectorReference(int localX, int localY, int localZ,
                                                  String subLevelId) throws LuaException {
        return blockEntity.selectConnector(
                new BlockPos(localX, localY, localZ), parseSubLevelId(subLevelId));
    }

    // Clear the selected connector
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSelectedConnector", signature = "clearSelectedConnector()",
            description = "Clears the selected connector.")
    public final void clearSelectedConnector() {
        blockEntity.clearSelectedConnector();
    }

    // Set the receiver frequency
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setReceiverFrequency", signature = "setReceiverFrequency(frequencyA: string, frequencyB: string)",
            description = "Sets the receiver frequency.")
    public final void setReceiverFrequency(String frequencyA, String frequencyB) throws LuaException {
        blockEntity.setReceiverFrequency(parseFrequency(frequencyA), parseFrequency(frequencyB));
    }

    // Clear the receiver frequency
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearReceiverFrequency", signature = "clearReceiverFrequency()",
            description = "Clears the receiver frequency.")
    public final void clearReceiverFrequency() {
        blockEntity.setReceiverFrequency(ItemStack.EMPTY, ItemStack.EMPTY);
    }

    // Get the receiver frequency
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getReceiverFrequency", signature = "getReceiverFrequency(): table",
            description = "Returns the receiver frequency.")
    public final Map<String, Object> getReceiverFrequency() {
        Map<String, Object> out = new HashMap<>();
        ItemStack first = blockEntity.getReceiverFrequencyFirst();
        ItemStack second = blockEntity.getReceiverFrequencySecond();
        out.put("frequencyA", stackToItemId(first));
        out.put("frequencyB", stackToItemId(second));
        out.put("bound", !first.isEmpty() || !second.isEmpty());
        return out;
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> out = new HashMap<>();
        out.put("signal", blockEntity.getSignalStrength());
        out.put("computerSignal", blockEntity.getComputerSignalOverride());
        out.put("wirelessSignal", blockEntity.getWirelessSignal());
        out.put("holding", blockEntity.isHoldingConnector());
        out.put("heldPos", getHeldConnectorPos());
        out.put("selectedPos", getSelectedConnectorPos());
        out.put("receiverFrequency", getReceiverFrequency());
        return out;
    }

    // Get the stack to item id
    private static String stackToItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    // Parse the frequency
    private static ItemStack parseFrequency(String itemId) throws LuaException {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location;
        try {
            location = ResourceLocation.parse(itemId.trim());
        } catch (Exception err) {
            throw new LuaException("invalid item id '" + itemId + "'");
        }
        Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        if (item == null) {
            throw new LuaException("unknown item id '" + itemId + "'");
        }
        return new ItemStack(item);
    }

    // Convert the claw peripheral to pos map
    private Map<String, Object> toPosMap(@Nullable ClawBlockEntity.ConnectorReference reference) {
        if (reference == null) {
            return null;
        }
        Map<String, Object> out = reference.worldPos() == null
                ? ComputerCraftPositionHelper.blockPosition(
                        blockEntity.getLevel(), reference.subLevelId(), reference.localPos())
                : ComputerCraftPositionHelper.worldPosition(blockEntity.getLevel(), reference.worldPos());
        out.put("localX", reference.localPos().getX());
        out.put("localY", reference.localPos().getY());
        out.put("localZ", reference.localPos().getZ());
        out.put("subLevelId", reference.subLevelId() == null ? "" : reference.subLevelId().toString());
        return out;
    }

    // Convert the claw peripheral to pos list
    private List<Map<String, Object>> toPosList(List<ClawBlockEntity.ConnectorReference> positions) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ClawBlockEntity.ConnectorReference pos : positions) {
            out.add(toPosMap(pos));
        }
        return out;
    }

    // Parse the sublevel id
    private static @Nullable UUID parseSubLevelId(String val) throws LuaException {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(val.trim());
        } catch (IllegalArgumentException err) {
            throw new LuaException("subLevelId must be an empty string or a UUID");
        }
    }
}
