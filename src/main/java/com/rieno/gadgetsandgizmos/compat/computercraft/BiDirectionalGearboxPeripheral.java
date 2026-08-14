package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import static java.util.Map.entry;

// Expose Bidirectional Gearbox controls and telemetry to ComputerCraft
public class BiDirectionalGearboxPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final BiDirectionalGearboxBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearbox peripheral
    public BiDirectionalGearboxPeripheral(BiDirectionalGearboxBlockEntity blockEntity) {
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
        return "bidirectional_gearbox";
    }

    // Compare this bi directional gearbox peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof BiDirectionalGearboxPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Check if this is a gyro mode
    @LuaFunction
    public final boolean isGyroMode() {
        return blockEntity.isGyroMode();
    }

    // Check if this is a servo mode
    @LuaFunction
    public final boolean isServoMode() {
        return blockEntity.isServoModeActive();
    }

    // Check if this has gyro source
    @LuaFunction
    public final boolean hasGyroSource() {
        return blockEntity.hasGyroSource();
    }

    // Get the mode
    @LuaFunction
    public final String getMode() {
        return blockEntity.getOperationModeName();
    }

    // Set the mode
    @LuaFunction
    public final void setMode(String mode) throws LuaException {
        try {
            blockEntity.setOperationMode(mode);
        } catch (IllegalArgumentException e) {
            throw new LuaException("unknown mode '" + mode + "'");
        }
    }

    // Get the lane mode
    @LuaFunction
    public final String getLaneMode(String axis) throws LuaException {
        return blockEntity.getLaneMode(parseAxis(axis)).name().toLowerCase();
    }

    // Set the lane mode
    @LuaFunction
    public final void setLaneMode(String axis, String mode) throws LuaException {
        blockEntity.setLaneMode(parseAxis(axis), parseEnum(mode, BiDirectionalGearboxBlockEntity.LaneMode.class, "lane mode"));
    }

    // Check if this is a reverse mode
    @LuaFunction
    public final boolean isReverseMode() {
        return blockEntity.isReverseMode();
    }

    // Get the speed
    @LuaFunction
    public final double getSpeed() {
        return blockEntity.getSpeed();
    }

    // Get the signal
    @LuaFunction
    public final int getSignal(String face) throws LuaException {
        return blockEntity.getOutputSignal(parseHorizontal(face));
    }

    // Get the face angle
    @LuaFunction
    public final double getFaceAngle(String face) throws LuaException {
        return blockEntity.getFaceAngle(parseHorizontal(face));
    }

    // Get the face max angle
    @LuaFunction
    public final double getFaceMaxAngle(String face) throws LuaException {
        return blockEntity.getFaceMaxAngle(parseHorizontal(face));
    }

    // Set the face angle
    @LuaFunction
    public final void setFaceAngle(String face, double angle) throws LuaException {
        blockEntity.setManualFaceAngle(parseHorizontal(face), angle);
    }

    // Set the face max angle
    @LuaFunction
    public final void setFaceMaxAngle(String face, double angle) throws LuaException {
        blockEntity.setFaceMaxAngle(parseHorizontal(face), angle);
    }

    // Clear the face angle
    @LuaFunction
    public final void clearFaceAngle(Optional<String> face) throws LuaException {
        if (face.isPresent()) {
            blockEntity.clearManualFaceAngle(parseHorizontal(face.get()));
        } else {
            blockEntity.clearManualFaceAngle(null);
        }
    }

    // Clear the face max angle
    @LuaFunction
    public final void clearFaceMaxAngle(Optional<String> face) throws LuaException {
        if (face.isPresent()) {
            blockEntity.clearFaceMaxAngle(parseHorizontal(face.get()));
        } else {
            blockEntity.clearFaceMaxAngle(null);
        }
    }

    // Get the lane speed
    @LuaFunction
    public final double getLaneSpeed(String axis) throws LuaException {
        return parseAxis(axis) == Direction.Axis.X ? blockEntity.getEastWestSpeed() : blockEntity.getNorthSouthSpeed();
    }

    // Get the status
    @LuaFunction
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

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "isGyroMode(): boolean",
                "isServoMode(): boolean",
                "hasGyroSource(): boolean",
                "getMode(): string",
                "setMode(mode:string)",
                "getLaneMode(axis:string): string",
                "setLaneMode(axis:string, mode:string)",
                "isReverseMode(): boolean",
                "getSpeed(): number",
                "getLaneSpeed(axis:string): number",
                "getSignal(face:string): number",
                "getFaceAngle(face:string): number",
                "getFaceMaxAngle(face:string): number",
                "setFaceAngle(face:string, angle:number)",
                "setFaceMaxAngle(face:string, angle:number)",
                "clearFaceAngle(face?:string)",
                "clearFaceMaxAngle(face?:string)",
                "getStatus(): table",
                "help(method?: string): string|table"
        );
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) throws LuaException {
        Map<String, String> docs = Map.ofEntries(
            entry("isGyroMode", "isGyroMode() -> legacy alias for isServoMode(); true when servo mode is active"),
            entry("isServoMode", "isServoMode() -> true when servo mode is active"),
                entry("hasGyroSource", "hasGyroSource() -> true when an Advanced Data Link or gimbal sensor is controlling the block"),
            entry("getMode", "getMode() -> auto, passthrough, passthrough_split, servo, or servo_locked"),
            entry("setMode", "setMode(mode) -> set auto/passthrough/passthrough_split/servo/servo_locked; split, angle_control, face_output, and locked remain accepted as aliases"),
                entry("getLaneMode", "getLaneMode(axis) -> lane mode for x/east_west or z/north_south"),
                entry("setLaneMode", "setLaneMode(axis, mode) -> set straight, reversed, or disabled"),
                entry("isReverseMode", "isReverseMode() -> true when redstone inversion is active"),
                entry("getSpeed", "getSpeed() -> Create kinetic speed of the north/south lane"),
                entry("getLaneSpeed", "getLaneSpeed(axis) -> kinetic speed for x/east_west or z/north_south"),
                entry("getSignal", "getSignal(face) -> redstone output for north/south/east/west"),
            entry("getFaceAngle", "getFaceAngle(face) -> current servo angle for north/south/east/west"),
            entry("getFaceMaxAngle", "getFaceMaxAngle(face) -> legacy advisory face angle retained for compatibility"),
            entry("setFaceAngle", "setFaceAngle(face, angle) -> set manual servo target angle in degrees and enter servo mode"),
            entry("setFaceMaxAngle", "setFaceMaxAngle(face, angle) -> set the legacy advisory face angle without limiting output"),
                entry("clearFaceAngle", "clearFaceAngle(face?) -> clear one manual angle or all manual angles"),
            entry("clearFaceMaxAngle", "clearFaceMaxAngle(face?) -> clear one legacy advisory face angle or all advisory values"),
                entry("getStatus", "getStatus() -> table with speed, signals, and angles"),
                entry("methods", "methods() -> list of all callable peripheral methods"),
                entry("help", "help() -> all docs, help('name') -> one entry")
        );
        if (method.isEmpty()) {
            return docs;
        }
        String key = method.get();
        if (!docs.containsKey(key)) {
            throw new LuaException("unknown method '" + key + "'");
        }
        return docs.get(key);
    }

    // Parse the horizontal
    private static Direction parseHorizontal(String face) throws LuaException {
        try {
            Direction dir = Direction.valueOf(face.trim().toUpperCase());
            if (dir.getAxis().isHorizontal()) {
                return dir;
            }
        } catch (Exception ignored) {
        }
        throw new LuaException("face must be north, south, east, or west");
    }

    // Parse the axis
    private static Direction.Axis parseAxis(String axis) throws LuaException {
        String normalized = axis.trim().toLowerCase();
        return switch (normalized) {
            case "x", "east_west", "east-west", "ew" -> Direction.Axis.X;
            case "z", "north_south", "north-south", "ns" -> Direction.Axis.Z;
            default -> throw new LuaException("axis must be x/east_west or z/north_south");
        };
    }

    // Parse the enum
    private static <E extends Enum<E>> E parseEnum(String val, Class<E> type, String label) throws LuaException {
        String normalized = val.trim().toUpperCase().replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException e) {
            throw new LuaException("unknown " + label + " '" + val + "'");
        }
    }
}
