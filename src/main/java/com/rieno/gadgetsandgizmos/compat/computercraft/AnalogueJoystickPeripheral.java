package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Expose Analogue Joystick controls and telemetry to ComputerCraft
@PeripheralTypeDoc("analogue_joystick")
public class AnalogueJoystickPeripheral extends GadgetsPeripheral<AnalogueJoystickBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick peripheral
    public AnalogueJoystickPeripheral(AnalogueJoystickBlockEntity blockEntity) {
        super(blockEntity, "analogue_joystick");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Custom joystick name or an empty string.")
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name:string)",
            description = "Sets or clear the custom joystick name.")
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the tilt
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTilt", signature = "getTilt(): table",
            description = "{x, z, magnitude, held, active}; x and z are local axes from -1 to 1.")
    public final Map<String, Object> getTilt() {
        DirectionalAnalogSnapshot snapshot = blockEntity.getDirectionalAnalogSnapshot();
        Map<String, Object> tilt = new LinkedHashMap<>();
        tilt.put("x", snapshot.localX());
        tilt.put("z", snapshot.localZ());
        tilt.put("magnitude", snapshot.magnitude());
        tilt.put("held", blockEntity.isHeld());
        tilt.put("active", blockEntity.isDirectionalAnalogActive());
        return tilt;
    }

    // Get the tilt degrees
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTiltDegrees", signature = "getTiltDegrees(): table",
            description = "{x, z, max}; sensor-equivalent tilt angles in degrees.")
    public final Map<String, Object> getTiltDegrees() {
        DirectionalAnalogSnapshot snapshot = blockEntity.getDirectionalAnalogSnapshot();
        double maxTilt = blockEntity.getMaxTiltDegrees();
        Map<String, Object> tilt = new LinkedHashMap<>();
        tilt.put("x", -snapshot.localZ() * maxTilt);
        tilt.put("z", -snapshot.localX() * maxTilt);
        tilt.put("max", maxTilt);
        return tilt;
    }

    // Get the x
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getX", signature = "getX(): number",
            description = "Local left/right tilt from -1 to 1.")
    public final double getX() {
        return blockEntity.getDirectionalAnalogSnapshot().localX();
    }

    // Get the z
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getZ", signature = "getZ(): number",
            description = "Local forward/backward tilt from -1 to 1.")
    public final double getZ() {
        return blockEntity.getDirectionalAnalogSnapshot().localZ();
    }

    // Get the redstone
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstone", signature = "getRedstone(): table",
            description = "{forward, backward, left, right, max}; each value is 0 to 15.")
    public final Map<String, Object> getRedstone() {
        DirectionalAnalogSnapshot snapshot = blockEntity.getDirectionalAnalogSnapshot();
        Map<String, Object> redstone = new LinkedHashMap<>();
        redstone.put("forward", snapshot.forwardRedstone());
        redstone.put("backward", snapshot.backwardRedstone());
        redstone.put("left", snapshot.leftRedstone());
        redstone.put("right", snapshot.rightRedstone());
        redstone.put("max", Math.max(
                Math.max(snapshot.forwardRedstone(), snapshot.backwardRedstone()),
                Math.max(snapshot.leftRedstone(), snapshot.rightRedstone())));
        return redstone;
    }

    // Get the redstone output
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstoneOutput", signature = "getRedstoneOutput(channel:string): number",
            description = "One directional redstone value from 0 to 15.")
    public final int getRedstoneOutput(String channel) throws LuaException {
        DirectionalAnalogSnapshot snapshot = blockEntity.getDirectionalAnalogSnapshot();
        return switch (normalizeChannel(channel)) {
            case "forward" -> snapshot.forwardRedstone();
            case "backward" -> snapshot.backwardRedstone();
            case "left" -> snapshot.leftRedstone();
            case "right" -> snapshot.rightRedstone();
            default -> throw new LuaException("channel must be 'forward', 'backward', 'left', or 'right'");
        };
    }

    // Check if this is held
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isHeld", signature = "isHeld(): boolean",
            description = "True while a player is actively dragging the joystick.")
    public final boolean isHeld() {
        return blockEntity.isHeld();
    }

    // Check if this is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(): boolean",
            description = "True while held or while a latched non-zero tilt remains.")
    public final boolean isActive() {
        return blockEntity.isDirectionalAnalogActive();
    }

    // Get the deadzone
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getDeadzone", signature = "getDeadzone(): number",
            description = "Configured neutral deadzone from 0 to 0.95.")
    public final double getDeadzone() {
        return blockEntity.getDeadzone();
    }

    // Get the max tilt degrees
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxTiltDegrees", signature = "getMaxTiltDegrees(): number",
            description = "Configured maximum sensor-equivalent tilt angle.")
    public final double getMaxTiltDegrees() {
        return blockEntity.getMaxTiltDegrees();
    }

    // Get the release mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getReleaseMode", signature = "getReleaseMode(): string",
            description = "'latched' or 'momentary'.")
    public final String getReleaseMode() {
        return blockEntity.getReleaseMode().name().toLowerCase(Locale.ROOT);
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Combined name, tilt, redstone, and configuration table.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("name", getName());
        status.put("tilt", getTilt());
        status.put("tiltDegrees", getTiltDegrees());
        status.put("redstone", getRedstone());
        status.put("deadzone", getDeadzone());
        status.put("maxTiltDegrees", getMaxTiltDegrees());
        status.put("releaseMode", getReleaseMode());
        return status;
    }

    // Normalize the channel
    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }
}
