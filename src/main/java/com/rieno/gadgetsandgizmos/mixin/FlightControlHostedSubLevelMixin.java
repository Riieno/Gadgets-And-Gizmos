package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Resolve detached native controllers against the ACC's real Sable membership
@Pseudo
@Mixin(targets = "ace.flight.physics.AttitudeTargetUtil", remap = false)
public abstract class FlightControlHostedSubLevelMixin{
    @Inject(method = "getContainingSubLevel(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true)
    private static void createThrusters$hostedSubLevel(Level level, BlockPos pos, BlockEntity component,
            CallbackInfoReturnable<Object> cir){
        BlockEntity host = com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration.componentHost(component);
        if(host != null) cir.setReturnValue(com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests.frame(component, SableLevelApi.containing(host)));
    }
}
