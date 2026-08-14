package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerSignalBus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Bridge Level signals
@Mixin(SignalGetter.class)
public interface LevelSignalBridgeMixin {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Inject the linker signal
    @Inject(method = "getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerSignal(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof Level level)) {
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

    // Inject the linker direct signal
    @Inject(method = "getDirectSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerDirectSignal(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof Level level)) {
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

    // Inject the linker direct signal
    @Inject(method = "getDirectSignalTo(Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerDirectSignalTo(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof Level level)) {
            return;
        }
        int current = cir.getReturnValue();
        if (current >= 15) {
            return;
        }
        int injected = ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(level, pos);
        if (injected > current) {
            cir.setReturnValue(injected);
        }
    }

    // Inject the linker control input signal
    @Inject(method = "getControlInputSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerControlInputSignal(BlockPos pos,
                                                   Direction direction,
                                                   boolean diodeOnly,
                                                   CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof Level level)) {
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

    // Inject the linker has signal
    @Inject(method = "hasSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerHasSignal(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Level level)) {
            return;
        }
        if (cir.getReturnValue()) {
            return;
        }
        if (ContraptionNetworkLinkerSignalBus.getInjectedSignal(level, pos, direction) > 0) {
            cir.setReturnValue(true);
        }
    }

    // Inject the linker neighbor signal
    @Inject(method = "hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerNeighborSignal(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Level level)) {
            return;
        }
        if (cir.getReturnValue()) {
            return;
        }
        if (ContraptionNetworkLinkerSignalBus.hasNeighborSignal(level, pos)) {
            cir.setReturnValue(true);
        }
    }

    // Inject the linker best neighbor signal
    @Inject(method = "getBestNeighborSignal(Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void ct$injectLinkerBestNeighborSignal(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof Level level)) {
            return;
        }
        int current = cir.getReturnValue();
        if (current >= 15) {
            return;
        }
        int injected = ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(level, pos);
        if (injected > current) {
            cir.setReturnValue(injected);
        }
    }
}
