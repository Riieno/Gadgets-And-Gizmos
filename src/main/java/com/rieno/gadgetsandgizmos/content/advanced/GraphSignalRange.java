package com.rieno.gadgetsandgizmos.content.advanced;

// Convert normalized graph redstone signals
public final class GraphSignalRange {
    public static final String REDSTONE_SIGNAL_PORT = "redstone_signal_strength";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph signal range
    private GraphSignalRange() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the graph signal range from normalized redstone
    static double fromNormalizedRedstone(double val) {
        if (Double.isNaN(val)) {
            return 0.0D;
        }
        return Math.round(Math.max(0.0D, Math.min(1.0D, val)) * 15.0D);
    }

    // Convert the graph signal range to normalized redstone
    public static int outputStrength(double val) {
        if (Double.isNaN(val)) {
            return 0;
        }
        // Graph numbers share a double representation. Treat 0..1 as a
        // fraction of full power, and larger numbers as redstone strengths.
        double strength = val <= 1.0D ? val * 15.0D : val;
        return (int) Math.round(Math.max(0.0D, Math.min(15.0D, strength)));
    }

    static double toNormalizedRedstone(double val) {
        return outputStrength(val) / 15.0D;
    }

    // Preserve continuous values for analogue receivers; quantize only redstone.
    static double toDirectControl(double val) {
        if (Double.isNaN(val)) return 0.0D;
        return val > 1.0D ? toNormalizedRedstone(val) : Math.max(0.0D, val);
    }
}
