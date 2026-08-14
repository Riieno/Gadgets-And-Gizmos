package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.util.OxidizedFuel;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Show the oxidized fuel name on matching fluid stacks
@Mixin(FluidStack.class)
public abstract class OxidizedFluidStackNameMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the oxidized name
    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void createthrusters$useOxidizedName(CallbackInfoReturnable<Component> callback) {
        FluidStack stack = (FluidStack) (Object) this;
        if (OxidizedFuel.isOxidized(stack)) {
            callback.setReturnValue(Component.translatable("createthrusters.fluid.oxidized",
                    stack.getFluidType().getDescription(stack)));
        }
    }
}
