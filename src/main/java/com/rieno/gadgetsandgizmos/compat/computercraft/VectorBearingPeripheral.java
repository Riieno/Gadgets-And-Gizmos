package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.VectorBearingPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;

// Expose Vector Bearing controls and telemetry to ComputerCraft
@PeripheralTypeDoc("vector_bearing")
public class VectorBearingPeripheral extends BearingThrusterPeripheral<VectorBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final VectorBearingPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public VectorBearingPeripheral(VectorBearingBlockEntity blockEntity) {
        super(blockEntity, "vector_bearing");
        this.shared = new VectorBearingPeripheralControl(blockEntity);
    }

    @Override
    protected AssemblyThrusterControl control(){
        return blockEntity.getBearingAssembly().controls();
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getControlMode", signature = "getControlMode(id|'all'): string|table",
            description = "Returns the control mode.")
    public final Object getControlMode(String thrusterId) throws LuaException{
        try{ return shared.getControlMode(thrusterId); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControlMode", signature = "setControlMode(id|'all', mode: 'auto'|'redstone'|'computer')",
            description = "Sets the control mode.")
    public final void setControlMode(String thrusterId, String mode) throws LuaException{
        try{ shared.setControlMode(thrusterId, mode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMode", signature = "getMode(): string",
            description = "Returns the mode.")
    public final String getMode() { return shared.getMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMode", signature = "setMode('auto'|'computer'|'redstone')",
            description = "Sets the mode.")
    public final void setMode(String modeName) throws LuaException {
        try{ shared.setMode(modeName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getActiveMode", signature = "getActiveMode(): string",
            description = "Returns the active mode.")
    public final String getActiveMode() { return shared.getActiveMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxTiltAngle", signature = "getMaxTiltAngle(): number",
            description = "Returns the maximum combined stabilization and manual tilt angle.")
    public final double getMaxTiltAngle() { return shared.getMaxTiltAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMaxTiltAngle", signature = "setMaxTiltAngle(angleDegrees: number)",
            description = "Sets the maximum combined stabilization and manual tilt angle.")
    public final void setMaxTiltAngle(double angleDegrees) throws LuaException {
        try{ shared.setMaxTiltAngle(angleDegrees); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAngles", signature = "getAngles(): table",
            description = "Returns the angles.")
    public final Map<String, Object> getAngles() { return shared.getAngles(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAngles", signature = "setAngles(xDegrees: number, zDegrees: number)",
            description = "Sets the angles.")
    public final void setAngles(double xDegrees, double zDegrees) throws LuaException {
        try{ shared.setAngles(xDegrees, zDegrees); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearAngles", signature = "clearAngles()",
            description = "Clears the angles.")
    public final void clearAngles() { shared.clearAngles(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStabilizeAxis", signature = "getStabilizeAxis(): string",
            description = "Returns the selected world-space stabilization axis or plane.")
    public final String getStabilizeAxis() { return shared.getStabilizeAxis(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setStabilizeAxis", signature = "setStabilizeAxis('X Axis'|'Y Axis'|'Z Axis'|'XZ Axis'|'XY Axis'|'ZY Axis')",
            description = "Sets the world-space axis or plane held when stabilization is enabled.")
    public final void setStabilizeAxis(String axis) throws LuaException {
        try{ shared.setStabilizeAxis(axis); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isKeepStable", signature = "isKeepStable(): boolean",
            description = "Returns whether the mounted head keeps its selected world axis stable.")
    public final boolean isKeepStable() { return shared.isKeepStable(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setKeepStable", signature = "setKeepStable(keepStable: boolean)",
            description = "Enables or disables world-axis stabilization for the mounted head.")
    public final void setKeepStable(boolean keepStable) { shared.setKeepStable(keepStable); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignals", signature = "getSignals(): table",
            description = "Returns the signals.")
    public final Map<String, Object> getSignals() { return shared.getSignals(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "assemble", signature = "assemble(): boolean",
            description = "Assemble the vector bearing peripheral.")
    public final boolean assemble() { return shared.assemble(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "disassemble", signature = "disassemble()",
            description = "Disassemble the vector bearing peripheral.")
    public final void disassemble() { shared.disassemble(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isAssembled", signature = "isAssembled(): boolean",
            description = "Returns whether this is assembled.")
    public final boolean isAssembled() { return shared.isAssembled(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
