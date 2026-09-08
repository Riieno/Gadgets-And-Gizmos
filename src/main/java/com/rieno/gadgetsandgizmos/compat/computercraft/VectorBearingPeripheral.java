package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Expose Vector Bearing controls and telemetry to ComputerCraft
@PeripheralTypeDoc("vector_bearing")
public class VectorBearingPeripheral extends GadgetsPeripheral<VectorBearingBlockEntity> {
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

    // Initialize the vector bearing peripheral
    public VectorBearingPeripheral(VectorBearingBlockEntity blockEntity) {
        super(blockEntity, "vector_bearing");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMode", signature = "getMode(): string",
            description = "Returns the mode.")
    public final String getMode() {
        return blockEntity.getControlMode().name().toLowerCase(Locale.ROOT);
    }

    // Set the mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMode", signature = "setMode('auto'|'computer'|'redstone')",
            description = "Sets the mode.")
    public final void setMode(String modeName) throws LuaException {
        blockEntity.setControlMode(parseMode(modeName));
    }

    // Get the active mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getActiveMode", signature = "getActiveMode(): string",
            description = "Returns the active mode.")
    public final String getActiveMode() {
        return blockEntity.getActiveControlMode().name().toLowerCase(Locale.ROOT);
    }

    // Get the max tilt angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxTiltAngle", signature = "getMaxTiltAngle(): number",
            description = "Returns the maximum combined stabilization and manual tilt angle.")
    public final double getMaxTiltAngle() {
        return blockEntity.getMaxTiltDegrees();
    }

    // Set the max tilt angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMaxTiltAngle", signature = "setMaxTiltAngle(angleDegrees: number)",
            description = "Sets the maximum combined stabilization and manual tilt angle.")
    public final void setMaxTiltAngle(double angleDegrees) throws LuaException {
        if (!Double.isFinite(angleDegrees)) {
            throw new LuaException("angleDegrees must be finite");
        }
        blockEntity.setMaxTiltDegrees(angleDegrees);
    }

    // Get the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAngles", signature = "getAngles(): table",
            description = "Returns the angles.")
    public final Map<String, Object> getAngles() {
        return Map.of(
                "x", blockEntity.getAppliedXDegrees(),
                "z", blockEntity.getAppliedZDegrees(),
                "computerX", blockEntity.getComputerXDegrees(),
                "computerZ", blockEntity.getComputerZDegrees(),
                "computerOverride", blockEntity.hasComputerOverride());
    }

    // Set the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAngles", signature = "setAngles(xDegrees: number, zDegrees: number)",
            description = "Sets the angles.")
    public final void setAngles(double xDegrees, double zDegrees) throws LuaException {
        if (!Double.isFinite(xDegrees) || !Double.isFinite(zDegrees)) {
            throw new LuaException("xDegrees and zDegrees must be finite");
        }
        blockEntity.setComputerAnglesDegrees(xDegrees, zDegrees);
    }

    // Clear the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearAngles", signature = "clearAngles()",
            description = "Clears the angles.")
    public final void clearAngles() {
        blockEntity.clearComputerAngles();
    }

    // Get the stabilization axis
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStabilizeAxis", signature = "getStabilizeAxis(): string",
            description = "Returns the selected world-space stabilization axis or plane.")
    public final String getStabilizeAxis() {
        return blockEntity.getStabilizeAxis();
    }

    // Set the stabilization axis
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setStabilizeAxis", signature = "setStabilizeAxis('X Axis'|'Y Axis'|'Z Axis'|'XZ Axis'|'XY Axis'|'ZY Axis')",
            description = "Sets the world-space axis or plane held when stabilization is enabled.")
    public final void setStabilizeAxis(String axis) throws LuaException {
        if (!blockEntity.setStabilizeAxis(axis)) {
            throw new LuaException("axis must be 'X Axis', 'Y Axis', 'Z Axis', 'XZ Axis', 'XY Axis' or 'ZY Axis'");
        }
    }

    // Check whether stabilization is enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isKeepStable", signature = "isKeepStable(): boolean",
            description = "Returns whether the mounted head keeps its selected world axis stable.")
    public final boolean isKeepStable() {
        return blockEntity.isKeepStable();
    }

    // Set whether stabilization is enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setKeepStable", signature = "setKeepStable(keepStable: boolean)",
            description = "Enables or disables world-axis stabilization for the mounted head.")
    public final void setKeepStable(boolean keepStable) {
        blockEntity.setKeepStable(keepStable);
    }

    // Get the signals
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignals", signature = "getSignals(): table",
            description = "Returns the signals.")
    public final Map<String, Object> getSignals() {
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("north", blockEntity.getSignal(Direction.NORTH));
        signals.put("south", blockEntity.getSignal(Direction.SOUTH));
        signals.put("east", blockEntity.getSignal(Direction.EAST));
        signals.put("west", blockEntity.getSignal(Direction.WEST));
        return signals;
    }

    // Assemble the vector bearing peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "assemble", signature = "assemble(): boolean",
            description = "Assemble the vector bearing peripheral.")
    public final boolean assemble() {
        return blockEntity.tryAssembleMountedBlock();
    }

    // Disassemble the vector bearing peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "disassemble", signature = "disassemble()",
            description = "Disassemble the vector bearing peripheral.")
    public final void disassemble() {
        blockEntity.disassembleMountedBlock();
    }

    // Check if this is assembled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isAssembled", signature = "isAssembled(): boolean",
            description = "Returns whether this is assembled.")
    public final boolean isAssembled() {
        return blockEntity.isMountedAssemblyPresent();
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
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
        status.put("stabilizeAxis", blockEntity.getStabilizeAxis());
        status.put("keepStable", blockEntity.isKeepStable());
        status.put("assembled", blockEntity.isMountedAssemblyPresent());
        status.put("signals", getSignals());
        return status;
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
