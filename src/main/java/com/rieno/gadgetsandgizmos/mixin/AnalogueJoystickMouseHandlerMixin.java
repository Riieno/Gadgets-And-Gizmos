package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueJoystickClientHandler;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Route raw mouse movement to the active controller or joystick
@Mixin(MouseHandler.class)
public class AnalogueJoystickMouseHandlerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Consume the joystick mouse turn
    @Inject(method = "turnPlayer", cancellable = true,
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void ct$consumeJoystickMouseTurn(double partialTicks, CallbackInfo ci,
                                             @Local(ordinal = 4) double yaw,
                                             @Local(ordinal = 5) double pitch,
                                             @Local(ordinal = 0) int invertY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return;
        }

        if (AnalogueContraptionControllerClientHandler.onMouseMove(
                yaw, pitch * (double) invertY)) {
            ci.cancel();
            return;
        }

        if (AnalogueJoystickClientHandler.onMouseMove(
                yaw, pitch * (double) invertY)) {
            ci.cancel();
        }
    }
}
