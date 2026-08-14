package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerSignalBus;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add redstone control to Aeroworks Mechanical Servos
@Pseudo
@Mixin(targets = "com.mred231.aeroworks.content.servo.MechanicalServoBlockEntity", remap = false)
public abstract class AeroworksMechanicalServoRedstoneMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the virtual signal
    @Inject(method = "readSignal", at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$readVirtualSignal(CallbackInfoReturnable<Integer> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null) {
            return;
        }
        int current = cir.getReturnValue();
        if (current >= 15) {
            return;
        }
        int injected = ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(self.getLevel(), self.getBlockPos());
        if (injected > current) {
            cir.setReturnValue(injected);
        }
    }
}
