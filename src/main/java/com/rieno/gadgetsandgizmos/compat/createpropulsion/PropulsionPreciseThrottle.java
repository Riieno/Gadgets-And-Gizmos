package com.rieno.gadgetsandgizmos.compat.createpropulsion;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity;
import net.minecraft.util.Mth;

// Handle precise Propulsion throttle values
public final class PropulsionPreciseThrottle {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the propulsion precise throttle
    private PropulsionPreciseThrottle() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Normalize the propulsion precise throttle
    public static float normalize(double throttle) {
        if (!Double.isFinite(throttle)) {
            return 0.0F;
        }
        return (float) Mth.clamp(throttle, 0.0D, 1.0D);
    }

    // Resolve the controller
    public static AbstractThrusterBlockEntity resolveController(AbstractThrusterBlockEntity thruster) {
        if (thruster instanceof ThrusterBlockEntity multiblockThruster) {
            ThrusterBlockEntity controller = multiblockThruster.getControllerBE();
            if (controller != null) {
                return controller;
            }
        }
        return thruster;
    }

    // Apply the propulsion precise throttle
    public static boolean apply(AbstractThrusterBlockEntity thruster, double throttle) {
        if (thruster == null) {
            return false;
        }
        AbstractThrusterBlockEntity target = resolveController(thruster);
        if (!(target instanceof PropulsionDirectThrottleAccess access)) {
            return false;
        }
        access.createThrusters$setDirectThrottle(normalize(throttle));
        return true;
    }
}
