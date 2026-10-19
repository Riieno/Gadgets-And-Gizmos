package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity.ControlMode;
import com.rieno.gadgetsandgizmos.lib.control.OrientationMath;
import net.minecraft.world.phys.Vec3;

// Store a vector bearing tilt command
record TiltCommand(double xDegrees, double zDegrees, Vec3 direction, ControlMode sourceMode, boolean active) {
    // Get the neutral command
    static TiltCommand neutral(ControlMode sourceMode) {
        return new TiltCommand(0.0D, 0.0D, new Vec3(0.0D, 1.0D, 0.0D), sourceMode, false);
    }

    // Create the tilt command from a local vector
    static TiltCommand fromLocalVector(double localX, double localZ, double maxTiltDegrees,
                                       ControlMode sourceMode, boolean active) {
        return fromDegrees(localZ * maxTiltDegrees, localX * maxTiltDegrees, maxTiltDegrees, sourceMode, active);
    }

    // Create the tilt command from degrees
    static TiltCommand fromDegrees(double xDegrees, double zDegrees, double maxTiltDegrees,
                                   ControlMode sourceMode, boolean active) {
        double max = Math.max(0.0D, maxTiltDegrees);
        double length = Math.hypot(xDegrees, zDegrees);
        if (length > max && length > 1.0E-6D) {
            double scale = max / length;
            xDegrees *= scale;
            zDegrees *= scale;
        }
        Vec3 direction = OrientationMath.directionFromAngles(Math.toRadians(xDegrees), Math.toRadians(zDegrees));
        return new TiltCommand(xDegrees, zDegrees, direction, sourceMode, active);
    }

    // Copy the command with the source
    TiltCommand withSource(ControlMode sourceMode) {
        return new TiltCommand(xDegrees, zDegrees, direction, sourceMode, active);
    }
}

// Store direct tilt input
record DirectTiltInput(double localX, double localZ, double value) {
}

// Store a mounted sail sample
record MountedSailSample(double power, double radius) {
    static final MountedSailSample EMPTY = new MountedSailSample(0.0D, 0.0D);
}
