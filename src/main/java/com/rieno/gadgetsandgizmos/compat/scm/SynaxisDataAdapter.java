package com.rieno.gadgetsandgizmos.compat.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataPort;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

// Expose Synaxis dynamic motor control and telemetry through graph data nodes
public final class SynaxisDataAdapter implements BlockEntityDataAdapter<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DYNAMIC_MOTOR =
            "com.verr1.synaxis.content.blocks.motor.AbstractDynamicMotorBlockEntity";
    private static final String LINEAR_ACTUATOR =
            "com.verr1.synaxis.content.blocks.slider.HydraulicLinearActuatorBlockEntity";
    private static final List<BlockEntityDataPort> GEOMETRY_PORTS = List.of(
            BlockEntityDataPort.readWrite("self_offset_x", "number"),
            BlockEntityDataPort.readWrite("self_offset_y", "number"),
            BlockEntityDataPort.readWrite("self_offset_z", "number"),
            BlockEntityDataPort.readWrite("companion_offset_x", "number"),
            BlockEntityDataPort.readWrite("companion_offset_y", "number"),
            BlockEntityDataPort.readWrite("companion_offset_z", "number"),
            BlockEntityDataPort.readable("connected", "boolean")
    );
    private static final List<BlockEntityDataPort> ROTARY_PORTS = ports(GEOMETRY_PORTS, List.of(
            new BlockEntityDataPort("drive_backend", "string", BlockEntityDataPort.Access.READ_WRITE,
                    List.of("force", "joint")),
            BlockEntityDataPort.readWrite("angle_mode", "boolean"),
            BlockEntityDataPort.readWrite("motor_target", "number"),
            BlockEntityDataPort.readWrite("output_torque", "number"),
            BlockEntityDataPort.readWrite("should_counter", "boolean"),
            BlockEntityDataPort.readWrite("eliminate_gravity", "boolean"),
            BlockEntityDataPort.readWrite("joint_eliminate_gravity", "boolean"),
            BlockEntityDataPort.readWrite("joint_contacts_enabled", "boolean"),
            BlockEntityDataPort.readWrite("lock_requested", "boolean"),
            BlockEntityDataPort.readWrite("auto_lock_enabled", "boolean"),
            BlockEntityDataPort.readWrite("auto_lock_angle_enter_threshold", "number"),
            BlockEntityDataPort.readWrite("auto_lock_angle_exit_threshold", "number"),
            BlockEntityDataPort.readWrite("auto_lock_speed_target_threshold", "number"),
            BlockEntityDataPort.readWrite("gain_p", "number"),
            BlockEntityDataPort.readWrite("gain_i", "number"),
            BlockEntityDataPort.readWrite("gain_d", "number"),
            BlockEntityDataPort.readWrite("joint_gain_p", "number"),
            BlockEntityDataPort.readWrite("joint_gain_d", "number"),
            BlockEntityDataPort.readWrite("joint_limit_min", "number"),
            BlockEntityDataPort.readWrite("joint_limit_max", "number"),
            BlockEntityDataPort.readWrite("joint_limit_across_wrap", "boolean"),
            BlockEntityDataPort.readWrite("joint_limit_enabled", "boolean"),
            BlockEntityDataPort.readable("joint_limit_angle_offset", "number"),
            BlockEntityDataPort.readable("lock_active", "boolean"),
            BlockEntityDataPort.readable("auto_lock_active", "boolean"),
            BlockEntityDataPort.readable("current_angle", "number"),
            BlockEntityDataPort.readable("current_speed", "number"),
            BlockEntityDataPort.readable("applied_torque", "number")
    ));
    private static final List<BlockEntityDataPort> LINEAR_PORTS = ports(GEOMETRY_PORTS, List.of(
            BlockEntityDataPort.readWrite("position_mode", "boolean"),
            BlockEntityDataPort.readWrite("motor_target", "number"),
            BlockEntityDataPort.readWrite("lock_requested", "boolean"),
            BlockEntityDataPort.readWrite("auto_lock_enabled", "boolean"),
            BlockEntityDataPort.readWrite("auto_lock_position_enter_threshold", "number"),
            BlockEntityDataPort.readWrite("auto_lock_position_exit_threshold", "number"),
            BlockEntityDataPort.readWrite("auto_lock_speed_target_threshold", "number"),
            BlockEntityDataPort.readWrite("contacts_enabled", "boolean"),
            BlockEntityDataPort.readWrite("joint_gain_p", "number"),
            BlockEntityDataPort.readWrite("joint_gain_d", "number"),
            BlockEntityDataPort.readWrite("has_max_force", "boolean"),
            BlockEntityDataPort.readWrite("max_force", "number"),
            BlockEntityDataPort.readWrite("min_extension", "number"),
            BlockEntityDataPort.readWrite("max_extension", "number"),
            BlockEntityDataPort.readable("lock_active", "boolean"),
            BlockEntityDataPort.readable("auto_lock_active", "boolean"),
            BlockEntityDataPort.readable("current_distance", "number"),
            BlockEntityDataPort.readable("current_speed", "number"),
            BlockEntityDataPort.readable("commanded_position", "number")
    ));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the adapted block entity type
    @Override
    public Class<BlockEntity> targetType() {
        return BlockEntity.class;
    }

    // Check whether this is one of Synaxis's dynamic joint implementations
    @Override
    public boolean supports(BlockEntity target) {
        return isInstanceOf(target, DYNAMIC_MOTOR) || isInstanceOf(target, LINEAR_ACTUATOR);
    }

    // Get the selected Synaxis motor's graph data ports
    @Override
    public List<BlockEntityDataPort> ports(BlockEntity target) {
        return isInstanceOf(target, LINEAR_ACTUATOR) ? LINEAR_PORTS : ROTARY_PORTS;
    }

    // Read one live Synaxis motor value
    @Override
    public GraphValue read(BlockEntity target, String port) {
        if (target == null || port == null) return null;
        Object value = invoke(target, getter(port));
        if (value instanceof Boolean bool) return GraphValue.bool(bool);
        if (value instanceof Number number) return GraphValue.number(number.doubleValue());
        if ("drive_backend".equals(port)) {
            Object serialized = invoke(value, "getSerializedName");
            return GraphValue.string(serialized == null ? String.valueOf(value) : String.valueOf(serialized));
        }
        return null;
    }

    // Write one Synaxis motor configuration value through its public command method
    @Override
    public boolean write(BlockEntity target, String port, GraphValue value) {
        if (target == null || port == null || value == null) return false;
        if(target.getLevel() != null && target.getLevel().isClientSide) return false;
        if("joint_limit_enabled".equals(port)){
            return invokeCommand(target, value.asBoolean() ? "applyJointLimit" : "clearJointLimit");
        }
        if ("drive_backend".equals(port)) return setDriveBackend(target, value.asString());
        String setter = setter(port);
        if (setter == null) return false;
        if(!isBooleanPort(port) && !Double.isFinite(value.asNumber())) return false;
        return isBooleanPort(port)
                ? invoke(target, setter, boolean.class, value.asBoolean())
                : invoke(target, setter, double.class, value.asNumber());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Combine static port groups in their display order
    private static List<BlockEntityDataPort> ports(
            List<BlockEntityDataPort> first, List<BlockEntityDataPort> second
    ) {
        List<BlockEntityDataPort> ports = new ArrayList<>(first.size() + second.size());
        ports.addAll(first);
        ports.addAll(second);
        return List.copyOf(ports);
    }

    // Check the runtime class hierarchy without linking optional Synaxis classes
    private static boolean isInstanceOf(Object value, String className) {
        if (value == null) return false;
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (className.equals(type.getName())) return true;
        }
        return false;
    }

    // Resolve the public getter for one graph port
    private static String getter(String port) {
        return switch (port) {
            case "self_offset_x" -> "selfOffsetX";
            case "self_offset_y" -> "selfOffsetY";
            case "self_offset_z" -> "selfOffsetZ";
            case "companion_offset_x" -> "companionOffsetX";
            case "companion_offset_y" -> "companionOffsetY";
            case "companion_offset_z" -> "companionOffsetZ";
            case "drive_backend" -> "driveBackend";
            case "angle_mode" -> "angleMode";
            case "motor_target" -> "target";
            case "output_torque" -> "outputTorque";
            case "should_counter" -> "shouldCounter";
            case "eliminate_gravity" -> "eliminateGravity";
            case "joint_eliminate_gravity" -> "jointEliminateGravity";
            case "joint_contacts_enabled" -> "jointContactsEnabled";
            case "lock_requested" -> "lockRequested";
            case "auto_lock_enabled" -> "autoLockEnabled";
            case "auto_lock_angle_enter_threshold" -> "autoLockAngleEnterThreshold";
            case "auto_lock_angle_exit_threshold" -> "autoLockAngleExitThreshold";
            case "auto_lock_speed_target_threshold" -> "autoLockSpeedTargetThreshold";
            case "gain_p" -> "gainP";
            case "gain_i" -> "gainI";
            case "gain_d" -> "gainD";
            case "joint_gain_p" -> "jointGainP";
            case "joint_gain_d" -> "jointGainD";
            case "joint_limit_min" -> "jointLimitMin";
            case "joint_limit_max" -> "jointLimitMax";
            case "joint_limit_across_wrap" -> "jointLimitAcrossWrap";
            case "joint_limit_enabled" -> "jointLimitEnabled";
            case "joint_limit_angle_offset" -> "jointLimitAngleOffset";
            case "lock_active" -> "lockActive";
            case "auto_lock_active" -> "autoLockActive";
            case "current_angle" -> "currentAngle";
            case "current_speed" -> "currentSpeed";
            case "applied_torque" -> "appliedTorque";
            case "position_mode" -> "positionMode";
            case "auto_lock_position_enter_threshold" -> "autoLockPositionEnterThreshold";
            case "auto_lock_position_exit_threshold" -> "autoLockPositionExitThreshold";
            case "contacts_enabled" -> "contactsEnabled";
            case "has_max_force" -> "hasMaxForce";
            case "max_force" -> "maxForce";
            case "min_extension" -> "minExtension";
            case "max_extension" -> "maxExtension";
            case "current_distance" -> "currentDistance";
            case "commanded_position" -> "commandedPosition";
            default -> port;
        };
    }

    // Resolve the public setter for one writable graph port
    private static String setter(String port) {
        return switch (port) {
            case "self_offset_x" -> "setSelfOffsetX";
            case "self_offset_y" -> "setSelfOffsetY";
            case "self_offset_z" -> "setSelfOffsetZ";
            case "companion_offset_x" -> "setCompanionOffsetX";
            case "companion_offset_y" -> "setCompanionOffsetY";
            case "companion_offset_z" -> "setCompanionOffsetZ";
            case "angle_mode" -> "setAngleMode";
            case "target", "motor_target" -> "setTarget";
            case "output_torque" -> "setOutputTorque";
            case "should_counter" -> "setShouldCounter";
            case "eliminate_gravity" -> "setEliminateGravity";
            case "joint_eliminate_gravity" -> "setJointEliminateGravity";
            case "joint_contacts_enabled" -> "setJointContactsEnabled";
            case "lock_requested" -> "setLockRequested";
            case "auto_lock_enabled" -> "setAutoLockEnabled";
            case "auto_lock_angle_enter_threshold" -> "setAutoLockAngleEnterThreshold";
            case "auto_lock_angle_exit_threshold" -> "setAutoLockAngleExitThreshold";
            case "auto_lock_speed_target_threshold" -> "setAutoLockSpeedTargetThreshold";
            case "gain_p" -> "setGainP";
            case "gain_i" -> "setGainI";
            case "gain_d" -> "setGainD";
            case "joint_gain_p" -> "setJointGainP";
            case "joint_gain_d" -> "setJointGainD";
            case "joint_limit_min" -> "setJointLimitMin";
            case "joint_limit_max" -> "setJointLimitMax";
            case "joint_limit_across_wrap" -> "setJointLimitAcrossWrap";
            case "position_mode" -> "setPositionMode";
            case "auto_lock_position_enter_threshold" -> "setAutoLockPositionEnterThreshold";
            case "auto_lock_position_exit_threshold" -> "setAutoLockPositionExitThreshold";
            case "contacts_enabled" -> "setContactsEnabled";
            case "has_max_force" -> "setHasMaxForce";
            case "max_force" -> "setMaxForce";
            case "min_extension" -> "setMinExtension";
            case "max_extension" -> "setMaxExtension";
            default -> null;
        };
    }

    // Check whether a graph port maps to a boolean setter
    private static boolean isBooleanPort(String port) {
        return switch (port) {
            case "angle_mode", "should_counter", "eliminate_gravity", "joint_eliminate_gravity",
                 "joint_contacts_enabled", "lock_requested", "auto_lock_enabled",
                 "joint_limit_across_wrap", "position_mode", "contacts_enabled", "has_max_force" -> true;
            default -> false;
        };
    }

    // Invoke one optional getter
    private static Object invoke(Object target, String name) {
        if (target == null || name == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean invokeCommand(Object target, String name){
        try{
            target.getClass().getMethod(name).invoke(target);
            return true;
        }catch(ReflectiveOperationException | RuntimeException ignored){
            return false;
        }
    }

    // Invoke one optional primitive setter
    private static boolean invoke(Object target, String name, Class<?> parameter, Object value) {
        if (target == null || name == null) return false;
        try {
            target.getClass().getMethod(name, parameter).invoke(target, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Select Synaxis's force or joint backend by its serialized graph value
    private static boolean setDriveBackend(BlockEntity target, String requested) {
        if (target == null || requested == null) return false;
        String value = requested.strip();
        for (Method method : target.getClass().getMethods()) {
            if (!"setDriveBackend".equals(method.getName()) || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (!type.isEnum()) continue;
            Object selected = enumValue(type, value);
            if (selected == null) return false;
            try {
                method.invoke(target, selected);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    // Resolve one enum value by Synaxis's serialized name or enum constant name
    private static Object enumValue(Class<?> type, String requested) {
        for (Object value : type.getEnumConstants()) {
            Object serialized = invoke(value, "getSerializedName");
            if (requested.equalsIgnoreCase(String.valueOf(serialized))
                    || requested.equalsIgnoreCase(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }
}
