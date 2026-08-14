package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.aeroworks.AeroworksControllerCompat;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Expose Aeroworks console controls to the analogue controller
@Pseudo
@Mixin(targets = "com.mred231.aeroworks.content.controls.ConsoleBlockEntity", remap = false)
public abstract class AeroworksConsoleControllerCompatMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reapply the controller signals
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mred231/aeroworks/content/controls/ConsoleBlockEntity;pushNetworkIfChanged()V",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void ct$reapplyControllerSignals(CallbackInfo callbackInfo) {
        AeroworksControllerCompat.reapplyDirectSignals((BlockEntity) (Object) this);
    }
}
