package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Clean legacy zipline ropes when their holder is removed
@Mixin(RopeStrandHolderBehavior.class)
public class PoweredZiplineRopeHolderDestroyLegacyMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Destroy the matching extra zipline rope
    @Inject(method = "destroyRope", at = @At("HEAD"), cancellable = true)
    private void createthrusters$destroyMatchingExtraZiplineRope(@Nullable ServerPlayer player,
                                                                 @Nullable Vec3 ropeDropPos,
                                                                 CallbackInfo ci) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        if (self.blockEntity instanceof PoweredZiplineBlockEntity zipline
                && zipline.destroyExtraHangingRopeNear(player, ropeDropPos)) {
            ci.cancel();
        }
    }
}
