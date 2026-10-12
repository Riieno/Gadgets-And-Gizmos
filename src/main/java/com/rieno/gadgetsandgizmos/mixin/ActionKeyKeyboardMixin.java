package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.neoforge.client.CTActionKeys;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletClientInteraction;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Handle reader exit before a matching vanilla key opens another screen
@Mixin(KeyboardHandler.class)
public abstract class ActionKeyKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void exitReader(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callback) {
        if (action == GLFW.GLFW_PRESS && CTActionKeys.TABLET_READER_EXIT.matchesKeyboard(key, scanCode)
                && DiagnosticTabletClientInteraction.exitReader()) callback.cancel();
    }
}
