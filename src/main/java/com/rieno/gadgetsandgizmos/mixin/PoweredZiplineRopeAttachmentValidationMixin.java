package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Check Powered Zipline rope attachments across sub-levels
@Mixin(value = RopeStrandHolderBehavior.class, priority = 1100)
public class PoweredZiplineRopeAttachmentValidationMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current owned server strand
    @Shadow
    @Nullable
    private ServerRopeStrand ownedServerStrand;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Validate the zipline rope attachment
    @Inject(method = "destroyRopeIfAttachmentBroken", at = @At("HEAD"), cancellable = true)
    private void createthrusters$validateZiplineRopeAttachment(CallbackInfo ci) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        if (self.blockEntity instanceof PoweredZiplineBlockEntity zipline
                && zipline.handleHangingRopeAttachmentValidation(self, ownedServerStrand)) {
            ci.cancel();
        }
    }
}
