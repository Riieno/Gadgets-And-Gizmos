package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerSignalBus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Bridge Block State signals
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSignalBridgeMixin {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Inject the linker state signal
    @Inject(method = "getSignal(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerStateSignal(BlockGetter getter,
                                            BlockPos pos,
                                            Direction direction,
                                            CallbackInfoReturnable<Integer> cir) {
        if (!(getter instanceof Level level)) {
            return;
        }
        int current = cir.getReturnValue();
        if (current >= 15) {
            return;
        }
        int injected = ContraptionNetworkLinkerSignalBus.getInjectedSignal(level, pos, direction);
        if (injected > current) {
            cir.setReturnValue(injected);
        }
    }

    // Inject the linker state direct signal
    @Inject(method = "getDirectSignal(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerStateDirectSignal(BlockGetter getter,
                                                  BlockPos pos,
                                                  Direction direction,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (!(getter instanceof Level level)) {
            return;
        }
        int current = cir.getReturnValue();
        if (current >= 15) {
            return;
        }
        int injected = ContraptionNetworkLinkerSignalBus.getInjectedSignal(level, pos, direction);
        if (injected > current) {
            cir.setReturnValue(injected);
        }
    }
}
