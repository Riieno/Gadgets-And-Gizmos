package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.GyroscopeLinkPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Expose Gyroscope Link controls and telemetry to ComputerCraft
@PeripheralTypeDoc({"advanced_data_link", "gyroscope_link"})
public class GyroscopeLinkPeripheral extends GadgetsPeripheral<GyroscopeLinkBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final GyroscopeLinkPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public GyroscopeLinkPeripheral(GyroscopeLinkBlockEntity blockEntity) {
        super(blockEntity, "advanced_data_link");
        this.shared = new GyroscopeLinkPeripheralControl(blockEntity);
    }

    @Override
    public Set<String> getAdditionalTypes() { return shared.getAdditionalTypes(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isLinked", signature = "isLinked(): boolean",
            description = "Returns whether this is linked.")
    public final boolean isLinked() { return shared.isLinked(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTarget", signature = "getTarget(): table",
            description = "Returns the target.")
    public final Map<String, Object> getTarget() { return shared.getTarget(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAngles", signature = "getAngles(): table",
            description = "Returns the angles.")
    public final Map<String, Object> getAngles() { return shared.getAngles(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getDirection", signature = "getDirection(): table",
            description = "Returns the direction.")
    public final Map<String, Object> getDirection() { return shared.getDirection(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMode", signature = "getMode(): string",
            description = "Returns the mode.")
    public final String getMode() { return shared.getMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMode", signature = "setMode(mode:string)",
            description = "Sets the mode.")
    public final void setMode(String modeName) throws LuaException {
        try{ shared.setMode(modeName); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setTarget", signature = "setTarget(x:number,y:number,z:number,dimension?:string)",
            description = "Sets the target.")
    public final void setTarget(int x, int y, int z, Optional<String> dimension) throws LuaException {
        try{ shared.setTarget(x, y, z, dimension); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearTarget", signature = "clearTarget()",
            description = "Clears the target.")
    public final void clearTarget() { shared.clearTarget(); }

}
