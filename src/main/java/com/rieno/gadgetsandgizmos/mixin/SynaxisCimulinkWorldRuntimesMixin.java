package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.synaxis.SynaxisNamedEventCompat;
import com.verr1.synaxis.foundation.cimulink.game.runtime.CimulinkWorldRuntimes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Register shared Named Event components after Synaxis has created its component registry
@Pseudo
@Mixin(targets = "com.verr1.synaxis.foundation.cimulink.game.runtime.CimulinkWorldRuntimes", remap = false)
public abstract class SynaxisCimulinkWorldRuntimesMixin {
    @Inject(method = "register", at = @At("RETURN"), require = 0)
    private static void createthrusters$registerNamedEventComponents(CallbackInfo callback) {
        SynaxisNamedEventCompat.install(CimulinkWorldRuntimes.componentRegistry());
    }
}
