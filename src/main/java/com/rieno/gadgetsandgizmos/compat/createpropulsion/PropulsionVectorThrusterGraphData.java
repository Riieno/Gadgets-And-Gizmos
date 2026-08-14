package com.rieno.gadgetsandgizmos.compat.createpropulsion;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;

import java.util.Map;

// Store and serialize Propulsion Vector Thruster Graph data
public final class PropulsionVectorThrusterGraphData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String VECTOR_ANGLE_X = "vector_angle_x";
    public static final String VECTOR_ANGLE_Y = "vector_angle_y";
    public static final String CURRENT_VECTOR_ANGLE_X = "current_vector_angle_x";
    public static final String CURRENT_VECTOR_ANGLE_Y = "current_vector_angle_y";
    public static final String VECTOR_ANGLE_OVERRIDE = "vector_angle_override";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the propulsion vector thruster graph data
    private PropulsionVectorThrusterGraphData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the readable ports
    public static void addReadablePorts(Map<String, String> ports, Object target) {
        if (!(target instanceof PropulsionVectorThrusterAngleAccess)) {
            return;
        }
        ports.put(VECTOR_ANGLE_X, "number");
        ports.put(VECTOR_ANGLE_Y, "number");
        ports.put(CURRENT_VECTOR_ANGLE_X, "number");
        ports.put(CURRENT_VECTOR_ANGLE_Y, "number");
        ports.put(VECTOR_ANGLE_OVERRIDE, "boolean");
    }

    // Add the writable ports
    public static void addWritablePorts(Map<String, String> ports, Object target) {
        if (!(target instanceof PropulsionVectorThrusterAngleAccess)) {
            return;
        }
        ports.put(VECTOR_ANGLE_X, "number");
        ports.put(VECTOR_ANGLE_Y, "number");
    }

    // Read the propulsion vector thruster graph data
    public static AdvancedGraphDocument.Value read(Object target, String field) {
        if (!(target instanceof PropulsionVectorThrusterAngleAccess access)) {
            return null;
        }
        Map<String, Object> angles = access.createThrusters$getVectorAngles();
        return switch (field) {
            case VECTOR_ANGLE_X -> AdvancedGraphDocument.Value.number(number(angles, "targetX"));
            case VECTOR_ANGLE_Y -> AdvancedGraphDocument.Value.number(number(angles, "targetY"));
            case CURRENT_VECTOR_ANGLE_X -> AdvancedGraphDocument.Value.number(number(angles, "currentX"));
            case CURRENT_VECTOR_ANGLE_Y -> AdvancedGraphDocument.Value.number(number(angles, "currentY"));
            case VECTOR_ANGLE_OVERRIDE -> AdvancedGraphDocument.Value.bool(booleanValue(angles, "override"));
            default -> null;
        };
    }

    // Write the propulsion vector thruster graph data
    public static boolean write(Object target, String field, AdvancedGraphDocument.Value val) {
        if (!(target instanceof PropulsionVectorThrusterAngleAccess access) || val == null
                || !VECTOR_ANGLE_X.equals(field) && !VECTOR_ANGLE_Y.equals(field)) {
            return false;
        }
        double requested = val.asNumber();
        if (!Double.isFinite(requested)) {
            return false;
        }
        Map<String, Object> angles = access.createThrusters$getVectorAngles();
        double xDegrees = VECTOR_ANGLE_X.equals(field) ? requested : number(angles, "targetX");
        double yDegrees = VECTOR_ANGLE_Y.equals(field) ? requested : number(angles, "targetY");
        access.createThrusters$setVectorAngles(xDegrees, yDegrees);
        return true;
    }

    // Read the numeric value
    private static double number(Map<String, Object> values, String key) {
        Object val = values == null ? null : values.get(key);
        return val instanceof Number num && Double.isFinite(num.doubleValue())
                ? num.doubleValue() : 0.0D;
    }

    // Resolve the boolean value
    private static boolean booleanValue(Map<String, Object> values, String key) {
        Object val = values == null ? null : values.get(key);
        return val instanceof Boolean bool && bool;
    }
}
