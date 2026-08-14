package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;

// Accept Smart Gearbox servo angles
public interface SmartGearboxServoAngleAcceptor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this can accept smart gearbox servo angle
    default boolean canAcceptSmartGearboxServoAngle(Direction inputSide) {
        return true;
    }

    // Accept the smart gearbox servo angle degrees
    boolean acceptSmartGearboxServoAngleDegrees(double angleDegrees, Direction inputSide);

    // Clear the smart gearbox servo angle
    default void clearSmartGearboxServoAngle(Direction inputSide) {
    }
}
