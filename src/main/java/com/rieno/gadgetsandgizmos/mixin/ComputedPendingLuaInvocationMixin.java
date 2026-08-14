package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computed.ComputedEventCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Route pending Computed Lua calls through the display adapter
@Pseudo
@Mixin(
        targets = "dev.propulsionteam.computed.lua.runtime.PendingLuaInvocation",
        remap = false
)
public abstract class ComputedPendingLuaInvocationMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current endpoint host
    @Shadow
    @Final
    private Object endpointHost;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Forward the computed event
    @Inject(method = "emit", at = @At("HEAD"), require = 0)
    private void createthrusters$forwardComputedEvent(
            String name, List<?> values, CallbackInfo callback
    ) {
        ComputedEventCompat.forwardFromComputed(endpointHost, name, values);
    }
}
