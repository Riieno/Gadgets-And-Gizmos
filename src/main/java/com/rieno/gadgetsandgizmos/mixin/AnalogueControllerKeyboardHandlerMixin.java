package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.AccDisplayGuiProjection;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler;
import com.rieno.gadgetsandgizmos.lib.client.input.ClientActionKey;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route raw keyboard input to displays and the active controller
@Mixin(KeyboardHandler.class)
public class AnalogueControllerKeyboardHandlerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Forward the analogue controller keys
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void ct$forwardAnalogueControllerKeys(long windowPointer, int key, int scanCode, int action,
                                                   int modifiers, CallbackInfo ci) {
        if(!ClientActionKey.canHandleGameplayInput(windowPointer)) return;

        if (AccDisplayGuiProjection.onRawKeyInput(key, scanCode, action, modifiers)) {
            ci.cancel();
            return;
        }

        if (AnalogueContraptionControllerClientHandler.onKeyInput(key, scanCode, action)) {
            ci.cancel();
        }
    }
}
