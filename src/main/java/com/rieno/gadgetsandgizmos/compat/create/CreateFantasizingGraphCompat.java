package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Add graph speed control for Create: Fantasizing yin-yang engines through guarded reflection
public final class CreateFantasizingGraphCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String YIN_YANG_ENTITY =
            "dev.hail.create_fantasizing.block.compat_engine.YinYangEngineEntity";
    public static final String SPEED_PORT = "speed";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create fantasizing graph compat
    private CreateFantasizingGraphCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a target
    public static boolean isTarget(Object target) {
        return target != null && YIN_YANG_ENTITY.equals(target.getClass().getName());
    }

    // Check if this is a target
    public static boolean isTarget(AdvancedGraphDocument.Node node) {
        return node != null && "create_fantasizing:yin_yang_engine".equalsIgnoreCase(
                node.data().getCompound("TargetData").getString("BlockId"));
    }

    // Get the readable data
    public static Map<String, String> readableData(Object target) {
        return isTarget(target) ? Map.of(SPEED_PORT, "number") : Map.of();
    }

    // Get the writable data
    public static Map<String, String> writableData(Object target) {
        return isTarget(target) ? Map.of(SPEED_PORT, "number") : Map.of();
    }

    // Check if this compatibility handler owns the target port
    public static boolean handles(Object target, String port) {
        return isTarget(target) && SPEED_PORT.equals(port);
    }

    // Get the ports requiring write
    public static Set<String> portsRequiringWrite(AdvancedGraphDocument.Node node,
                                                   Set<String> activePorts) {
        return isTarget(node) && activePorts != null && activePorts.contains(SPEED_PORT)
                ? Set.of(SPEED_PORT) : Set.of();
    }

    // Read the create fantasizing graph compat
    public static @org.jetbrains.annotations.Nullable AdvancedGraphDocument.Value read(
            Object target, String port) {
        if (!handles(target, port)) {
            return null;
        }
        Number speed = invokeNumberField(target, "generatedSpeed", "getValue");
        return AdvancedGraphDocument.Value.number(speed == null ? 0.0D : speed.doubleValue());
    }

    // Write the create fantasizing graph compat
    public static boolean write(Object target, Set<String> activePorts,
                                Function<String, AdvancedGraphDocument.Value> values) {
        if (!isTarget(target) || activePorts == null || values == null
                || !activePorts.contains(SPEED_PORT)) {
            return false;
        }
        double speed = values.apply(SPEED_PORT).asNumber();
        if (!Double.isFinite(speed)) {
            return false;
        }
        try {
            Field generatedSpeed = target.getClass().getField("generatedSpeed");
            Object behaviour = generatedSpeed.get(target);
            if (behaviour == null) {
                return false;
            }
            Method setValue = behaviour.getClass().getMethod("setValue", int.class);
            setValue.invoke(behaviour, (int) Math.round(speed));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Read the numeric field
    private static Number invokeNumberField(Object target, String fieldName, String getterName) {
        try {
            Field field = target.getClass().getField(fieldName);
            Object val = field.get(target);
            if (val == null) {
                return null;
            }
            Object res = val.getClass().getMethod(getterName).invoke(val);
            return res instanceof Number num ? num : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
