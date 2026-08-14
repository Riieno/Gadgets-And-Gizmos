package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Remove invalid Create contraptions before Sable initializes their mass
@Mixin(AbstractContraptionEntity.class)
public abstract class CreateContraptionEmptyAssemblyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current Create contraption
    @Shadow
    protected Contraption contraption;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Discard contraptions without a physical block
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void createthrusters$discardEmptyContraption(CallbackInfo callbackInfo) {
        if (contraption == null || contraption.getBlocks().values().stream()
                .anyMatch(block -> !block.state().isAir())) {
            return;
        }
        ((AbstractContraptionEntity) (Object) this).discard();
        callbackInfo.cancel();
    }
}
