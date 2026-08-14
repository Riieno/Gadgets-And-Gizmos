package com.rieno.gadgetsandgizmos.config;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Locale;

// Define the claw marker render modes
public enum ClawMarkerRenderMode {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    DEPTH,
    DECAL,
    PARTICLE,
    OFF;

    // Get the id
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    // Create the claw marker render mode from string
    public static ClawMarkerRenderMode fromString(String val) {
        if (val == null || val.isBlank()) {
            return PARTICLE;
        }
        String normalized = val.trim().toLowerCase(Locale.ROOT);
        for (ClawMarkerRenderMode mode : values()) {
            if (mode.id().equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown claw marker render mode: " + val);
    }
}
