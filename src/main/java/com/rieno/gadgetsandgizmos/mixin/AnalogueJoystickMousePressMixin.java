package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueJoystickClientHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route mouse buttons to the active controller or joystick
@Mixin(MouseHandler.class)
public class AnalogueJoystickMousePressMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the gate analogue joystick mouse press
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void ct$gateAnalogueJoystickMousePress(long windowPointer, int button, int action, int modifiers,
                                                   CallbackInfo ci) {

        if (AnalogueContraptionControllerClientHandler.onMouseButton(button, action)) {
            ci.cancel();
            return;
        }

        if (AnalogueJoystickClientHandler.onMouseButton(button, action)) {
            ci.cancel();
        }
    }
}
