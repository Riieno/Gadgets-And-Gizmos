package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Expose Analogue Joystick controls and telemetry to ComputerCraft
public class AnalogueJoystickPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final AnalogueJoystickBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick peripheral
    public AnalogueJoystickPeripheral(AnalogueJoystickBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "analogue_joystick";
    }

    // Compare this analogue joystick peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof AnalogueJoystickPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Get the name
    @LuaFunction
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the tilt
    @LuaFunction
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
    @LuaFunction
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
    @LuaFunction
    public final double getX() {
        return blockEntity.getDirectionalAnalogSnapshot().localX();
    }

    // Get the z
    @LuaFunction
    public final double getZ() {
        return blockEntity.getDirectionalAnalogSnapshot().localZ();
    }

    // Get the redstone
    @LuaFunction
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
    @LuaFunction
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
    @LuaFunction
    public final boolean isHeld() {
        return blockEntity.isHeld();
    }

    // Check if this is active
    @LuaFunction
    public final boolean isActive() {
        return blockEntity.isDirectionalAnalogActive();
    }

    // Get the deadzone
    @LuaFunction
    public final double getDeadzone() {
        return blockEntity.getDeadzone();
    }

    // Get the max tilt degrees
    @LuaFunction
    public final double getMaxTiltDegrees() {
        return blockEntity.getMaxTiltDegrees();
    }

    // Get the release mode
    @LuaFunction
    public final String getReleaseMode() {
        return blockEntity.getReleaseMode().name().toLowerCase(Locale.ROOT);
    }

    // Get the status
    @LuaFunction
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

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "getName(): string",
                "setName(name:string)",
                "getTilt(): table",
                "getTiltDegrees(): table",
                "getX(): number",
                "getZ(): number",
                "getRedstone(): table",
                "getRedstoneOutput(channel:string): number",
                "isHeld(): boolean",
                "isActive(): boolean",
                "getDeadzone(): number",
                "getMaxTiltDegrees(): number",
                "getReleaseMode(): string",
                "getStatus(): table",
                "methods(): table",
                "help(method?:string): string|table"
        );
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) throws LuaException {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("getName", "getName() -> custom joystick name or an empty string");
        docs.put("setName", "setName(name) -> set or clear the custom joystick name");
        docs.put("getTilt", "getTilt() -> {x, z, magnitude, held, active}; x and z are local axes from -1 to 1");
        docs.put("getTiltDegrees", "getTiltDegrees() -> {x, z, max}; sensor-equivalent tilt angles in degrees");
        docs.put("getX", "getX() -> local left/right tilt from -1 to 1");
        docs.put("getZ", "getZ() -> local forward/backward tilt from -1 to 1");
        docs.put("getRedstone", "getRedstone() -> {forward, backward, left, right, max}; each value is 0 to 15");
        docs.put("getRedstoneOutput", "getRedstoneOutput(channel) -> one directional redstone value from 0 to 15");
        docs.put("isHeld", "isHeld() -> true while a player is actively dragging the joystick");
        docs.put("isActive", "isActive() -> true while held or while a latched non-zero tilt remains");
        docs.put("getDeadzone", "getDeadzone() -> configured neutral deadzone from 0 to 0.95");
        docs.put("getMaxTiltDegrees", "getMaxTiltDegrees() -> configured maximum sensor-equivalent tilt angle");
        docs.put("getReleaseMode", "getReleaseMode() -> 'latched' or 'momentary'");
        docs.put("getStatus", "getStatus() -> combined name, tilt, redstone, and configuration table");
        docs.put("methods", "methods() -> list of all callable joystick methods");
        docs.put("help", "help() -> all docs, help('name') -> one entry");
        if (method.isEmpty()) {
            return docs;
        }
        String entry = docs.get(method.get());
        if (entry == null) {
            throw new LuaException("unknown method '" + method.get() + "'");
        }
        return entry;
    }

    // Normalize the channel
    private static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }
}
