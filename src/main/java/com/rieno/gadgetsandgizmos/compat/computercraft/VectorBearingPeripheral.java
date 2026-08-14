package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Expose Vector Bearing controls and telemetry to ComputerCraft
public class VectorBearingPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final VectorBearingBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vector bearing peripheral
    public VectorBearingPeripheral(VectorBearingBlockEntity blockEntity) {
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
        return "vector_bearing";
    }

    // Compare this vector bearing peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof VectorBearingPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Get the mode
    @LuaFunction
    public final String getMode() {
        return blockEntity.getControlMode().name().toLowerCase(Locale.ROOT);
    }

    // Set the mode
    @LuaFunction
    public final void setMode(String modeName) throws LuaException {
        blockEntity.setControlMode(parseMode(modeName));
    }

    // Get the active mode
    @LuaFunction
    public final String getActiveMode() {
        return blockEntity.getActiveControlMode().name().toLowerCase(Locale.ROOT);
    }

    // Get the max tilt angle
    @LuaFunction
    public final double getMaxTiltAngle() {
        return blockEntity.getMaxTiltDegrees();
    }

    // Set the max tilt angle
    @LuaFunction
    public final void setMaxTiltAngle(double angleDegrees) throws LuaException {
        if (!Double.isFinite(angleDegrees)) {
            throw new LuaException("angleDegrees must be finite");
        }
        blockEntity.setMaxTiltDegrees(angleDegrees);
    }

    // Get the angles
    @LuaFunction
    public final Map<String, Object> getAngles() {
        return Map.of(
                "x", blockEntity.getAppliedXDegrees(),
                "z", blockEntity.getAppliedZDegrees(),
                "computerX", blockEntity.getComputerXDegrees(),
                "computerZ", blockEntity.getComputerZDegrees(),
                "computerOverride", blockEntity.hasComputerOverride());
    }

    // Set the angles
    @LuaFunction
    public final void setAngles(double xDegrees, double zDegrees) throws LuaException {
        if (!Double.isFinite(xDegrees) || !Double.isFinite(zDegrees)) {
            throw new LuaException("xDegrees and zDegrees must be finite");
        }
        blockEntity.setComputerAnglesDegrees(xDegrees, zDegrees);
    }

    // Clear the angles
    @LuaFunction
    public final void clearAngles() {
        blockEntity.clearComputerAngles();
    }

    // Get the signals
    @LuaFunction
    public final Map<String, Object> getSignals() {
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("north", blockEntity.getSignal(Direction.NORTH));
        signals.put("south", blockEntity.getSignal(Direction.SOUTH));
        signals.put("east", blockEntity.getSignal(Direction.EAST));
        signals.put("west", blockEntity.getSignal(Direction.WEST));
        return signals;
    }

    // Assemble the vector bearing peripheral
    @LuaFunction
    public final boolean assemble() {
        return blockEntity.tryAssembleMountedBlock();
    }

    // Disassemble the vector bearing peripheral
    @LuaFunction
    public final void disassemble() {
        blockEntity.disassembleMountedBlock();
    }

    // Check if this is assembled
    @LuaFunction
    public final boolean isAssembled() {
        return blockEntity.isMountedAssemblyPresent();
    }

    // Get the status
    @LuaFunction
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", getMode());
        status.put("activeMode", getActiveMode());
        status.put("maxTiltAngle", blockEntity.getMaxTiltDegrees());
        status.put("xAngle", blockEntity.getAppliedXDegrees());
        status.put("zAngle", blockEntity.getAppliedZDegrees());
        status.put("computerXAngle", blockEntity.getComputerXDegrees());
        status.put("computerZAngle", blockEntity.getComputerZDegrees());
        status.put("computerOverride", blockEntity.hasComputerOverride());
        status.put("assembled", blockEntity.isMountedAssemblyPresent());
        status.put("signals", getSignals());
        return status;
    }

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "getMode", "setMode", "getActiveMode",
                "getMaxTiltAngle", "setMaxTiltAngle",
                "getAngles", "setAngles", "clearAngles",
                "getSignals", "assemble", "disassemble", "isAssembled", "getStatus");
    }

    // Get the help
    @LuaFunction
    public final Map<String, String> help() {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("setMode", "setMode('auto'|'computer'|'redstone')");
        help.put("setAngles", "setAngles(xDegrees, zDegrees) sets computer-mode tilt input");
        help.put("setMaxTiltAngle", "setMaxTiltAngle(angleDegrees) clamps to 0..89");
        help.put("assemble", "assemble() attempts to mount the block on the bearing-facing side");
        help.put("disassemble", "disassemble() returns the mounted sublevel to the bearing-facing side");
        return help;
    }

    // Parse the mode
    private static VectorBearingBlockEntity.ControlMode parseMode(String modeName) throws LuaException {
        if (modeName == null) {
            throw new LuaException("mode must be 'auto', 'computer' or 'redstone'");
        }
        return switch (modeName.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> VectorBearingBlockEntity.ControlMode.AUTO;
            case "computer", "cc" -> VectorBearingBlockEntity.ControlMode.COMPUTER;
            case "redstone" -> VectorBearingBlockEntity.ControlMode.REDSTONE;
            default -> throw new LuaException("mode must be 'auto', 'computer' or 'redstone'");
        };
    }
}
