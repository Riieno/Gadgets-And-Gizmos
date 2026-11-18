package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptBlockPeripheral;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import net.minecraft.core.Direction;

import java.util.Map;
import java.util.Optional;
import static java.util.Map.entry;

// Expose Bidirectional Gearbox controls and telemetry to ComputerCraft
@PeripheralTypeDoc("bidirectional_gearbox")
public class BiDirectionalGearboxPeripheralControl extends ScriptBlockPeripheral<BiDirectionalGearboxBlockEntity> {
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

    // Initialize the bi directional gearbox peripheral
    public BiDirectionalGearboxPeripheralControl(BiDirectionalGearboxBlockEntity blockEntity) {
        super(blockEntity, "bidirectional_gearbox");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a gyro mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isGyroMode", signature = "isGyroMode(): boolean",
            description = "Returns whether this is a gyro mode.")
    public final boolean isGyroMode() {
        return blockEntity.isGyroMode();
    }

    // Check if this is a servo mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isServoMode", signature = "isServoMode(): boolean",
            description = "Returns whether this is a servo mode.")
    public final boolean isServoMode() {
        return blockEntity.isServoModeActive();
    }

    // Check if this has gyro source
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "hasGyroSource", signature = "hasGyroSource(): boolean",
            description = "Returns whether this has gyro source.")
    public final boolean hasGyroSource() {
        return blockEntity.hasGyroSource();
    }

    // Get the mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getMode", signature = "getMode(): string",
            description = "Returns the mode.")
    public final String getMode() {
        return blockEntity.getOperationModeName();
    }

    // Set the mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setMode", signature = "setMode(mode:string)",
            description = "Sets the mode.")
    public final void setMode(String mode) throws ScriptException {
        try {
            blockEntity.setOperationMode(mode);
        } catch (IllegalArgumentException e) {
            throw new ScriptException("unknown mode '" + mode + "'");
        }
    }

    // Get the lane mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getLaneMode", signature = "getLaneMode(axis:string): string",
            description = "Returns the lane mode.")
    public final String getLaneMode(String axis) throws ScriptException {
        return blockEntity.getLaneMode(parseAxis(axis)).name().toLowerCase();
    }

    // Set the lane mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setLaneMode", signature = "setLaneMode(axis:string, mode:string)",
            description = "Sets the lane mode.")
    public final void setLaneMode(String axis, String mode) throws ScriptException {
        blockEntity.setLaneMode(parseAxis(axis), parseEnum(mode, BiDirectionalGearboxBlockEntity.LaneMode.class, "lane mode"));
    }

    // Check if this is a reverse mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isReverseMode", signature = "isReverseMode(): boolean",
            description = "Returns whether this is a reverse mode.")
    public final boolean isReverseMode() {
        return blockEntity.isReverseMode();
    }

    // Get the speed
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getSpeed", signature = "getSpeed(): number",
            description = "Returns the speed.")
    public final double getSpeed() {
        return blockEntity.getSpeed();
    }

    // Get the signal
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getSignal", signature = "getSignal(face:string): number",
            description = "Returns the signal.")
    public final int getSignal(String face) throws ScriptException {
        return blockEntity.getOutputSignal(parseHorizontal(face));
    }

    // Get the face angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getFaceAngle", signature = "getFaceAngle(face:string): number",
            description = "Returns the face angle.")
    public final double getFaceAngle(String face) throws ScriptException {
        return blockEntity.getFaceAngle(parseHorizontal(face));
    }

    // Get the face max angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getFaceMaxAngle", signature = "getFaceMaxAngle(face:string): number",
            description = "Returns the face max angle.")
    public final double getFaceMaxAngle(String face) throws ScriptException {
        return blockEntity.getFaceMaxAngle(parseHorizontal(face));
    }

    // Set the face angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setFaceAngle", signature = "setFaceAngle(face:string, angle:number)",
            description = "Sets the face angle.")
    public final void setFaceAngle(String face, double angle) throws ScriptException {
        blockEntity.setManualFaceAngle(parseHorizontal(face), angle);
    }

    // Set the face max angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setFaceMaxAngle", signature = "setFaceMaxAngle(face:string, angle:number)",
            description = "Sets the face max angle.")
    public final void setFaceMaxAngle(String face, double angle) throws ScriptException {
        blockEntity.setFaceMaxAngle(parseHorizontal(face), angle);
    }

    // Clear the face angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearFaceAngle", signature = "clearFaceAngle(face?:string)",
            description = "Clears the face angle.")
    public final void clearFaceAngle(Optional<String> face) throws ScriptException {
        if (face.isPresent()) {
            blockEntity.clearManualFaceAngle(parseHorizontal(face.get()));
        } else {
            blockEntity.clearManualFaceAngle(null);
        }
    }

    // Clear the face max angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearFaceMaxAngle", signature = "clearFaceMaxAngle(face?:string)",
            description = "Clears the face max angle.")
    public final void clearFaceMaxAngle(Optional<String> face) throws ScriptException {
        if (face.isPresent()) {
            blockEntity.clearFaceMaxAngle(parseHorizontal(face.get()));
        } else {
            blockEntity.clearFaceMaxAngle(null);
        }
    }

    // Get the lane speed
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getLaneSpeed", signature = "getLaneSpeed(axis:string): number",
            description = "Returns the lane speed.")
    public final double getLaneSpeed(String axis) throws ScriptException {
        return parseAxis(axis) == Direction.Axis.X ? blockEntity.getEastWestSpeed() : blockEntity.getNorthSouthSpeed();
    }

    // Get the status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        return Map.of(
                "gyroMode", blockEntity.isGyroMode(),
            "servoMode", blockEntity.isServoModeActive(),
                "gyroSource", blockEntity.hasGyroSource(),
                "mode", getMode(),
                "reverseMode", blockEntity.isReverseMode(),
                "speed", Map.of(
                        "north_south", blockEntity.getNorthSouthSpeed(),
                        "east_west", blockEntity.getEastWestSpeed()),
                "laneModes", Map.of(
                        "north_south", blockEntity.getLaneMode(Direction.Axis.Z).name().toLowerCase(),
                        "east_west", blockEntity.getLaneMode(Direction.Axis.X).name().toLowerCase()),
                "signals", Map.of(
                        "north", blockEntity.getOutputSignal(Direction.NORTH),
                        "south", blockEntity.getOutputSignal(Direction.SOUTH),
                        "east", blockEntity.getOutputSignal(Direction.EAST),
                        "west", blockEntity.getOutputSignal(Direction.WEST)),
                "angles", Map.of(
                        "north", blockEntity.getFaceAngle(Direction.NORTH),
                        "south", blockEntity.getFaceAngle(Direction.SOUTH),
                        "east", blockEntity.getFaceAngle(Direction.EAST),
                    "west", blockEntity.getFaceAngle(Direction.WEST)),
                "maxAngles", Map.of(
                    "north", blockEntity.getFaceMaxAngle(Direction.NORTH),
                    "south", blockEntity.getFaceMaxAngle(Direction.SOUTH),
                    "east", blockEntity.getFaceMaxAngle(Direction.EAST),
                    "west", blockEntity.getFaceMaxAngle(Direction.WEST))
        );
    }

    // Parse the horizontal
    private static Direction parseHorizontal(String face) throws ScriptException {
        try {
            Direction dir = Direction.valueOf(face.trim().toUpperCase());
            if (dir.getAxis().isHorizontal()) {
                return dir;
            }
        } catch (Exception ignored) {
        }
        throw new ScriptException("face must be north, south, east, or west");
    }

    // Parse the axis
    private static Direction.Axis parseAxis(String axis) throws ScriptException {
        String normalized = axis.trim().toLowerCase();
        return switch (normalized) {
            case "x", "east_west", "east-west", "ew" -> Direction.Axis.X;
            case "z", "north_south", "north-south", "ns" -> Direction.Axis.Z;
            default -> throw new ScriptException("axis must be x/east_west or z/north_south");
        };
    }

    // Parse the enum
    private static <E extends Enum<E>> E parseEnum(String val, Class<E> type, String label) throws ScriptException {
        String normalized = val.trim().toUpperCase().replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException e) {
            throw new ScriptException("unknown " + label + " '" + val + "'");
        }
    }
}
