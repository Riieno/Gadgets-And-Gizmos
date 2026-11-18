package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.AileronBearingPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;
import java.util.Optional;

// Expose one aileron target and its live angle to ComputerCraft
@PeripheralTypeDoc("aileron_bearing")
public class AileronBearingPeripheral extends BearingThrusterPeripheral<AileronBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final AileronBearingPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public AileronBearingPeripheral(AileronBearingBlockEntity blockEntity) {
        super(blockEntity, "aileron_bearing");
        this.shared = new AileronBearingPeripheralControl(blockEntity);
    }

    @Override
    protected AssemblyThrusterControl control(){
        return blockEntity.getBearingAssembly().controls();
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getHeadMode", signature = "getHeadMode(): string",
            description = "Returns the head mode.")
    public final String getHeadMode() { return shared.getHeadMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setHeadMode", signature = "setHeadMode(mode: 'single'|'mirrored'|'opposed'|'precise'|'free_single'|'free_mirrored'|'free_opposed')",
            description = "Sets the head mode.")
    public final void setHeadMode(String modeName) throws LuaException {
        try{ shared.setHeadMode(modeName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    public final String getControlMode() { return shared.getControlMode(); }

    public final void setControlMode(String modeName) throws LuaException {
        try{ shared.setControlMode(modeName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(value = "getControlMode", mainThread = true)
    @PeripheralDoc(name = "getControlMode", signature = "getControlMode([id|'all']): string|table",
            description = "Returns the bearing mode without an ID, or attached thruster modes with an ID.")
    public final Object getLuaControlMode(Optional<String> id) throws LuaException{
        try{ return shared.getLuaControlMode(id); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(value = "setControlMode", mainThread = true)
    @PeripheralDoc(name = "setControlMode", signature = "setControlMode(mode) | setControlMode(id|'all', mode)",
            description = "Sets the bearing mode with one argument, or attached thruster modes with two arguments.")
    public final void setLuaControlMode(String idOrMode, Optional<String> mode) throws LuaException{
        try{ shared.setLuaControlMode(idOrMode, mode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getActiveControlMode", signature = "getActiveControlMode(): string",
            description = "Returns the active control mode.")
    public final String getActiveControlMode() { return shared.getActiveControlMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAngles", signature = "getAngles(): table",
            description = "Returns the angles.")
    public final Map<String, Object> getAngles() { return shared.getAngles(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAngles", signature = "setAngles(primaryDegrees: number, secondaryDegrees: number)",
            description = "Sets the angles.")
    public final void setAngles(double primaryDegrees, double secondaryDegrees) throws LuaException {
        try{ shared.setAngles(primaryDegrees, secondaryDegrees); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setHeadAngle", signature = "setHeadAngle(head: string, angleDegrees: number)",
            description = "Sets the head angle.")
    public final void setHeadAngle(String headName, double angleDegrees) throws LuaException {
        try{ shared.setHeadAngle(headName, angleDegrees); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearHeadAngle", signature = "clearHeadAngle(head: string)",
            description = "Clears the head angle.")
    public final void clearHeadAngle(String headName) throws LuaException {
        try{ shared.clearHeadAngle(headName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearAngles", signature = "clearAngles()",
            description = "Clears the angles.")
    public final void clearAngles() { shared.clearAngles(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRanges", signature = "getRanges(): table",
            description = "Returns the ranges.")
    public final Map<String, Object> getRanges() { return shared.getRanges(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setHeadRange", signature = "setHeadRange(head: string, minDegrees: number, maxDegrees: number)",
            description = "Sets the head range.")
    public final void setHeadRange(String headName, double minDegrees, double maxDegrees) throws LuaException {
        try{ shared.setHeadRange(headName, minDegrees, maxDegrees); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignals", signature = "getSignals(): table",
            description = "Returns the signals.")
    public final Map<String, Object> getSignals() { return shared.getSignals(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "assemble", signature = "assemble(head: string): boolean",
            description = "Assemble the aileron bearing peripheral.")
    public final boolean assemble(String headName) throws LuaException {
        try{ return shared.assemble(headName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "disassemble", signature = "disassemble(head: string)",
            description = "Disassemble the aileron bearing peripheral.")
    public final void disassemble(String headName) throws LuaException {
        try{ shared.disassemble(headName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isAssembled", signature = "isAssembled(head: string): boolean",
            description = "Returns whether this is assembled.")
    public final boolean isAssembled(String headName) throws LuaException {
        try{ return shared.isAssembled(headName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAssemblies", signature = "getAssemblies(): table",
            description = "Returns the assemblies.")
    public final Map<String, Object> getAssemblies() { return shared.getAssemblies(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
