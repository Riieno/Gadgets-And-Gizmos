package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletRemoteSessions;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Allow an authorised tablet session to keep a ComputerCraft computer menu open remotely
@Mixin(AbstractComputerBlockEntity.class)
abstract class ComputerCraftRemoteDesktopMenuMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the allow tablet remote desktop
    @Inject(method = "isUsable", at = @At("HEAD"), cancellable = true)
    private void ct$allowTabletRemoteDesktop(Player player, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof AbstractComputerBlockEntity computer
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && DiagnosticTabletRemoteSessions.isInteractionAuthorized(serverPlayer,
                computer.getBlockPos(), SimulatedHelper.getContainingSubLevelId(computer))) {
            callback.setReturnValue(true);
        }
    }
}
