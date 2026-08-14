package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Expose Create Rotation Speed Controller speed and direction through graph data ports
public final class CreateRotationSpeedControllerGraphCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String BLOCK_ID = "create:rotation_speed_controller";
    public static final String SPEED_PORT = "speed";
    public static final String DIRECTION_PORT = "direction";
    public static final String CLOCKWISE = "clockwise";
    public static final String COUNTER_CLOCKWISE = "counter_clockwise";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create rotation speed controller graph compat
    private CreateRotationSpeedControllerGraphCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a target
    public static boolean isTarget(Object target) {
        return target instanceof SpeedControllerBlockEntity;
    }

    // Check if this is a target
    public static boolean isTarget(AdvancedGraphDocument.Node node) {
        return node != null && BLOCK_ID.equalsIgnoreCase(
                node.data().getCompound("TargetData").getString("BlockId"));
    }

    // Get the readable data
    public static Map<String, String> readableData(Object target) {
        return isTarget(target)
                ? Map.of(SPEED_PORT, "number", DIRECTION_PORT, "string")
                : Map.of();
    }

    // Read the create rotation speed controller graph compat
    public static @Nullable AdvancedGraphDocument.Value read(Object target, String port) {
        if (!(target instanceof SpeedControllerBlockEntity controller)) {
            return null;
        }
        return readValue(controller.targetSpeed == null ? 0 : controller.targetSpeed.getValue(), port);
    }

    // Get the writable data
    public static Map<String, String> writableData(Object target) {
        return target instanceof SpeedControllerBlockEntity
                ? Map.of(SPEED_PORT, "number", DIRECTION_PORT, "string")
                : Map.of();
    }

    // Get the writable options
    public static CompoundTag writableOptions(Object target) {
        CompoundTag opts = new CompoundTag();
        if (!(target instanceof SpeedControllerBlockEntity)) {
            return opts;
        }
        ListTag directions = new ListTag();
        directions.add(StringTag.valueOf(CLOCKWISE));
        directions.add(StringTag.valueOf(COUNTER_CLOCKWISE));
        opts.put(DIRECTION_PORT, directions);
        return opts;
    }

    // Check if this compatibility handler owns the target port
    public static boolean handles(Object target, String port) {
        return isTarget(target)
                && (SPEED_PORT.equals(port) || DIRECTION_PORT.equals(port));
    }

    // Get the ports requiring write
    public static Set<String> portsRequiringWrite(AdvancedGraphDocument.Node node, Set<String> activePorts) {
        if (!isTarget(node) || activePorts == null || activePorts.isEmpty()) {
            return Set.of();
        }
        Set<String> required = new LinkedHashSet<>();
        if (activePorts.contains(SPEED_PORT)) required.add(SPEED_PORT);
        if (activePorts.contains(DIRECTION_PORT)) required.add(DIRECTION_PORT);
        return required;
    }

    // Check if this has active write port
    public static boolean hasActiveWritePort(Object target, Set<String> activePorts) {
        return isTarget(target) && activePorts != null
                && (activePorts.contains(SPEED_PORT) || activePorts.contains(DIRECTION_PORT));
    }

    // Write the create rotation speed controller graph compat
    public static boolean write(Object target, Set<String> activePorts,
                                Function<String, AdvancedGraphDocument.Value> values) {
        if (!(target instanceof SpeedControllerBlockEntity controller) || controller.targetSpeed == null) {
            return false;
        }
        boolean speedActive = activePorts.contains(SPEED_PORT);
        boolean directionActive = activePorts.contains(DIRECTION_PORT);
        if (!speedActive && !directionActive) {
            return false;
        }
        Double speed = speedActive ? values.apply(SPEED_PORT).asNumber() : null;
        String dir = directionActive ? values.apply(DIRECTION_PORT).asString() : null;
        controller.targetSpeed.setValue(signedTargetSpeed(controller.targetSpeed.getValue(), speed, dir));
        return true;
    }

    // Read the value
    static @Nullable AdvancedGraphDocument.Value readValue(int targetSpeed, String port) {
        return switch (port) {
            case SPEED_PORT -> AdvancedGraphDocument.Value.number(Math.abs(targetSpeed));
            case DIRECTION_PORT -> AdvancedGraphDocument.Value.string(
                    targetSpeed < 0 ? CLOCKWISE : COUNTER_CLOCKWISE);
            default -> null;
        };
    }

    // Get the signed target speed
    static int signedTargetSpeed(int current, @Nullable Double requestedSpeed, @Nullable String requestedDirection) {
        double finiteSpeed = requestedSpeed == null || !Double.isFinite(requestedSpeed)
                ? Math.abs(current)
                : requestedSpeed;
        int magnitude = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.abs(finiteSpeed)));
        int sign = current < 0 ? -1 : 1;
        if (requestedSpeed != null && requestedSpeed < 0) {
            sign = -1;
        }
        String dir = requestedDirection == null ? "" : requestedDirection.trim().toLowerCase(java.util.Locale.ROOT);
        if (!dir.isBlank()) {
            sign = isClockwise(dir) ? -1 : 1;
        }
        return magnitude == 0 ? 0 : sign * magnitude;
    }

    // Check if this is clockwise
    private static boolean isClockwise(String dir) {
        return CLOCKWISE.equals(dir)
                || "cw".equals(dir)
                || "reverse".equals(dir)
                || "negative".equals(dir)
                || "-".equals(dir);
    }
}
