package com.rieno.gadgetsandgizmos.compat.controller;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Adapt supported external block entities to direct control through guarded reflection
public final class ExternalBlockEntityDirectControlCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String SIMULATED_DIRECTIONAL_GEARSHIFT = "simulated:directional_gearshift";
    private static final String SIMULATED_THROTTLE_LEVER = "simulated:throttle_lever";
    private static final String SIMULATED_LASER_POINTER = "simulated:laser_pointer";
    private static final String SIMULATED_LASER_SENSOR = "simulated:laser_sensor";
    private static final String SIMULATED_LASER_SENSOR_BE = "simulated:ir_sensor";
    private static final String SIMULATED_ANALOGUE_TRANSMISSION = "simulated:analogue_transmission";
    private static final String SIMULATED_ANALOGUE_TRANSMISSION_BE = "simulated:simple";
    private static final String SIMULATED_REDSTONE_ACCUMULATOR = "simulated:redstone_accumulator";
    private static final String SIMULATED_REDSTONE_INDUCTOR = "simulated:redstone_inductor";
    private static final String SIMULATED_REDSTONE_MAGNET = "simulated:redstone_magnet";
    private static final String SIMULATED_OPTICAL_SENSOR = "simulated:optical_sensor";
    private static final String SIMULATED_DOCKING_CONNECTOR = "simulated:docking_connector";
    private static final String SIMULATED_ALTITUDE_SENSOR = "simulated:altitude_sensor";
    private static final String SIMULATED_NAVIGATION_TABLE = "simulated:navigation_table";
    private static final String SIMULATED_GIMBAL_SENSOR = "simulated:gimbal_sensor";
    private static final String CT_ADVANCED_NAVIGATION_TABLE = "createthrusters:advanced_navigation_table";
    private static final String AERONAUTICS_HOT_AIR_BURNER_ALT = "aeronautics:hot_air_burner";
    private static final String AERONAUTICS_HOT_AIR_BURNER = "aeronautics:adjustable_burner";
    private static final String AERONAUTICS_STEAM_VENT = "aeronautics:steam_vent";
    private static final String AERONAUTICS_MOUNTED_POTATO_CANNON = "aeronautics:mounted_potato_cannon";
    private static final String AERONAUTICS_PROPELLER_BEARING = "aeronautics:propeller_bearing";
    private static final String AERONAUTICS_GYRO_PROPELLER_BEARING = "aeronautics:gyroscopic_propeller_bearing";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the external block entity direct control compat
    private ExternalBlockEntityDirectControlCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the direct signal
    public static boolean applyDirectSignal(@Nullable BlockEntity blockEntity, float val) {
        return applyDirectSignal(blockEntity, "", val);
    }

    // Apply the direct signal
    public static boolean applyDirectSignal(@Nullable BlockEntity blockEntity, String channelId, float val) {
        if (blockEntity instanceof IDirectControlReceiver receiver) {
            receiver.applyDirectControllerSignal(channelId == null ? "" : channelId, val);
            return true;
        }
        String blockId = blockId(blockEntity);
        if (blockId == null) {
            return false;
        }
        if (blockId.startsWith("create_connected:") && applyCreateConnectedSignal(blockEntity, val)) {
            return true;
        }
        if (blockId.startsWith("dndesires:") && applyDndSignal(blockEntity, val)) {
            return true;
        }

        return switch (blockId) {
            case SIMULATED_REDSTONE_ACCUMULATOR -> invokeOneArg(blockEntity, "setOutputSignal", int.class, scaledSignal(val))
                || invokeOneArg(blockEntity, "setOutputSignal", Integer.class, scaledSignal(val))
                || writeIntField(blockEntity, "outputSignal", scaledSignal(val));
            case SIMULATED_REDSTONE_INDUCTOR -> writeIntField(blockEntity, "outputSignal", scaledSignal(val));
            case AERONAUTICS_HOT_AIR_BURNER, AERONAUTICS_HOT_AIR_BURNER_ALT, AERONAUTICS_STEAM_VENT -> invokeOneArg(blockEntity, "setSignalStrength", int.class, scaledSignal(val))
                    || invokeOneArg(blockEntity, "setSignalStrength", Integer.class, scaledSignal(val));
            case AERONAUTICS_PROPELLER_BEARING, AERONAUTICS_GYRO_PROPELLER_BEARING -> {
                float directedValue = channelId == null || channelId.isBlank() ? val
                        : isCounterClockwiseChannel(channelId) ? -Math.abs(val) : Math.abs(val);
                yield invokeOneArg(blockEntity, "setRotationSpeed", float.class, directedValue)
                        || invokeOneArg(blockEntity, "setRotationSpeed", Float.class, directedValue);
            }
            default -> false;
        };
    }

    // Check if this is a counter-clockwise channel
    private static boolean isCounterClockwiseChannel(String channelId) {
        String normalized = channelId == null ? "" : channelId.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("ccw") || normalized.contains("counter") || normalized.contains("left");
    }

    // Sample the direct signal
    public static @Nullable Double sampleDirectSignal(@Nullable BlockEntity blockEntity) {
        String blockId = blockId(blockEntity);
        if (blockId == null) {
            return null;
        }
        if (blockId.startsWith("create_connected:")) {
            Double connectedSample = sampleCreateConnectedSignal(blockEntity);
            if (connectedSample != null) {
                return connectedSample;
            }
        }
        if (blockId.startsWith("dndesires:")) {
            Double dndSample = sampleDndSignal(blockEntity);
            if (dndSample != null) {
                return dndSample;
            }
        }

        return switch (blockId) {
            case SIMULATED_DIRECTIONAL_GEARSHIFT -> directionalPoweredSample(blockEntity);
            case SIMULATED_THROTTLE_LEVER -> normalizedSignal(invokeNoArgs(blockEntity, "getState"));
            case SIMULATED_LASER_POINTER -> normalizedSignal(invokeNoArgs(blockEntity, "getPower"));
            case SIMULATED_LASER_SENSOR, SIMULATED_LASER_SENSOR_BE -> normalizedSignal(invokeNoArgs(blockEntity, "getSignal"), readField(blockEntity, "currentPower"));
            case SIMULATED_ANALOGUE_TRANSMISSION, SIMULATED_ANALOGUE_TRANSMISSION_BE -> normalizedSignal(invokeNoArgs(blockEntity, "getRedstoneSignal"), readField(blockEntity, "redstoneSignal"));
            case SIMULATED_REDSTONE_ACCUMULATOR, SIMULATED_REDSTONE_INDUCTOR -> normalizedSignal(invokeNoArgs(blockEntity, "getSignal"), readField(blockEntity, "outputSignal"));
            case SIMULATED_REDSTONE_MAGNET -> normalizedBoolean(readField(blockEntity, "powered"));
            case SIMULATED_OPTICAL_SENSOR -> normalizedBoolean(invokeNoArgs(blockEntity, "hasHit"));
            case SIMULATED_DOCKING_CONNECTOR -> normalizedBoolean(invokeNoArgs(blockEntity, "isExtended"), readField(blockEntity, "powered"));
            case SIMULATED_ALTITUDE_SENSOR -> normalizedSignal(invokeNoArgs(blockEntity, "getSignal"));
            case SIMULATED_NAVIGATION_TABLE, CT_ADVANCED_NAVIGATION_TABLE -> normalizedAngle(invokeNoArgs(blockEntity, "getRelativeAngle"));
            case SIMULATED_GIMBAL_SENSOR -> sampleGimbalSensor(blockEntity);
            case AERONAUTICS_HOT_AIR_BURNER, AERONAUTICS_HOT_AIR_BURNER_ALT, AERONAUTICS_STEAM_VENT -> normalizedSignal(invokeNoArgs(blockEntity, "getSignalStrength"), readField(blockEntity, "signalStrength"));
            case AERONAUTICS_MOUNTED_POTATO_CANNON -> normalizedEnumState(readField(blockEntity, "currentState"), "CHARGED");
            case AERONAUTICS_PROPELLER_BEARING, AERONAUTICS_GYRO_PROPELLER_BEARING -> normalizedSignal(invokeNoArgs(blockEntity, "getRotationSpeed"));
            default -> null;
        };
    }

    // Get the readable data
    public static Map<String, String> readableData(@Nullable BlockEntity blockEntity) {
        return dndDataPorts(blockEntity);
    }

    // Get the writable data
    public static Map<String, String> writableData(@Nullable BlockEntity blockEntity) {
        return dndDataPorts(blockEntity);
    }

    // Read the data
    public static @Nullable AdvancedGraphDocument.Value readData(@Nullable BlockEntity blockEntity, String port) {
        ScrollValueBehaviour behaviour = dndBehaviours(blockEntity).get(port);
        if (behaviour == null) {
            return null;
        }
        if (behaviour instanceof ScrollOptionBehaviour<?> option) {
            Enum<?> selected = option.get();
            return AdvancedGraphDocument.Value.string(selected == null
                    ? ""
                    : selected.name().toLowerCase(Locale.ROOT));
        }
        return AdvancedGraphDocument.Value.number(behaviour.getValue());
    }

    // Write the data
    public static boolean writeData(@Nullable BlockEntity blockEntity, String port,
                                    AdvancedGraphDocument.Value val) {
        ScrollValueBehaviour behaviour = dndBehaviours(blockEntity).get(port);
        if (behaviour == null || val == null) {
            return false;
        }
        int next;
        if (behaviour instanceof ScrollOptionBehaviour<?> option) {
            Object current = option.get();
            if (!(current instanceof Enum<?> selected)) {
                return false;
            }
            Enum<?>[] constants = selected.getDeclaringClass().getEnumConstants();
            next = optionIndex(constants, val);
            if (next < 0) {
                return false;
            }
        } else {
            next = (int) Math.round(val.asNumber());
        }
        behaviour.setValue(next);
        markDirty(blockEntity);
        return true;
    }

    // Get the block id
    private static @Nullable String blockId(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getBlockState() == null) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        return id == null ? null : id.toString();
    }

    // Get the scaled signal
    private static int scaledSignal(float val) {
        return Math.max(0, Math.min(15, Math.round(Math.abs(val) * 15.0f)));
    }

    // Get the normalized signal
    private static @Nullable Double normalizedSignal(@Nullable Object val) {
        return normalizedSignal(val, null);
    }

    // Get the normalized signal
    private static @Nullable Double normalizedSignal(@Nullable Object primary, @Nullable Object secondary) {
        if (primary instanceof Number num) {
            return Math.max(0.0D, Math.min(1.0D, Math.abs(num.doubleValue()) / 15.0D));
        }
        if (secondary instanceof Number num) {
            return Math.max(0.0D, Math.min(1.0D, Math.abs(num.doubleValue()) / 15.0D));
        }
        return null;
    }

    // Get the normalized boolean
    private static @Nullable Double normalizedBoolean(@Nullable Object val) {
        return normalizedBoolean(val, null);
    }

    // Get the normalized boolean
    private static @Nullable Double normalizedBoolean(@Nullable Object primary, @Nullable Object secondary) {
        if (primary instanceof Boolean bool) {
            return bool ? 1.0D : 0.0D;
        }
        if (secondary instanceof Boolean bool) {
            return bool ? 1.0D : 0.0D;
        }
        return null;
    }

    // Get the normalized enum state
    private static @Nullable Double normalizedEnumState(@Nullable Object enumValue, String activeName) {
        if (!(enumValue instanceof Enum<?> enumConstant)) {
            return null;
        }
        return enumConstant.name().equalsIgnoreCase(activeName) ? 1.0D : 0.0D;
    }

    // Get the directional powered sample
    private static @Nullable Double directionalPoweredSample(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getBlockState() == null) {
            return null;
        }
        String stateText = blockEntity.getBlockState().toString().toLowerCase();
        if (stateText.contains("left_powered=true") || stateText.contains("right_powered=true")) {
            return 1.0D;
        }
        if (stateText.contains("left_powered=false") || stateText.contains("right_powered=false")) {
            return 0.0D;
        }
        return null;
    }

    // Get the normalized angle
    private static @Nullable Double normalizedAngle(@Nullable Object val) {
        if (!(val instanceof Number num)) {
            return null;
        }
        return Math.max(0.0D, Math.min(1.0D, Math.abs(num.doubleValue()) / 360.0D));
    }

    // Sample the gimbal sensor
    private static @Nullable Double sampleGimbalSensor(BlockEntity blockEntity) {
        double[] angles = SimulatedHelper.getAngles(blockEntity);
        if (angles == null || angles.length < 2) {
            return null;
        }
        double magnitude = Math.max(Math.abs(angles[0]), Math.abs(angles[1]));
        return Math.max(0.0D, Math.min(1.0D, magnitude / (Math.PI / 2.0D)));
    }

    // Invoke a method without arguments
    private static @Nullable Object invokeNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Read the field
    private static @Nullable Object readField(Object target, String fieldName) {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Run the one arg
    private static boolean invokeOneArg(Object target, String methodName, Class<?> parameterType, Object arg) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            method.invoke(target, arg);
            markDirty(target);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Write the int field
    private static boolean writeIntField(Object target, String fieldName, int val) {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return false;
        }
        try {
            field.setAccessible(true);
            field.setInt(target, val);
            markDirty(target);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Apply the create connected signal
    private static boolean applyCreateConnectedSignal(BlockEntity blockEntity, float val) {
        int signal = scaledSignal(val);
        if (invokeOneArg(blockEntity, "setSignal", int.class, signal)
                || invokeOneArg(blockEntity, "setState", int.class, signal)
                || invokeOneArg(blockEntity, "transmit", int.class, signal)) {
            return true;
        }

        Object scrollValue = findCreateConnectedScrollValue(blockEntity);
        if (scrollValue == null) {
            return false;
        }
        Integer min = readIntField(scrollValue, "min");
        Integer max = readIntField(scrollValue, "max");
        if (min == null || max == null) {
            return false;
        }
        int target = Math.round(min + Math.max(0.0f, Math.min(1.0f, val)) * (max - min));
        return invokeOneArg(scrollValue, "setValue", int.class, target);
    }

    // Apply the dnd signal
    private static boolean applyDndSignal(BlockEntity blockEntity, float val) {
        Map<String, ScrollValueBehaviour> behaviours = dndBehaviours(blockEntity);
        ScrollValueBehaviour behaviour = behaviours.get("target_speed");
        if (behaviour == null) {
            behaviour = behaviours.get("generated_speed");
        }
        if (behaviour == null) {
            behaviour = behaviours.values().stream()
                    .filter(candidate -> !(candidate instanceof ScrollOptionBehaviour<?>))
                    .findFirst()
                    .orElse(null);
        }
        if (behaviour == null) {
            return false;
        }
        Integer min = readIntField(behaviour, "min");
        Integer max = readIntField(behaviour, "max");
        if (min == null || max == null) {
            return false;
        }
        behaviour.setValue(Math.round(min + Math.max(0.0f, Math.min(1.0f, val)) * (max - min)));
        markDirty(blockEntity);
        return true;
    }

    // Sample the dnd signal
    private static @Nullable Double sampleDndSignal(BlockEntity blockEntity) {
        Map<String, ScrollValueBehaviour> behaviours = dndBehaviours(blockEntity);
        ScrollValueBehaviour behaviour = behaviours.get("target_speed");
        if (behaviour == null) {
            behaviour = behaviours.get("generated_speed");
        }
        if (behaviour == null) {
            behaviour = behaviours.values().stream()
                    .filter(candidate -> !(candidate instanceof ScrollOptionBehaviour<?>))
                    .findFirst()
                    .orElse(null);
        }
        if (behaviour == null) {
            return null;
        }
        Integer min = readIntField(behaviour, "min");
        Integer max = readIntField(behaviour, "max");
        if (min == null || max == null) {
            return null;
        }
        return max.equals(min) ? 0.0D : Math.max(0.0D,
                Math.min(1.0D, (behaviour.getValue() - min) / (double) (max - min)));
    }

    // Get the dnd data ports
    private static Map<String, String> dndDataPorts(@Nullable BlockEntity blockEntity) {
        Map<String, String> ports = new LinkedHashMap<>();
        dndBehaviours(blockEntity).forEach((name, behaviour) -> ports.put(name,
                behaviour instanceof ScrollOptionBehaviour<?> ? "string" : "number"));
        return ports;
    }

    // Get the dnd behaviours
    private static Map<String, ScrollValueBehaviour> dndBehaviours(@Nullable BlockEntity blockEntity) {
        Map<String, ScrollValueBehaviour> behaviours = new LinkedHashMap<>();
        String id = blockId(blockEntity);
        if (blockEntity == null || id == null || !id.startsWith("dndesires:")) {
            return behaviours;
        }
        for (Class<?> current = blockEntity.getClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!ScrollValueBehaviour.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object candidate = field.get(blockEntity);
                    if (candidate instanceof ScrollValueBehaviour behaviour) {
                        behaviours.putIfAbsent(toSnakeCase(field.getName()), behaviour);
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return behaviours;
    }

    // Get the option index
    private static int optionIndex(Enum<?>[] constants, AdvancedGraphDocument.Value val) {
        if (constants == null || constants.length == 0) {
            return -1;
        }
        if ("number".equals(val.type())) {
            return Math.max(0, Math.min(constants.length - 1, (int) Math.round(val.asNumber())));
        }
        String requested = val.asString().trim();
        for (int idx = 0; idx < constants.length; idx++) {
            if (constants[idx].name().equalsIgnoreCase(requested)
                    || constants[idx].name().replace('_', ' ').equalsIgnoreCase(requested)) {
                return idx;
            }
        }
        return -1;
    }

    // Convert the external block entity direct control compat to snake case
    private static String toSnakeCase(String val) {
        return val.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    // Sample the create connected signal
    private static @Nullable Double sampleCreateConnectedSignal(BlockEntity blockEntity) {
        Object signal = invokeNoArgs(blockEntity, "getSignal");
        if (!(signal instanceof Number)) {
            signal = invokeNoArgs(blockEntity, "getState");
        }
        if (signal instanceof Number num) {
            return Math.max(0.0D, Math.min(1.0D, num.doubleValue() / 15.0D));
        }

        Object scrollValue = findCreateConnectedScrollValue(blockEntity);
        if (scrollValue == null) {
            return null;
        }
        Object current = invokeNoArgs(scrollValue, "getValue");
        Integer min = readIntField(scrollValue, "min");
        Integer max = readIntField(scrollValue, "max");
        if (!(current instanceof Number num) || min == null || max == null || max.equals(min)) {
            return null;
        }
        return Math.max(0.0D, Math.min(1.0D, (num.doubleValue() - min) / (max - min)));
    }

    // Find the create connected scroll value
    private static @Nullable Object findCreateConnectedScrollValue(Object target) {
        for (Class<?> current = target.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            if (!current.getName().startsWith("com.hlysine.create_connected.")) {
                continue;
            }
            for (Field field : current.getDeclaredFields()) {
                if (!field.getType().getName().endsWith("ScrollValueBehaviour")
                        && !isSubclassNamed(field.getType(), "com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object val = field.get(target);
                    if (val != null) {
                        return val;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    // Check if the subclass has the requested name
    private static boolean isSubclassNamed(Class<?> type, String className) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (className.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    // Read the int field
    private static @Nullable Integer readIntField(Object target, String fieldName) {
        Object val = readField(target, fieldName);
        return val instanceof Number num ? num.intValue() : null;
    }

    // Find the field
    private static @Nullable Field findField(Class<?> type, String fieldName) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    // Mark the dirty
    private static void markDirty(Object target) {
        if (!(target instanceof BlockEntity blockEntity)) {
            return;
        }
        blockEntity.setChanged();
        if (blockEntity.getLevel() != null) {
            blockEntity.getLevel().sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }
}
