package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

// Handle Create slider input
final class CTSliderInputHelper {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double SHIFT_DRAG_SCALE = 0.25D;
    private static final double PERCENT_STEP = 0.05D;
    private static final double DEGREE_STEP = 5.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT slider input
    private CTSliderInputHelper() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the adjust percent
    static float adjustPercent(double directValue, double dragStartDirectValue, double dragStartValue) {
        double adjusted = adjustedValue(directValue, dragStartDirectValue, dragStartValue, PERCENT_STEP);
        return (float) Mth.clamp(adjusted, 0.0D, 1.0D);
    }

    // Get the adjust degrees
    static double adjustDegrees(double directValue, double dragStartDirectValue, double dragStartValue,
                                double minAllowed, double maxAllowed) {
        double adjusted = adjustedValue(directValue, dragStartDirectValue, dragStartValue, DEGREE_STEP);
        return Mth.clamp(adjusted, minAllowed, maxAllowed);
    }

    // Get the adjusted value
    private static double adjustedValue(double directValue, double dragStartDirectValue, double dragStartValue,
                                        double controlStep) {
        double val = directValue;
        if (Screen.hasShiftDown() && !Screen.hasControlDown()) {
            val = dragStartValue + (directValue - dragStartDirectValue) * SHIFT_DRAG_SCALE;
        }
        if (Screen.hasControlDown()) {
            val = snap(val, controlStep);
        }
        return val;
    }

    // Snap the CT slider input
    private static double snap(double val, double step) {
        if (step <= 0.0D) {
            return val;
        }
        return Math.round(val / step) * step;
    }
}
