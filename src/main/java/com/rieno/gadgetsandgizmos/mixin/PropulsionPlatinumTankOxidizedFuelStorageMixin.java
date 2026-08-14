package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.rieno.gadgetsandgizmos.util.OxidizedFuelStorageAccess;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.function.Consumer;

// Add support for Oxidized Fuel to the Platinum Fluid Tanks
@Pseudo
@Mixin(
    targets = "dev.propulsionteam.propulsionsimulated.content.platinum.PlatinumFluidTankBlockEntity",
    remap = false
)


/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

public abstract class PropulsionPlatinumTankOxidizedFuelStorageMixin {
    @Redirect(method = "createInventory", at = @At(value = "NEW",
            target = "com/simibubi/create/foundation/fluid/SmartFluidTank"))

    private SmartFluidTank createthrusters$createOxidiFluidTank(int capacity,
        Consumer<FluidStack> updateCallback){
            return new SmartFluidTank(capacity,  updateCallback){
                // Push Oxidized Fuel to the Platinum Fluid Tank
                @Override
                public int fill(FluidStack resource, IFluidHandler.FluidAction action){
                    return OxidizedFuelStorageAccess.allow(()->super.fill(resource, action));
                }
            };
        }


}
