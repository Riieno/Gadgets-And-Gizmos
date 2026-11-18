package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.WheelMountPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

// Expose Wheel Mount controls and telemetry to ComputerCraft
@PeripheralTypeDoc("wheel_mount")
public class WheelMountPeripheral extends GadgetsPeripheral<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final WheelMountPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public WheelMountPeripheral(BlockEntity blockEntity) {
        super(blockEntity, "wheel_mount");
        this.shared = new WheelMountPeripheralControl(blockEntity);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setLeft", signature = "setLeft(value: number)",
            description = "Sets the left.")
    public final void setLeft(double value) throws LuaException {
        try{ shared.setLeft(value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setRight", signature = "setRight(value: number)",
            description = "Sets the right.")
    public final void setRight(double value) throws LuaException {
        try{ shared.setRight(value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setBrake", signature = "setBrake(value: number)",
            description = "Sets the brake.")
    public final void setBrake(double value) throws LuaException {
        try{ shared.setBrake(value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControls", signature = "setControls(left: number, right: number, brake: number)",
            description = "Sets the controls.")
    public final void setControls(double left, double right, double brake) throws LuaException {
        try{ shared.setControls(left, right, brake); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearControls", signature = "clearControls()",
            description = "Clears the controls.")
    public final void clearControls() throws LuaException {
        try{ shared.clearControls(); }
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
