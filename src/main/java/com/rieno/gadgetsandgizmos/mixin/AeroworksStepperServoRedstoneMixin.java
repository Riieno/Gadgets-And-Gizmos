package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerSignalBus;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add redstone control to Aeroworks Stepper Servos
@Pseudo
@Mixin(targets = "com.mred231.aeroworks.content.servo.StepperServoBlockEntity", remap = false)
public abstract class AeroworksStepperServoRedstoneMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the virtual face signal
    @Inject(method = "readFaceSignal", at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$readVirtualFaceSignal(Direction face, CallbackInfoReturnable<Integer> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null || face == null) {
            return;
        }
        int current = cir.getReturnValue();
        if (current >= 15) {
            return;
        }
        int injected = ContraptionNetworkLinkerSignalBus.getInjectedSignal(self.getLevel(), self.getBlockPos(), face);
        if (injected > current) {
            cir.setReturnValue(injected);
        }
    }
}
