package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep existing zipline ropes on older Simulated versions
@Mixin(RopeStrandHolderBehavior.class)
public class PoweredZiplineMultiRopeCreateLegacyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the zipline rope without replacing existing
    @Inject(method = "createRope", at = @At("HEAD"), cancellable = true)
    private void createthrusters$createZiplineRopeWithoutReplacingExisting(RopeStrandHolderBehavior target,
                                                                            CallbackInfoReturnable<Boolean> cir) {
        RopeStrandHolderBehavior src = (RopeStrandHolderBehavior) (Object) this;
        if (src.blockEntity instanceof PoweredZiplineBlockEntity zipline && !zipline.isCreatingZiplineRope()) {
            cir.setReturnValue(zipline.tryCreateRopeFromSimulated(src, target));
            return;
        }
        if (target.blockEntity instanceof PoweredZiplineBlockEntity zipline && !zipline.isCreatingZiplineRope()) {
            cir.setReturnValue(zipline.tryCreateRopeFromSimulated(src, target));
        }
    }
}
