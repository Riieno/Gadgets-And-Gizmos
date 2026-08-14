package com.rieno.gadgetsandgizmos.compat.controller;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueAxis;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;

// Convert supported orientation providers into the addon's shared payload
public final class OrientationAdapters {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the orientation adapters
    private OrientationAdapters() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the controller angles
    public static double[] resolveControllerAngles(AnalogueContraptionControllerBlockEntity controller, double maxTiltRadians) {
        if (controller == null) {
            return null;
        }
        AnalogueAxis pitchAxis = controller.getAxis("pitch");
        AnalogueAxis rollAxis = controller.getAxis("roll");
        if (pitchAxis == null && rollAxis == null) {
            return null;
        }

        double pitch = pitchAxis == null ? 0.0D : pitchAxis.getSignedValue();
        double roll = rollAxis == null ? 0.0D : rollAxis.getSignedValue();
        if (Math.abs(pitch) < 1.0E-6D && Math.abs(roll) < 1.0E-6D) {
            return null;
        }
        return new double[]{pitch * maxTiltRadians, roll * maxTiltRadians};
    }

    // Resolve the navigation angles
    public static double[] resolveNavigationAngles(NavigationTableExtensionAccess nav) {
        if (nav == null) {
            return null;
        }
        DirectionalAnalogSnapshot snapshot = nav.ct$getDirectionalSnapshot();
        if (snapshot != null && snapshot.magnitude() > 1.0E-6D) {
            double xAngle = Math.atan(snapshot.localZ());
            double zAngle = Math.atan(snapshot.localX());
            return new double[]{xAngle, zAngle};
        }

        float relativeAngleDeg = nav.ct$getRelativeAngleDeg();
        if (!Float.isFinite(relativeAngleDeg)) {
            return null;
        }
        double zAngle = Math.toRadians(Math.max(-90.0F, Math.min(90.0F, relativeAngleDeg)));
        if (Math.abs(zAngle) < 1.0E-6D) {
            return null;
        }
        return new double[]{0.0D, zAngle};
    }
}
