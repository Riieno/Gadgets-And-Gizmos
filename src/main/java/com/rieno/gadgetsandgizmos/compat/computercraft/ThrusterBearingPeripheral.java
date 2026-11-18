package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.ThrusterBearingPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;

// Expose bearing pose and attached-thruster control through a stable ComputerCraft API
@PeripheralTypeDoc("thruster_bearing")
public class ThrusterBearingPeripheral extends BearingThrusterPeripheral<ThrusterBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ThrusterBearingPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public ThrusterBearingPeripheral(ThrusterBearingBlockEntity blockEntity) {
        super(blockEntity, "thruster_bearing");
        this.shared = new ThrusterBearingPeripheralControl(blockEntity);
    }

    @Override
    protected AssemblyThrusterControl control(){
        return blockEntity.getBearingAssembly().controls();
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() { return shared.getName(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) { shared.setName(name); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getForwardSignal", signature = "getForwardSignal(): number",
            description = "Returns the forward signal.")
    public final int getForwardSignal() { return shared.getForwardSignal(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBackwardSignal", signature = "getBackwardSignal(): number",
            description = "Returns the backward signal.")
    public final int getBackwardSignal() { return shared.getBackwardSignal(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getPivotAngle", signature = "getPivotAngle(): number",
            description = "Returns the pivot angle.")
    public final double getPivotAngle() { return shared.getPivotAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCurrentAngle", signature = "getCurrentAngle(): number",
            description = "Returns the current angle.")
    public final double getCurrentAngle() { return shared.getCurrentAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetAngle", signature = "getTargetAngle(): number",
            description = "Returns the target angle.")
    public final double getTargetAngle() { return shared.getTargetAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBearingControlMode", signature = "getBearingControlMode(): string",
            description = "Returns the bearing control mode.")
    public final String getBearingControlMode() { return shared.getBearingControlMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getServoInputAngle", signature = "getServoInputAngle(): number",
            description = "Returns the servo input angle.")
    public final double getServoInputAngle() { return shared.getServoInputAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMinAngle", signature = "getMinAngle(): number",
            description = "Returns the min angle.")
    public final double getMinAngle() { return shared.getMinAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxAngle", signature = "getMaxAngle(): number",
            description = "Returns the max angle.")
    public final double getMaxAngle() { return shared.getMaxAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setPivotAngle", signature = "setPivotAngle(angleDeg: number)",
            description = "Sets the pivot angle.")
    public final void setPivotAngle(double angleDeg) throws LuaException {
        try{ shared.setPivotAngle(angleDeg); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setBearingControlMode", signature = "setBearingControlMode(mode: 'auto'|'redstone'|'computer'|'servo')",
            description = "Sets the bearing control mode.")
    public final void setBearingControlMode(String mode) throws LuaException {
        try{ shared.setBearingControlMode(mode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMinAngle", signature = "setMinAngle(angleDeg: number)",
            description = "Sets the min angle.")
    public final void setMinAngle(double angleDeg) throws LuaException {
        try{ shared.setMinAngle(angleDeg); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMaxAngle", signature = "setMaxAngle(angleDeg: number)",
            description = "Sets the max angle.")
    public final void setMaxAngle(double angleDeg) throws LuaException {
        try{ shared.setMaxAngle(angleDeg); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearPivotOverride", signature = "clearPivotOverride()",
            description = "Clears the pivot override.")
    public final void clearPivotOverride() { shared.clearPivotOverride(); }

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
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFacing", signature = "getFacing(): string",
            description = "Returns the facing.")
    public final String getFacing() { return shared.getFacing(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getWorldFacing", signature = "getWorldFacing(): table",
            description = "Returns the world facing.")
    public final Map<String, Object> getWorldFacing() { return shared.getWorldFacing(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setFacing", signature = "setFacing(direction: string)",
            description = "Sets the facing.")
    public final void setFacing(String direction) throws LuaException {
        try{ shared.setFacing(direction); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

}
