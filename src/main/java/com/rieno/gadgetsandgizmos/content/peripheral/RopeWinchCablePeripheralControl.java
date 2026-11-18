package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralBinding;
import com.rieno.gadgetsandgizmos.compat.computercraft.RopeWinchPeripheralBridge;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Expose Rope Winch Cable controls and telemetry to ComputerCraft
@PeripheralTypeDoc("rope_winch_cable")
public class RopeWinchCablePeripheralControl implements ScriptPeripheralBinding {
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
    public RopeWinchCablePeripheralControl(RopeWinchPeripheralBridge winch) {
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
    public boolean equals(ScriptPeripheralBinding other) {
        return other instanceof RopeWinchCablePeripheralControl peripheral
                && peripheral.winch == winch;
    }

    // Check if this is connected
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isConnected", signature = "isConnected(): boolean",
            description = "Returns whether this is connected.")
    public final boolean isConnected() {
        return getAttachedClaw() != null;
    }

    // Get the remote type
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRemoteType", signature = "getRemoteType(): string",
            description = "Returns the remote type.")
    public final String getRemoteType() {
        return getAttachedClaw() != null ? "claw" : "none";
    }

    // Set the signal
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setSignal", signature = "setSignal(signal: number)",
            description = "Sets the signal.")
    public final void setSignal(int signal) throws ScriptException {
        if (signal < 0 || signal > 15) {
            throw new ScriptException("signal must be between 0 and 15");
        }
        requireAttachedClaw().setComputerSignalOverride(signal);
    }

    // Clear the signal override
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearSignalOverride", signature = "clearSignalOverride()",
            description = "Clears the signal override.")
    public final void clearSignalOverride() throws ScriptException {
        requireAttachedClaw().clearComputerSignalOverride();
    }

    // Open the rope winch cable peripheral
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "open", signature = "open()",
            description = "Open the rope winch cable peripheral.")
    public final void open() throws ScriptException {
        requireAttachedClaw().setComputerSignalOverride(0);
    }

    // Close the rope winch cable peripheral
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "close", signature = "close()",
            description = "Close the rope winch cable peripheral.")
    public final void close() throws ScriptException {
        requireAttachedClaw().setComputerSignalOverride(15);
    }

    // Release the rope winch cable peripheral
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "release", signature = "release()",
            description = "Release the rope winch cable peripheral.")
    public final void release() throws ScriptException {
        ClawBlockEntity claw = requireAttachedClaw();
        claw.forceRelease();
        claw.setComputerSignalOverride(0);
    }

    // Get the signal
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns the signal.")
    public final int getSignal() throws ScriptException {
        return requireAttachedClaw().getSignalStrength();
    }

    // Check if this is holding
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isHolding", signature = "isHolding(): boolean",
            description = "Returns whether this is holding.")
    public final boolean isHolding() throws ScriptException {
        return requireAttachedClaw().isHoldingConnector();
    }

    // Get the held connector pos
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getHeldConnectorPos", signature = "getHeldConnectorPos(): table",
            description = "Returns the held connector pos.")
    public final Map<String, Object> getHeldConnectorPos() throws ScriptException {
        Map<String, Object> held = clawPeripheral().getHeldConnectorPos();
        if (held == null) {
            return new HashMap<>();
        }
        return held;
    }

    // Get the selected connector pos
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getSelectedConnectorPos", signature = "getSelectedConnectorPos(): table",
            description = "Returns the selected connector pos.")
    public final Map<String, Object> getSelectedConnectorPos() throws ScriptException {
        Map<String, Object> selected = clawPeripheral().getSelectedConnectorPos();
        if (selected == null) {
            return new HashMap<>();
        }
        return selected;
    }

    // Get the nearest connector
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getNearestConnector", signature = "getNearestConnector(): table",
            description = "Returns the nearest connector.")
    public final Map<String, Object> getNearestConnector() throws ScriptException {
        Map<String, Object> nearest = clawPeripheral().getNearestConnector();
        if (nearest == null) {
            return new HashMap<>();
        }
        return nearest;
    }

    // Get the nearest connector in range
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getNearestConnectorInRange", signature = "getNearestConnectorInRange(range: number): table",
            description = "Returns the nearest connector in range.")
    public final Map<String, Object> getNearestConnectorInRange(int range) throws ScriptException {
        Map<String, Object> nearest = clawPeripheral().getNearestConnectorInRange(range);
        if (nearest == null) {
            return new HashMap<>();
        }
        return nearest;
    }

    // Get the connectors in range
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getConnectorsInRange", signature = "getConnectorsInRange(range: number): table",
            description = "Returns the connectors in range.")
    public final List<Map<String, Object>> getConnectorsInRange(int range) throws ScriptException {
        return clawPeripheral().getConnectorsInRange(range);
    }

    // Get the connectors in range limited
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getConnectorsInRangeLimited", signature = "getConnectorsInRangeLimited(range: number, limit: number): table",
            description = "Returns the connectors in range limited.")
    public final List<Map<String, Object>> getConnectorsInRangeLimited(int range, int limit) throws ScriptException {
        return clawPeripheral().getConnectorsInRangeLimited(range, limit);
    }

    // Check if the connector is in range
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isConnectorInRange", signature = "isConnectorInRange(x: number, y: number, z: number): boolean",
            description = "Returns whether the connector is in range.")
    public final boolean isConnectorInRange(int x, int y, int z) throws ScriptException {
        return requireAttachedClaw().isConnectorInRange(new BlockPos(x, y, z), 3);
    }

    // Check if the connector is in the range with radius
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isConnectorInRangeWithRadius", signature = "isConnectorInRangeWithRadius(x: number, y: number, z: number, range: number): boolean",
            description = "Returns whether the connector is in the range with radius.")
    public final boolean isConnectorInRangeWithRadius(int x, int y, int z, int range) throws ScriptException {
        return requireAttachedClaw().isConnectorInRange(new BlockPos(x, y, z), range);
    }

    // Select the connector
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "selectConnector", signature = "selectConnector(x: number, y: number, z: number): boolean",
            description = "Select the connector.")
    public final boolean selectConnector(int x, int y, int z) throws ScriptException {
        return requireAttachedClaw().selectConnector(new BlockPos(x, y, z));
    }

    // Check if the connector reference is in range
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isConnectorReferenceInRange", signature = "isConnectorReferenceInRange(localX: number, localY: number, localZ: number, subLevelId: string, [range: number]): boolean",
            description = "Returns whether the connector reference is in range.")
    public final boolean isConnectorReferenceInRange(int localX, int localY, int localZ, String subLevelId,
                                                     Optional<Integer> range) throws ScriptException {
        return clawPeripheral().isConnectorReferenceInRange(localX, localY, localZ, subLevelId, range);
    }

    // Select the connector reference
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "selectConnectorReference", signature = "selectConnectorReference(localX: number, localY: number, localZ: number, subLevelId: string): boolean",
            description = "Select the connector reference.")
    public final boolean selectConnectorReference(int localX, int localY, int localZ,
                                                  String subLevelId) throws ScriptException {
        return clawPeripheral().selectConnectorReference(localX, localY, localZ, subLevelId);
    }

    // Clear the selected connector
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearSelectedConnector", signature = "clearSelectedConnector()",
            description = "Clears the selected connector.")
    public final void clearSelectedConnector() throws ScriptException {
        requireAttachedClaw().clearSelectedConnector();
    }

    // Get the status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() throws ScriptException {
        return new ClawPeripheralControl(requireAttachedClaw()).getStatus();
    }

    // Get the require attached claw
    private ClawBlockEntity requireAttachedClaw() throws ScriptException {
        ClawBlockEntity claw = getAttachedClaw();
        if (claw == null) {
            throw new ScriptException("no claw connected to this rope winch");
        }
        return claw;
    }

    // Get the attached claw
    private ClawBlockEntity getAttachedClaw() {
        return winch.ct$getAttachedClaw();
    }

    // Get the claw peripheral
    private ClawPeripheralControl clawPeripheral() throws ScriptException {
        return new ClawPeripheralControl(requireAttachedClaw());
    }
}
