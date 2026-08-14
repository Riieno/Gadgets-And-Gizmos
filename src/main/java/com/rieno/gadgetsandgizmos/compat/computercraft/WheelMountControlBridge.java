package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

// Expose wheel mount controls to ComputerCraft
public interface WheelMountControlBridge {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the left override
    float ct$getLeftOverride();

    // Get the right override
    float ct$getRightOverride();

    // Get the brake override
    float ct$getBrakeOverride();

    // Get the effective steering signal
    int ct$getEffectiveSteeringSignal();

    // Set the direct inputs
    void ct$setDirectInputs(float left, float right, float brake);

    // Get the physical sample
    PhysicalSample ct$getPhysicalSample();

    // Store the physical sample
    record PhysicalSample(long samples, Vec3 localPosition, Vec3 cumulativeImpulse) {
        // Initialize the physical sample
        public PhysicalSample {
            samples = Math.max(0L, samples);
            localPosition = localPosition == null ? Vec3.ZERO : localPosition;
            cumulativeImpulse = cumulativeImpulse == null ? Vec3.ZERO : cumulativeImpulse;
        }
    }
}
