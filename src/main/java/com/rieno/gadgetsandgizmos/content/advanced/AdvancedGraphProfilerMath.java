package com.rieno.gadgetsandgizmos.content.advanced;

// Calculate graph profiler timings
public final class AdvancedGraphProfilerMath {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph profiler math
    private AdvancedGraphProfilerMath() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Calculate ticks per second
    public static double ticksPerSecond(double millisecondsPerTick, double targetTicksPerSecond) {
        double target = Math.max(0.0D, targetTicksPerSecond);
        if (millisecondsPerTick <= 0.0D) {
            return target;
        }
        return Math.min(target, 1000.0D / millisecondsPerTick);
    }

    // Get the exponential moving average
    public static double exponentialMovingAverage(
            double prev, double sample, double smoothing) {
        if (!Double.isFinite(sample)) {
            return Double.isFinite(prev) ? prev : 0.0D;
        }
        if (!Double.isFinite(prev)) {
            return sample;
        }
        double amount = Math.max(0.0D, Math.min(1.0D, smoothing));
        return prev + (sample - prev) * amount;
    }
}
