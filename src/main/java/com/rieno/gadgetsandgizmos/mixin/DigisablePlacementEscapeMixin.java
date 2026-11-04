package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.DigisablePlacementClient;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Return to the tablet without opening the pause menu when placement is cancelled
@Mixin(KeyboardHandler.class)
public final class DigisablePlacementEscapeMixin{
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void ct$cancelPlacement(long window, int key, int scan, int action, int modifiers, CallbackInfo ci){
        if(key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS && DigisablePlacementClient.cancel()) ci.cancel();
    }
}
