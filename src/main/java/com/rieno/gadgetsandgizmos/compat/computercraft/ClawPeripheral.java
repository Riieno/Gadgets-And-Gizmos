package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.ClawPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Give ComputerCraft safe control over one attached claw and its held target
@PeripheralTypeDoc("claw")
public class ClawPeripheral extends GadgetsPeripheral<ClawBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ClawPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public ClawPeripheral(ClawBlockEntity blockEntity) {
        super(blockEntity, "claw");
        this.shared = new ClawPeripheralControl(blockEntity);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSignal", signature = "setSignal(signal: number)",
            description = "Sets the signal.")
    public final void setSignal(int signal) throws LuaException {
        try{ shared.setSignal(signal); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSignalOverride", signature = "clearSignalOverride()",
            description = "Clears the signal override.")
    public final void clearSignalOverride() { shared.clearSignalOverride(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "open", signature = "open()",
            description = "Open the claw peripheral.")
    public final void open() { shared.open(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "close", signature = "close()",
            description = "Close the claw peripheral.")
    public final void close() { shared.close(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "release", signature = "release()",
            description = "Release the claw peripheral.")
    public final void release() { shared.release(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns the signal.")
    public final int getSignal() { return shared.getSignal(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getComputerSignal", signature = "getComputerSignal(): number",
            description = "Returns the computer signal.")
    public final int getComputerSignal() { return shared.getComputerSignal(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isHolding", signature = "isHolding(): boolean",
            description = "Returns whether this is holding.")
    public final boolean isHolding() { return shared.isHolding(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getHeldConnectorPos", signature = "getHeldConnectorPos(): table",
            description = "Returns the held connector pos.")
    public final @Nullable Map<String, Object> getHeldConnectorPos() { return shared.getHeldConnectorPos(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedConnectorPos", signature = "getSelectedConnectorPos(): table",
            description = "Returns the selected connector pos.")
    public final @Nullable Map<String, Object> getSelectedConnectorPos() { return shared.getSelectedConnectorPos(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnector", signature = "getNearestConnector(): table",
            description = "Returns the nearest connector.")
    public final @Nullable Map<String, Object> getNearestConnector() { return shared.getNearestConnector(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnectorInRange", signature = "getNearestConnectorInRange(range: number): table",
            description = "Returns the nearest connector in range.")
    public final @Nullable Map<String, Object> getNearestConnectorInRange(int range) { return shared.getNearestConnectorInRange(range); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRange", signature = "getConnectorsInRange(range: number): table",
            description = "Returns the connectors in range.")
    public final List<Map<String, Object>> getConnectorsInRange(int range) { return shared.getConnectorsInRange(range); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRangeLimited", signature = "getConnectorsInRangeLimited(range: number, limit: number): table",
            description = "Returns the connectors in range limited.")
    public final List<Map<String, Object>> getConnectorsInRangeLimited(int range, int limit) { return shared.getConnectorsInRangeLimited(range, limit); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRange", signature = "isConnectorInRange(x: number, y: number, z: number): boolean",
            description = "Returns whether the connector is in range.")
    public final boolean isConnectorInRange(int x, int y, int z) { return shared.isConnectorInRange(x, y, z); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRangeWithRadius", signature = "isConnectorInRangeWithRadius(x: number, y: number, z: number, range: number): boolean",
            description = "Returns whether the connector is in the range with radius.")
    public final boolean isConnectorInRangeWithRadius(int x, int y, int z, int range) { return shared.isConnectorInRangeWithRadius(x, y, z, range); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "selectConnector", signature = "selectConnector(x: number, y: number, z: number): boolean",
            description = "Select the connector.")
    public final boolean selectConnector(int x, int y, int z) { return shared.selectConnector(x, y, z); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorReferenceInRange", signature = "isConnectorReferenceInRange(localX: number, localY: number, localZ: number, subLevelId: string, [range: number]): boolean",
            description = "Returns whether the connector reference is in range.")
    public final boolean isConnectorReferenceInRange(int localX, int localY, int localZ, String subLevelId,
                                                     Optional<Integer> range) throws LuaException {
        try{ return shared.isConnectorReferenceInRange(localX, localY, localZ, subLevelId, range); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "selectConnectorReference", signature = "selectConnectorReference(localX: number, localY: number, localZ: number, subLevelId: string): boolean",
            description = "Select the connector reference.")
    public final boolean selectConnectorReference(int localX, int localY, int localZ,
                                                  String subLevelId) throws LuaException {
        try{ return shared.selectConnectorReference(localX, localY, localZ, subLevelId); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSelectedConnector", signature = "clearSelectedConnector()",
            description = "Clears the selected connector.")
    public final void clearSelectedConnector() { shared.clearSelectedConnector(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setReceiverFrequency", signature = "setReceiverFrequency(frequencyA: string, frequencyB: string)",
            description = "Sets the receiver frequency.")
    public final void setReceiverFrequency(String frequencyA, String frequencyB) throws LuaException {
        try{ shared.setReceiverFrequency(frequencyA, frequencyB); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearReceiverFrequency", signature = "clearReceiverFrequency()",
            description = "Clears the receiver frequency.")
    public final void clearReceiverFrequency() { shared.clearReceiverFrequency(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getReceiverFrequency", signature = "getReceiverFrequency(): table",
            description = "Returns the receiver frequency.")
    public final Map<String, Object> getReceiverFrequency() { return shared.getReceiverFrequency(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
