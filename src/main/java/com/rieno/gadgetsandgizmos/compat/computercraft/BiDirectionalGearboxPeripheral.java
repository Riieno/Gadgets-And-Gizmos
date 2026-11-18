package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.BiDirectionalGearboxPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;
import java.util.Optional;
import static java.util.Map.entry;

// Expose Bidirectional Gearbox controls and telemetry to ComputerCraft
@PeripheralTypeDoc("bidirectional_gearbox")
public class BiDirectionalGearboxPeripheral extends GadgetsPeripheral<BiDirectionalGearboxBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final BiDirectionalGearboxPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public BiDirectionalGearboxPeripheral(BiDirectionalGearboxBlockEntity blockEntity) {
        super(blockEntity, "bidirectional_gearbox");
        this.shared = new BiDirectionalGearboxPeripheralControl(blockEntity);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isGyroMode", signature = "isGyroMode(): boolean",
            description = "Returns whether this is a gyro mode.")
    public final boolean isGyroMode() { return shared.isGyroMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isServoMode", signature = "isServoMode(): boolean",
            description = "Returns whether this is a servo mode.")
    public final boolean isServoMode() { return shared.isServoMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasGyroSource", signature = "hasGyroSource(): boolean",
            description = "Returns whether this has gyro source.")
    public final boolean hasGyroSource() { return shared.hasGyroSource(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMode", signature = "getMode(): string",
            description = "Returns the mode.")
    public final String getMode() { return shared.getMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMode", signature = "setMode(mode:string)",
            description = "Sets the mode.")
    public final void setMode(String mode) throws LuaException {
        try{ shared.setMode(mode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLaneMode", signature = "getLaneMode(axis:string): string",
            description = "Returns the lane mode.")
    public final String getLaneMode(String axis) throws LuaException {
        try{ return shared.getLaneMode(axis); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setLaneMode", signature = "setLaneMode(axis:string, mode:string)",
            description = "Sets the lane mode.")
    public final void setLaneMode(String axis, String mode) throws LuaException {
        try{ shared.setLaneMode(axis, mode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isReverseMode", signature = "isReverseMode(): boolean",
            description = "Returns whether this is a reverse mode.")
    public final boolean isReverseMode() { return shared.isReverseMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSpeed", signature = "getSpeed(): number",
            description = "Returns the speed.")
    public final double getSpeed() { return shared.getSpeed(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignal", signature = "getSignal(face:string): number",
            description = "Returns the signal.")
    public final int getSignal(String face) throws LuaException {
        try{ return shared.getSignal(face); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFaceAngle", signature = "getFaceAngle(face:string): number",
            description = "Returns the face angle.")
    public final double getFaceAngle(String face) throws LuaException {
        try{ return shared.getFaceAngle(face); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFaceMaxAngle", signature = "getFaceMaxAngle(face:string): number",
            description = "Returns the face max angle.")
    public final double getFaceMaxAngle(String face) throws LuaException {
        try{ return shared.getFaceMaxAngle(face); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setFaceAngle", signature = "setFaceAngle(face:string, angle:number)",
            description = "Sets the face angle.")
    public final void setFaceAngle(String face, double angle) throws LuaException {
        try{ shared.setFaceAngle(face, angle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setFaceMaxAngle", signature = "setFaceMaxAngle(face:string, angle:number)",
            description = "Sets the face max angle.")
    public final void setFaceMaxAngle(String face, double angle) throws LuaException {
        try{ shared.setFaceMaxAngle(face, angle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearFaceAngle", signature = "clearFaceAngle(face?:string)",
            description = "Clears the face angle.")
    public final void clearFaceAngle(Optional<String> face) throws LuaException {
        try{ shared.clearFaceAngle(face); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearFaceMaxAngle", signature = "clearFaceMaxAngle(face?:string)",
            description = "Clears the face max angle.")
    public final void clearFaceMaxAngle(Optional<String> face) throws LuaException {
        try{ shared.clearFaceMaxAngle(face); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLaneSpeed", signature = "getLaneSpeed(axis:string): number",
            description = "Returns the lane speed.")
    public final double getLaneSpeed(String axis) throws LuaException {
        try{ return shared.getLaneSpeed(axis); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
