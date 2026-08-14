package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.aeronautics.CreateThrustersAeroLevititeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add Simulated Service Util Aero Levitite fallback
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.service.ServiceUtil", remap = false)
public abstract class SimulatedServiceUtilAeroLevititeFallbackMixin {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the provide aero levitite service fallback
    @Inject(method = "load", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ct$provideAeroLevititeServiceFallback(Class<?> serviceClass, CallbackInfoReturnable<Object> cir) {
        if (serviceClass == null || !"dev.eriksonn.aeronautics.service.AeroLevititeService".equals(serviceClass.getName())) {
            return;
        }

        try {
            Class<?> neoForgeImpl = Class.forName(
                    "dev.eriksonn.aeronautics.neoforge.service.NeoForgeAeroLevititeService",
                    true,
                    Thread.currentThread().getContextClassLoader()
            );
            cir.setReturnValue(neoForgeImpl.getDeclaredConstructor().newInstance());
            return;
        } catch (Throwable ignored) {

        }

        cir.setReturnValue(new CreateThrustersAeroLevititeService());
    }
}