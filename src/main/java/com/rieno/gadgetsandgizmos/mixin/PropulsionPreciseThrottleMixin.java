package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionPreciseThrottle;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Preserve fractional throttle values inside Propulsion thruster state
@Pseudo
@Mixin(
        targets = "dev.propulsionteam.propulsionsimulated.compat.computercraft.ThrusterComputerHelpers",
        remap = false
)
public abstract class PropulsionPreciseThrottleMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the precise throttle
    @Inject(
            method = "setThrottleNormalized(Ldev/propulsionteam/propulsionsimulated/content/thruster/AbstractThrusterBlockEntity;D)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void createthrusters$setPreciseThrottle(
            AbstractThrusterBlockEntity thruster, double throttle, CallbackInfo callback
    ) {
        if (PropulsionPreciseThrottle.apply(thruster, throttle)) {
            callback.cancel();
        }
    }
}
