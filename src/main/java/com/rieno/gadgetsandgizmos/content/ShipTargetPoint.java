package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Define the ship reference points schedules can use for navigation and docking
public enum ShipTargetPoint {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    CENTER_OF_MASS("center_of_mass", 0, 0, 0, true),
    CENTER("center", 0, 0, 0, false),
    TOP("top", 0, 1, 0, false),
    BOTTOM("bottom", 0, -1, 0, false),
    LEFT("left", -1, 0, 0, false),
    RIGHT("right", 1, 0, 0, false),
    FRONT("front", 0, 0, 1, false),
    BACK("back", 0, 0, -1, false),
    FRONT_LEFT("front_left", -1, 0, 1, false),
    FRONT_RIGHT("front_right", 1, 0, 1, false),
    BACK_LEFT("back_left", -1, 0, -1, false),
    BACK_RIGHT("back_right", 1, 0, -1, false),
    DOCKING_CONNECTOR("docking_connector", 0, 0, 0, false);

    private static final List<String> SERIALIZED_VALUES = Arrays.stream(values())
            .map(ShipTargetPoint::serializedName)
            .toList();

    // Serialized name
    private final String serializedName;
    // Right
    private final int right;
    // Vertical offset
    private final int up;
    // Forward
    private final int forward;
    // Tracks whether center of mass is set
    private final boolean centerOfMass;

    // Initialize the ship target point
    ShipTargetPoint(
            String serializedName,
            int right,
            int up,
            int forward,
            boolean centerOfMass
    ) {
        this.serializedName = serializedName;
        this.right = right;
        this.up = up;
        this.forward = forward;
        this.centerOfMass = centerOfMass;
    }

    // Get the serialized name
    public String serializedName() {
        return serializedName;
    }

    // Get the right
    public int right() {
        return right;
    }

    // Get the upward block offset
    public int up() {
        return up;
    }

    // Forward the ship target point
    public int forward() {
        return forward;
    }

    // Check if the target uses its center of mass
    public boolean centerOfMass() {
        return centerOfMass;
    }

    // Check if the target uses a docking connector
    public boolean dockingConnector() {
        return this == DOCKING_CONNECTOR;
    }

    // Get the serialized values
    public static List<String> serializedValues() {
        return SERIALIZED_VALUES;
    }

    // Create the ship target point from serialized
    public static ShipTargetPoint fromSerialized(String val) {
        String normalized = val == null
                ? "" : val.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.startsWith("docking_connector")) {
            return DOCKING_CONNECTOR;
        }
        for (ShipTargetPoint point : values()) {
            if (point.serializedName.equals(normalized)) {
                return point;
            }
        }
        return CENTER_OF_MASS;
    }

    // Get the connector index from serialized
    public static int connectorIndexFromSerialized(String val) {
        String normalized = val == null ? "" : val.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (!normalized.startsWith("docking_connector") || separator < 0
                || separator + 1 >= normalized.length()) {
            return -1;
        }
        try {
            return Math.max(-1, Integer.parseInt(normalized.substring(separator + 1)));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
