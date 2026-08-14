package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.util.QuietUse;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep joystick use from playing the normal block sound
@Mixin(MultiPlayerGameMode.class)
public class AnalogueJoystickQuietUseMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the quiet use intercept
    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lorg/apache/commons/lang3/mutable/MutableObject;<init>()V"), cancellable = true)
    private void ct$quietUseIntercept(LocalPlayer player, InteractionHand hand, BlockHitResult result,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        BlockState state = player.level().getBlockState(result.getBlockPos());
        Block block = state.getBlock();
        if (!(block instanceof QuietUse quietUse)) {
            return;
        }

        InteractionResult useResult = quietUse.quietUse(player, hand, result.getBlockPos(), state);
        if (useResult != null) {
            cir.setReturnValue(useResult);
        }
    }
}
