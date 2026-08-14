package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Random;

// Provide deterministic and stateful random values for graph nodes
final class AdvancedGraphRandom {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph random
    private AdvancedGraphRandom() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the random float
    static double randomFloat(long seed) {
        return new Random(seed).nextDouble();
    }

    // Get the random int
    static double randomInt(long seed, double maximum) {
        int bound = wholeNumber(maximum);
        return bound <= 0 ? 0 : new Random(seed).nextInt(bound);
    }

    // Get the random float in range
    static double randomFloatInRange(long seed, double minimum, double maximum) {
        double lower = finiteOrZero(minimum);
        double upper = finiteOrZero(maximum);
        if (lower > upper) {
            double swap = lower;
            lower = upper;
            upper = swap;
        }
        return Double.compare(lower, upper) == 0
                ? lower
                : new Random(seed).nextDouble(lower, upper);
    }

    // Get the random int in range
    static double randomIntInRange(long seed, double minimum, double maximum) {
        int lower = wholeNumber(minimum);
        int upper = wholeNumber(maximum);
        if (lower > upper) {
            int swap = lower;
            lower = upper;
            upper = swap;
        }
        return lower == upper
                ? lower
                : new Random(seed).nextLong(lower, (long) upper + 1L);
    }

    // Get the whole number
    private static int wholeNumber(double val) {
        if (!Double.isFinite(val)) {
            return 0;
        }
        long rounded = Math.round(val);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, rounded));
    }

    // Replace non-finite values with zero
    private static double finiteOrZero(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }
}
