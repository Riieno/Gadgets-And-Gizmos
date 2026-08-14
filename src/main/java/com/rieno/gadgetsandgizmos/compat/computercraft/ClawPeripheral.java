package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
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
public class ClawPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final ClawBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw peripheral
    public ClawPeripheral(ClawBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "claw";
    }

    // Compare this claw peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ClawPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Set the signal
    @LuaFunction
    public final void setSignal(int signal) throws LuaException {
        if (signal < 0 || signal > 15) {
            throw new LuaException("signal must be between 0 and 15");
        }
        blockEntity.setComputerSignalOverride(signal);
    }

    // Clear the signal override
    @LuaFunction
    public final void clearSignalOverride() {
        blockEntity.clearComputerSignalOverride();
    }

    // Open the claw peripheral
    @LuaFunction
    public final void open() {
        blockEntity.setComputerSignalOverride(0);
    }

    // Close the claw peripheral
    @LuaFunction
    public final void close() {
        blockEntity.setComputerSignalOverride(15);
    }

    // Release the claw peripheral
    @LuaFunction
    public final void release() {
        blockEntity.forceRelease();
        blockEntity.setComputerSignalOverride(0);
    }

    // Get the signal
    @LuaFunction
    public final int getSignal() {
        return blockEntity.getSignalStrength();
    }

    // Get the computer signal
    @LuaFunction
    public final int getComputerSignal() {
        return blockEntity.getComputerSignalOverride();
    }

    // Check if this is holding
    @LuaFunction
    public final boolean isHolding() {
        return blockEntity.isHoldingConnector();
    }

    // Get the held connector pos
    @LuaFunction
    public final @Nullable Map<String, Object> getHeldConnectorPos() {
        return toPosMap(blockEntity.getGrabbedConnectorReference());
    }

    // Get the selected connector pos
    @LuaFunction
    public final @Nullable Map<String, Object> getSelectedConnectorPos() {
        return toPosMap(blockEntity.getPendingConnectorReference());
    }

    // Get the nearest connector
    @LuaFunction
    public final @Nullable Map<String, Object> getNearestConnector() {
        return toPosMap(blockEntity.findNearestFreeConnectorReferenceInRange(3));
    }

    // Get the nearest connector in range
    @LuaFunction
    public final @Nullable Map<String, Object> getNearestConnectorInRange(int range) {
        return toPosMap(blockEntity.findNearestFreeConnectorReferenceInRange(range));
    }

    // Get the connectors in range
    @LuaFunction
    public final List<Map<String, Object>> getConnectorsInRange(int range) {
        return toPosList(blockEntity.getFreeConnectorReferencesInRange(range, 64));
    }

    // Get the connectors in range limited
    @LuaFunction
    public final List<Map<String, Object>> getConnectorsInRangeLimited(int range, int limit) {
        return toPosList(blockEntity.getFreeConnectorReferencesInRange(range, limit));
    }

    // Check if the connector is in range
    @LuaFunction
    public final boolean isConnectorInRange(int x, int y, int z) {
        return blockEntity.isConnectorInRange(new BlockPos(x, y, z), 3);
    }

    // Check if the connector is in the range with radius
    @LuaFunction
    public final boolean isConnectorInRangeWithRadius(int x, int y, int z, int range) {
        return blockEntity.isConnectorInRange(new BlockPos(x, y, z), range);
    }

    // Select the connector
    @LuaFunction
    public final boolean selectConnector(int x, int y, int z) {
        return blockEntity.selectConnector(new BlockPos(x, y, z));
    }

    // Check if the connector reference is in range
    @LuaFunction
    public final boolean isConnectorReferenceInRange(int localX, int localY, int localZ, String subLevelId,
                                                     Optional<Integer> range) throws LuaException {
        int checkedRange = Math.max(1, range.orElse(3));
        return blockEntity.isConnectorInRange(
                new BlockPos(localX, localY, localZ), parseSubLevelId(subLevelId), checkedRange);
    }

    // Select the connector reference
    @LuaFunction
    public final boolean selectConnectorReference(int localX, int localY, int localZ,
                                                  String subLevelId) throws LuaException {
        return blockEntity.selectConnector(
                new BlockPos(localX, localY, localZ), parseSubLevelId(subLevelId));
    }

    // Clear the selected connector
    @LuaFunction
    public final void clearSelectedConnector() {
        blockEntity.clearSelectedConnector();
    }

    // Set the receiver frequency
    @LuaFunction
    public final void setReceiverFrequency(String frequencyA, String frequencyB) throws LuaException {
        blockEntity.setReceiverFrequency(parseFrequency(frequencyA), parseFrequency(frequencyB));
    }

    // Clear the receiver frequency
    @LuaFunction
    public final void clearReceiverFrequency() {
        blockEntity.setReceiverFrequency(ItemStack.EMPTY, ItemStack.EMPTY);
    }

    // Get the receiver frequency
    @LuaFunction
    public final Map<String, Object> getReceiverFrequency() {
        Map<String, Object> out = new HashMap<>();
        ItemStack first = blockEntity.getReceiverFrequencyFirst();
        ItemStack second = blockEntity.getReceiverFrequencySecond();
        out.put("frequencyA", stackToItemId(first));
        out.put("frequencyB", stackToItemId(second));
        out.put("bound", !first.isEmpty() || !second.isEmpty());
        return out;
    }

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "methods",
                "setSignal",
                "clearSignalOverride",
                "open",
                "close",
                "release",
                "getSignal",
                "getComputerSignal",
                "isHolding",
                "getHeldConnectorPos",
                "getSelectedConnectorPos",
                "getNearestConnector",
                "getNearestConnectorInRange",
                "getConnectorsInRange",
                "getConnectorsInRangeLimited",
                "isConnectorInRange",
                "isConnectorInRangeWithRadius",
                "selectConnector",
                "isConnectorReferenceInRange",
                "selectConnectorReference",
                "clearSelectedConnector",
                "setReceiverFrequency",
                "clearReceiverFrequency",
                "getReceiverFrequency",
                "getStatus");
    }

    // Get the status
    @LuaFunction
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
