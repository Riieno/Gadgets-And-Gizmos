package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.RcsThrusterPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;

// Expose RCS Thruster controls and telemetry to ComputerCraft
@PeripheralTypeDoc("rcs_thruster")
public class RcsThrusterPeripheral extends GadgetsPeripheral<RcsThrusterBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final RcsThrusterPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public RcsThrusterPeripheral(RcsThrusterBlockEntity blockEntity) {
        super(blockEntity, "rcs_thruster");
        this.shared = new RcsThrusterPeripheralControl(blockEntity);
    }

    @LuaFunction
    @PeripheralDoc(name = "setThrottle", signature = "setThrottle(nozzle: 'north'|'east'|'south'|'west', throttle: number 0..1)",
            description = "Sets the throttle.")
    public final void setThrottle(String nozzle, double throttle) throws LuaException {
        try{ shared.setThrottle(nozzle, throttle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction
    @PeripheralDoc(name = "getThrottle", signature = "getThrottle(nozzle): number",
            description = "Returns the throttle.")
    public final double getThrottle(String nozzle) throws LuaException {
        try{ return shared.getThrottle(nozzle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction
    @PeripheralDoc(name = "clearThrottle", signature = "clearThrottle(nozzle)",
            description = "Clears the throttle.")
    public final void clearThrottle(String nozzle) throws LuaException {
        try{ shared.clearThrottle(nozzle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction
    @PeripheralDoc(name = "clearAllThrottles", signature = "clearAllThrottles()",
            description = "Clears every thruster throttle.")
    public final void clearAllThrottles() { shared.clearAllThrottles(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrust", signature = "getThrust(nozzle): number",
            description = "Returns the thrust.")
    public final double getThrust(String nozzle) throws LuaException {
        try{ return shared.getThrust(nozzle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxThrust", signature = "getMaxThrust(): number",
            description = "Returns the max thrust.")
    public final double getMaxThrust() { return shared.getMaxThrust(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRPM", signature = "getRPM(): number",
            description = "Returns the RPM.")
    public final double getRPM() { return shared.getRPM(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(nozzle): boolean",
            description = "Returns whether this is active.")
    public final boolean isActive(String nozzle) throws LuaException {
        try{ return shared.isActive(nozzle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
