package com.rieno.gadgetsandgizmos.compat.createpropulsion;

// Expose direct Propulsion throttle control
public interface PropulsionDirectThrottleAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the direct throttle
    void createThrusters$setDirectThrottle(double throttle);

    // Clear the direct throttle
    void createThrusters$clearDirectThrottle();

    // Check if this has direct throttle
    boolean createThrusters$hasDirectThrottle();

    // Get the direct throttle
    float createThrusters$getDirectThrottle();
}
