package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests;
import com.rieno.gadgetsandgizmos.lib.physics.SableBodyFrameView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep native direct control sampling consistent with the configured craft axes
@Pseudo
@Mixin(targets = "ace.flight.physics.AttitudeTargetUtil", remap = false)
public abstract class FlightControlScopedPoseMixin{
    @Inject(method = "getLogicalPose", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$pose(Object body, CallbackInfoReturnable<Object> cir){
        Object view = FlightControlRequests.frame(body);
        if(view != body && view instanceof SableBodyFrameView frame) cir.setReturnValue(frame.logicalPose());
    }
}
