package com.rieno.gadgetsandgizmos.mixin;

import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Return sneaking staff clicks beside Backtanks to the normal placement handler
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler$PhysicsStaffMouseHandler")
public class PhysicsStaffAnchorPlacementMixin {
    // Allow the staff to place before Simulated starts a drag
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void ct$allowSneakPlacement(int button, int action, KeyMapping keyMapping,
                                        CallbackInfoReturnable<InteractCallback.Result> cir) {
        var player = Minecraft.getInstance().player;
        if (player == null || !com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) return;
        if (com.rieno.gadgetsandgizmos.neoforge.client.PhysicsStaffActionClient.isPlacementUse()) {
            cir.setReturnValue(InteractCallback.Result.empty());
        } else {
            cir.setReturnValue(new InteractCallback.Result(true));
        }
    }

    // Reserve the upstream attack callback for staff actions sampled from configured keys
    @Inject(method = "onAttack", at = @At("HEAD"), cancellable = true)
    private void ct$boundLock(int modifiers, int action, KeyMapping keyMapping,
                             CallbackInfoReturnable<InteractCallback.Result> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) {
            cir.setReturnValue(new InteractCallback.Result(true));
        }
    }

    // Keep the scroll speed modifier configurable for the powered staff
    @org.spongepowered.asm.mixin.injection.Redirect(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean ct$boundFastScroll(KeyMapping mapping) {
        var player = Minecraft.getInstance().player;
        return player != null && com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)
                ? com.rieno.gadgetsandgizmos.neoforge.client.CTActionKeys.STAFF_FAST.isHeld() : mapping.isDown();
    }
}
