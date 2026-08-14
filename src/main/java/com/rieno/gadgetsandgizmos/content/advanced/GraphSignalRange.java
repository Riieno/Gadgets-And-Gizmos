package com.rieno.gadgetsandgizmos.content.advanced;

// Convert normalized graph redstone signals
final class GraphSignalRange {
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
    static double toNormalizedRedstone(double val) {
        if (Double.isNaN(val)) {
            return 0.0D;
        }
        double signal = Math.max(0.0D, Math.min(15.0D, Math.round(val)));
        return signal / 15.0D;
    }
}
