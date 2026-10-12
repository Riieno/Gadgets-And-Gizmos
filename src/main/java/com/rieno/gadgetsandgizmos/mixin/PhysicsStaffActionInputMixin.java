package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.neoforge.client.CTActionKeys;
import net.minecraft.client.Minecraft;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Use the addon's configurable rotation action for its powered staff
@Mixin(PhysicsStaffClientHandler.class)
public abstract class PhysicsStaffActionInputMixin {
    @Inject(method = "isRotating", at = @At("HEAD"), cancellable = true)
    private void rotateBoundStaff(CallbackInfoReturnable<Boolean> callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) {
            callback.setReturnValue(((PhysicsStaffClientHandler) (Object) this).getDragSession() != null
                    && CTActionKeys.STAFF_ROTATE.isHeld());
        }
    }
}
