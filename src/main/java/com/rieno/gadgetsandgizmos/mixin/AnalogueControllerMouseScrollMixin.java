package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route mouse wheel input to the active controller
@Mixin(MouseHandler.class)
public class AnalogueControllerMouseScrollMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Consume the controller mouse scroll
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void ct$consumeControllerMouseScroll(long windowPointer, double horizontal, double vertical,
                                                  CallbackInfo ci) {
        if (AnalogueContraptionControllerClientHandler.onMouseScroll(horizontal, vertical)) {
            ci.cancel();
        }
    }
}
