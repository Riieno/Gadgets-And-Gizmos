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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Stop oxidized fuel from entering unsupported creative tanks
@Mixin(targets = "com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity$CreativeSmartFluidTank",
        remap = false)
public abstract class OxidizedCreativeFluidTankStorageMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the prevent creative oxidized storage
    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void createthrusters$preventCreativeOxidizedStorage(FluidStack resource,
            IFluidHandler.FluidAction action, CallbackInfoReturnable<Integer> callback) {
        if (OxidizedFuel.isOxidized(resource) && !OxidizedFuelStorageAccess.isAllowed()) {
            callback.setReturnValue(0);
        }
    }
}
