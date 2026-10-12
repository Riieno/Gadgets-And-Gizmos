package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.neoforge.client.CTActionKeys;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletClientInteraction;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Allow reader exit to be assigned to a mouse button
@Mixin(MouseHandler.class)
public abstract class ActionKeyMouseMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void exitReader(long window, int button, int action, int modifiers, CallbackInfo callback) {
        if (action == GLFW.GLFW_PRESS && CTActionKeys.TABLET_READER_EXIT.matchesMouse(button)
                && DiagnosticTabletClientInteraction.exitReader()) callback.cancel();
    }
}
