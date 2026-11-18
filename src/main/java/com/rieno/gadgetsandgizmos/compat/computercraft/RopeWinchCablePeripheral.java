package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.RopeWinchCablePeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.DocumentedPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Expose Rope Winch Cable controls and telemetry to ComputerCraft
@PeripheralTypeDoc("rope_winch_cable")
public class RopeWinchCablePeripheral implements DocumentedPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final RopeWinchPeripheralBridge winch;
    private final RopeWinchCablePeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public RopeWinchCablePeripheral(RopeWinchPeripheralBridge winch) {
        this.winch = winch;
        this.shared = new RopeWinchCablePeripheralControl(winch);
    }

    @Override
    public String getType() { return shared.getType(); }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof RopeWinchCablePeripheral peripheral
                && peripheral.winch == winch;
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnected", signature = "isConnected(): boolean",
            description = "Returns whether this is connected.")
    public final boolean isConnected() { return shared.isConnected(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRemoteType", signature = "getRemoteType(): string",
            description = "Returns the remote type.")
    public final String getRemoteType() { return shared.getRemoteType(); }

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
    public final void clearSignalOverride() throws LuaException {
        try{ shared.clearSignalOverride(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "open", signature = "open()",
            description = "Open the rope winch cable peripheral.")
    public final void open() throws LuaException {
        try{ shared.open(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "close", signature = "close()",
            description = "Close the rope winch cable peripheral.")
    public final void close() throws LuaException {
        try{ shared.close(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "release", signature = "release()",
            description = "Release the rope winch cable peripheral.")
    public final void release() throws LuaException {
        try{ shared.release(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns the signal.")
    public final int getSignal() throws LuaException {
        try{ return shared.getSignal(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isHolding", signature = "isHolding(): boolean",
            description = "Returns whether this is holding.")
    public final boolean isHolding() throws LuaException {
        try{ return shared.isHolding(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getHeldConnectorPos", signature = "getHeldConnectorPos(): table",
            description = "Returns the held connector pos.")
    public final Map<String, Object> getHeldConnectorPos() throws LuaException {
        try{ return shared.getHeldConnectorPos(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedConnectorPos", signature = "getSelectedConnectorPos(): table",
            description = "Returns the selected connector pos.")
    public final Map<String, Object> getSelectedConnectorPos() throws LuaException {
        try{ return shared.getSelectedConnectorPos(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnector", signature = "getNearestConnector(): table",
            description = "Returns the nearest connector.")
    public final Map<String, Object> getNearestConnector() throws LuaException {
        try{ return shared.getNearestConnector(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNearestConnectorInRange", signature = "getNearestConnectorInRange(range: number): table",
            description = "Returns the nearest connector in range.")
    public final Map<String, Object> getNearestConnectorInRange(int range) throws LuaException {
        try{ return shared.getNearestConnectorInRange(range); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRange", signature = "getConnectorsInRange(range: number): table",
            description = "Returns the connectors in range.")
    public final List<Map<String, Object>> getConnectorsInRange(int range) throws LuaException {
        try{ return shared.getConnectorsInRange(range); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getConnectorsInRangeLimited", signature = "getConnectorsInRangeLimited(range: number, limit: number): table",
            description = "Returns the connectors in range limited.")
    public final List<Map<String, Object>> getConnectorsInRangeLimited(int range, int limit) throws LuaException {
        try{ return shared.getConnectorsInRangeLimited(range, limit); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRange", signature = "isConnectorInRange(x: number, y: number, z: number): boolean",
            description = "Returns whether the connector is in range.")
    public final boolean isConnectorInRange(int x, int y, int z) throws LuaException {
        try{ return shared.isConnectorInRange(x, y, z); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isConnectorInRangeWithRadius", signature = "isConnectorInRangeWithRadius(x: number, y: number, z: number, range: number): boolean",
            description = "Returns whether the connector is in the range with radius.")
    public final boolean isConnectorInRangeWithRadius(int x, int y, int z, int range) throws LuaException {
        try{ return shared.isConnectorInRangeWithRadius(x, y, z, range); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "selectConnector", signature = "selectConnector(x: number, y: number, z: number): boolean",
            description = "Select the connector.")
    public final boolean selectConnector(int x, int y, int z) throws LuaException {
        try{ return shared.selectConnector(x, y, z); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

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
    public final void clearSelectedConnector() throws LuaException {
        try{ shared.clearSelectedConnector(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() throws LuaException {
        try{ return shared.getStatus(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

}
