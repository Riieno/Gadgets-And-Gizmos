package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.util.OxidizedFuel;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

// Keep oxidized fuel out of normal Create Fluid Tanks
@Mixin(FluidTankBlockEntity.class)
public abstract class OxidizedCreateFluidTankStorageMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Replace only the normal Create tank inventory
    @Redirect(
            method = "createInventory",
            at = @At(value = "NEW", target = "com/simibubi/create/foundation/fluid/SmartFluidTank")
    )
    private SmartFluidTank createthrusters$createRestrictedTank(int capacity,
                                                                 Consumer<FluidStack> updateCallback) {
        return new SmartFluidTank(capacity, updateCallback) {
            // Reject oxidized stacks from normal Create Fluid Tanks
            @Override
            public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
                if (OxidizedFuel.isOxidized(resource)) {
                    return 0;
                }
                return super.fill(resource, action);
            }
        };
    }
}
