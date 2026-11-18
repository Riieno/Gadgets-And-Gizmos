package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.AnalogueJoystickPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;

// Expose Analogue Joystick controls and telemetry to ComputerCraft
@PeripheralTypeDoc("analogue_joystick")
public class AnalogueJoystickPeripheral extends GadgetsPeripheral<AnalogueJoystickBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final AnalogueJoystickPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public AnalogueJoystickPeripheral(AnalogueJoystickBlockEntity blockEntity) {
        super(blockEntity, "analogue_joystick");
        this.shared = new AnalogueJoystickPeripheralControl(blockEntity);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Custom joystick name or an empty string.")
    public final String getName() { return shared.getName(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name:string)",
            description = "Sets or clear the custom joystick name.")
    public final void setName(String name) { shared.setName(name); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTilt", signature = "getTilt(): table",
            description = "{x, z, magnitude, held, active}; x and z are local axes from -1 to 1.")
    public final Map<String, Object> getTilt() { return shared.getTilt(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTiltDegrees", signature = "getTiltDegrees(): table",
            description = "{x, z, max}; sensor-equivalent tilt angles in degrees.")
    public final Map<String, Object> getTiltDegrees() { return shared.getTiltDegrees(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getX", signature = "getX(): number",
            description = "Local left/right tilt from -1 to 1.")
    public final double getX() { return shared.getX(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getZ", signature = "getZ(): number",
            description = "Local forward/backward tilt from -1 to 1.")
    public final double getZ() { return shared.getZ(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstone", signature = "getRedstone(): table",
            description = "{forward, backward, left, right, max}; each value is 0 to 15.")
    public final Map<String, Object> getRedstone() { return shared.getRedstone(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstoneOutput", signature = "getRedstoneOutput(channel:string): number",
            description = "One directional redstone value from 0 to 15.")
    public final int getRedstoneOutput(String channel) throws LuaException {
        try{ return shared.getRedstoneOutput(channel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isHeld", signature = "isHeld(): boolean",
            description = "True while a player is actively dragging the joystick.")
    public final boolean isHeld() { return shared.isHeld(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(): boolean",
            description = "True while held or while a latched non-zero tilt remains.")
    public final boolean isActive() { return shared.isActive(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getDeadzone", signature = "getDeadzone(): number",
            description = "Configured neutral deadzone from 0 to 0.95.")
    public final double getDeadzone() { return shared.getDeadzone(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxTiltDegrees", signature = "getMaxTiltDegrees(): number",
            description = "Configured maximum sensor-equivalent tilt angle.")
    public final double getMaxTiltDegrees() { return shared.getMaxTiltDegrees(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getReleaseMode", signature = "getReleaseMode(): string",
            description = "'latched' or 'momentary'.")
    public final String getReleaseMode() { return shared.getReleaseMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Combined name, tilt, redstone, and configuration table.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
