package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.DocumentedPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Expose Rope Winch Cable controls and telemetry to ComputerCraft
@PeripheralTypeDoc("rope_winch_cable")
public class RopeWinchCablePeripheral implements DocumentedPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Winch
    private final RopeWinchPeripheralBridge winch;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the rope winch cable peripheral
    public RopeWinchCablePeripheral(RopeWinchPeripheralBridge winch) {
        this.winch = winch;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "rope_winch_cable";
    }

    // Compare this rope winch cable peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof RopeWinchCablePeripheral peripheral
                && peripheral.winch == winch;
    }

    // Check if this is connected
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnected", signature = "isConnected(): boolean",
            description = "Returns whether this is connected.")
    public final boolean isConnected() {
        return getAttachedClaw() != null;
    }

    // Get the remote type
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRemoteType", signature = "getRemoteType(): string",
            description = "Returns the remote type.")
    public final String getRemoteType() {
        return getAttachedClaw() != null ? "claw" : "none";
    }

    // Set the signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSignal", signature = "setSignal(signal: number)",
            description = "Sets the signal.")
    public final void setSignal(int signal) throws LuaException {
        if (signal < 0 || signal > 15) {
            throw new LuaException("signal must be between 0 and 15");
        }
        requireAttachedClaw().setComputerSignalOverride(signal);
    }

    // Clear the signal override
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSignalOverride", signature = "clearSignalOverride()",
            description = "Clears the signal override.")
    public final void clearSignalOverride() throws LuaException {
        requireAttachedClaw().clearComputerSignalOverride();
    }

    // Open the rope winch cable peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "open", signature = "open()",
            description = "Open the rope winch cable peripheral.")
    public final void open() throws LuaException {
        requireAttachedClaw().setComputerSignalOverride(0);
    }

    // Close the rope winch cable peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "close", signature = "close()",
            description = "Close the rope winch cable peripheral.")
    public final void close() throws LuaException {
        requireAttachedClaw().setComputerSignalOverride(15);
    }

    // Release the rope winch cable peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "release", signature = "release()",
            description = "Release the rope winch cable peripheral.")
    public final void release() throws LuaException {
        ClawBlockEntity claw = requireAttachedClaw();
        claw.forceRelease();
        claw.setComputerSignalOverride(0);
    }

    // Get the signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns the signal.")
    public final int getSignal() throws LuaException {
        return requireAttachedClaw().getSignalStrength();
    }

    // Check if this is holding
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isHolding", signature = "isHolding(): boolean",
            description = "Returns whether this is holding.")
    public final boolean isHolding() throws LuaException {
        return requireAttachedClaw().isHoldingConnector();
    }

    // Get the held connector pos
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getHeldConnectorPos", signature = "getHeldConnectorPos(): table",
            description = "Returns the held connector pos.")
    public final Map<String, Object> getHeldConnectorPos() throws LuaException {
        Map<String, Object> held = clawPeripheral().getHeldConnectorPos();
        if (held == null) {
            return new HashMap<>();
        }
        return held;
    }

    // Get the selected connector pos
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedConnectorPos", signature = "getSelectedConnectorPos(): table",
            description = "Returns the selected connector pos.")
    public final Map<String, Object> getSelectedConnectorPos() throws LuaException {
        Map<String, Object> selected = clawPeripheral().getSelectedConnectorPos();
        if (selected == null) {
            return new HashMap<>();
        }
        return selected;
    }

    // Get the nearest connector
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnector", signature = "getNearestConnector(): table",
            description = "Returns the nearest connector.")
    public final Map<String, Object> getNearestConnector() throws LuaException {
        Map<String, Object> nearest = clawPeripheral().getNearestConnector();
        if (nearest == null) {
            return new HashMap<>();
        }
        return nearest;
    }

    // Get the nearest connector in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnectorInRange", signature = "getNearestConnectorInRange(range: number): table",
            description = "Returns the nearest connector in range.")
    public final Map<String, Object> getNearestConnectorInRange(int range) throws LuaException {
        Map<String, Object> nearest = clawPeripheral().getNearestConnectorInRange(range);
        if (nearest == null) {
            return new HashMap<>();
        }
        return nearest;
    }

    // Get the connectors in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRange", signature = "getConnectorsInRange(range: number): table",
            description = "Returns the connectors in range.")
    public final List<Map<String, Object>> getConnectorsInRange(int range) throws LuaException {
        return clawPeripheral().getConnectorsInRange(range);
    }

    // Get the connectors in range limited
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRangeLimited", signature = "getConnectorsInRangeLimited(range: number, limit: number): table",
            description = "Returns the connectors in range limited.")
    public final List<Map<String, Object>> getConnectorsInRangeLimited(int range, int limit) throws LuaException {
        return clawPeripheral().getConnectorsInRangeLimited(range, limit);
    }

    // Check if the connector is in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRange", signature = "isConnectorInRange(x: number, y: number, z: number): boolean",
            description = "Returns whether the connector is in range.")
    public final boolean isConnectorInRange(int x, int y, int z) throws LuaException {
        return requireAttachedClaw().isConnectorInRange(new BlockPos(x, y, z), 3);
    }

    // Check if the connector is in the range with radius
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRangeWithRadius", signature = "isConnectorInRangeWithRadius(x: number, y: number, z: number, range: number): boolean",
            description = "Returns whether the connector is in the range with radius.")
    public final boolean isConnectorInRangeWithRadius(int x, int y, int z, int range) throws LuaException {
        return requireAttachedClaw().isConnectorInRange(new BlockPos(x, y, z), range);
    }

    // Select the connector
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "selectConnector", signature = "selectConnector(x: number, y: number, z: number): boolean",
            description = "Select the connector.")
    public final boolean selectConnector(int x, int y, int z) throws LuaException {
        return requireAttachedClaw().selectConnector(new BlockPos(x, y, z));
    }

    // Check if the connector reference is in range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorReferenceInRange", signature = "isConnectorReferenceInRange(localX: number, localY: number, localZ: number, subLevelId: string, [range: number]): boolean",
            description = "Returns whether the connector reference is in range.")
    public final boolean isConnectorReferenceInRange(int localX, int localY, int localZ, String subLevelId,
                                                     Optional<Integer> range) throws LuaException {
        return clawPeripheral().isConnectorReferenceInRange(localX, localY, localZ, subLevelId, range);
    }

    // Select the connector reference
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "selectConnectorReference", signature = "selectConnectorReference(localX: number, localY: number, localZ: number, subLevelId: string): boolean",
            description = "Select the connector reference.")
    public final boolean selectConnectorReference(int localX, int localY, int localZ,
                                                  String subLevelId) throws LuaException {
        return clawPeripheral().selectConnectorReference(localX, localY, localZ, subLevelId);
    }

    // Clear the selected connector
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSelectedConnector", signature = "clearSelectedConnector()",
            description = "Clears the selected connector.")
    public final void clearSelectedConnector() throws LuaException {
        requireAttachedClaw().clearSelectedConnector();
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() throws LuaException {
        return new ClawPeripheral(requireAttachedClaw()).getStatus();
    }

    // Get the require attached claw
    private ClawBlockEntity requireAttachedClaw() throws LuaException {
        ClawBlockEntity claw = getAttachedClaw();
        if (claw == null) {
            throw new LuaException("no claw connected to this rope winch");
        }
        return claw;
    }

    // Get the attached claw
    private ClawBlockEntity getAttachedClaw() {
        return winch.ct$getAttachedClaw();
    }

    // Get the claw peripheral
    private ClawPeripheral clawPeripheral() throws LuaException {
        return new ClawPeripheral(requireAttachedClaw());
    }
}
