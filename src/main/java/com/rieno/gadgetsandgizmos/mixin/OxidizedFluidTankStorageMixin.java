package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.util.OxidizedFuel;
import com.rieno.gadgetsandgizmos.util.OxidizedFuelStorageAccess;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Stop oxidized fuel from entering unsupported tanks
@Mixin(value = FluidTank.class, remap = false)
public abstract class OxidizedFluidTankStorageMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the prevent generic oxidized storage
    @Inject(method = "fill", at = @At("HEAD"), cancellable = true, remap = false)
    private void createthrusters$preventGenericOxidizedStorage(FluidStack resource,
            IFluidHandler.FluidAction action, CallbackInfoReturnable<Integer> callback) {
        if (OxidizedFuel.isOxidized(resource) && !OxidizedFuelStorageAccess.isAllowed()) {
            callback.setReturnValue(0);
        }
    }
}
