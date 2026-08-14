package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

// Evaluate editable curves with clamped interpolation between control points
public final class AdvancedGraphCurve {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final double DEFAULT_MINIMUM = 0.0D;
    public static final double DEFAULT_MAXIMUM = 1.0D;
    public static final double DEFAULT_SPEED = 0.1D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph curve
    private AdvancedGraphCurve() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Evaluate the advanced graph curve
    public static double evaluate(CompoundTag data, double pos) {
        var points = data.getList("Points", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (points.isEmpty()) return Mth.clamp(pos, 0, 1);
        CompoundTag first = points.getCompound(0);
        if (pos <= first.getDouble("X")) return first.getDouble("Y");
        for (int idx = 1; idx < points.size(); idx++) {
            CompoundTag prev = points.getCompound(idx - 1);
            CompoundTag next = points.getCompound(idx);
            if (pos <= next.getDouble("X")) {
                double span = next.getDouble("X") - prev.getDouble("X");
                double amount = span == 0 ? 0 : (pos - prev.getDouble("X")) / span;
                if (!"bezier".equals(data.getString("CurveType"))) {
                    return Mth.lerp(amount, prev.getDouble("Y"), next.getDouble("Y"));
                }
                CompoundTag before = points.getCompound(Math.max(0, idx - 2));
                CompoundTag after = points.getCompound(Math.min(points.size() - 1, idx + 1));
                double startSlope = slope(before, next);
                double endSlope = slope(prev, after);
                double t2 = amount * amount;
                double t3 = t2 * amount;
                double val = (2 * t3 - 3 * t2 + 1) * prev.getDouble("Y")
                        + (t3 - 2 * t2 + amount) * startSlope * span
                        + (-2 * t3 + 3 * t2) * next.getDouble("Y")
                        + (t3 - t2) * endSlope * span;
                return Mth.clamp(val, 0, 1);
            }
        }
        return points.getCompound(points.size() - 1).getDouble("Y");
    }

    // Advance the advanced graph curve
    public static double advance(double pos, double speed, boolean forward) {
        double finitePosition = Double.isFinite(pos) ? pos : 0.0D;
        double finiteSpeed = Double.isFinite(speed) ? Math.abs(speed) : 0.0D;
        return Mth.clamp(finitePosition + (forward ? finiteSpeed : -finiteSpeed), 0.0D, 1.0D);
    }

    // Map the value
    public static double mapValue(CompoundTag data, double pos, double val,
                                  double minimum, double maximum) {
        double lower = Math.min(finiteOrDefault(minimum, DEFAULT_MINIMUM),
                finiteOrDefault(maximum, DEFAULT_MAXIMUM));
        double upper = Math.max(finiteOrDefault(minimum, DEFAULT_MINIMUM),
                finiteOrDefault(maximum, DEFAULT_MAXIMUM));
        double target = Mth.clamp(finiteOrDefault(val, upper), lower, upper);
        double curveAmount = Mth.clamp(evaluate(data, Mth.clamp(
                finiteOrDefault(pos, 0.0D), 0.0D, 1.0D)), 0.0D, 1.0D);
        return Mth.clamp(Mth.lerp(curveAmount, lower, target), lower, upper);
    }

    // Check if this is an input pulse
    public static boolean isInputPulse(String eventId) {
        return eventId != null && (eventId.startsWith("input:")
                || eventId.startsWith("key:") || eventId.startsWith("mouse:"));
    }

    // Check if this is an active input pulse
    public static boolean isActiveInputPulse(String eventId) {
        return isInputPulse(eventId) && !eventId.endsWith(":inactive")
                && !eventId.endsWith(":released");
    }

    // Use the fallback when the value is not finite
    private static double finiteOrDefault(double val, double fallback) {
        return Double.isFinite(val) ? val : fallback;
    }

    // Get the slope
    private static double slope(CompoundTag from, CompoundTag to) {
        double span = to.getDouble("X") - from.getDouble("X");
        return span == 0 ? 0 : (to.getDouble("Y") - from.getDouble("Y")) / span;
    }
}
