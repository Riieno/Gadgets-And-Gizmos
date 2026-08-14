package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computed.ComputedEventCompat;
import com.rieno.gadgetsandgizmos.compat.computed.ComputedDisplayCompat;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Route Computed block entities through the display adapter
@Pseudo
@Mixin(
        targets = "dev.propulsionteam.computed.content.blocks.ComputerBlockEntity",
        remap = false
)
public abstract class ComputedComputerBlockEntityMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Show the computed widgets on ACC display
    @Inject(method = "showWidgets", at = @At("HEAD"), cancellable = true, require = 0)
    private void createthrusters$showComputedWidgetsOnAccDisplay(
            String target, List<?> definitions, CallbackInfo callback) {
        if (ComputedDisplayCompat.applyWidgets(
                (BlockEntity) (Object) this, target, definitions)) {
            callback.cancel();
        }
    }

    // Track the computed scheduler
    @Inject(method = "ensureScheduler", at = @At("RETURN"), require = 0)
    private void createthrusters$trackComputedScheduler(
            CallbackInfoReturnable<?> callback
    ) {
        ComputedEventCompat.trackComputer(
                (BlockEntity) (Object) this, callback.getReturnValue());
    }

    // Handle the untrack computed computer
    @Inject(method = "setRemoved", at = @At("HEAD"), require = 0)
    private void createthrusters$untrackComputedComputer(CallbackInfo callback) {
        ComputedEventCompat.untrackComputer((BlockEntity) (Object) this);
    }
}
