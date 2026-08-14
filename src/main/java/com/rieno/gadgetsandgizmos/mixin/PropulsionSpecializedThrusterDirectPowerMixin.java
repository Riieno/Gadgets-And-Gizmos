package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionDirectThrottleAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Apply direct throttle to specialized Propulsion thrusters
@Pseudo
@Mixin(
        targets = {
                "dev.propulsionteam.propulsionsimulated.content.thruster.creative_thruster.CreativeThrusterBlockEntity",
                "dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity",
                "dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity"
        },
        remap = false
)
public abstract class PropulsionSpecializedThrusterDirectPowerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the direct power
    @Inject(method = "getPower", at = @At("HEAD"), cancellable = true, require = 0)
    private void createThrusters$readDirectPower(
            CallbackInfoReturnable<Float> callback
    ) {
        PropulsionDirectThrottleAccess access =
                (PropulsionDirectThrottleAccess) this;
        if (access.createThrusters$hasDirectThrottle()) {
            callback.setReturnValue(access.createThrusters$getDirectThrottle());
        }
    }
}
