package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.physics.FlightPhysicsController;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests;
import com.rieno.gadgetsandgizmos.lib.physics.SableBodyFrameView;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Matrix3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Resolve native reflection against the library's craft frame view
@Pseudo
@Mixin(targets = "ace.flight.physics.FlightPhysicsController", remap = false)
public abstract class FlightControlFramePhysicsMixin{
    @Inject(method = "getBodyStateOverride", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$composite(Object body,
            CallbackInfoReturnable<FlightPhysicsController.BodyStateOverride> cir){
        if(!(body instanceof SableBodyFrameView frame)) return;
        var val = FlightPhysicsController.getBodyStateOverride(frame.body());
        if(val != null){
            cir.setReturnValue(new FlightPhysicsController.BodyStateOverride(frame, val.mass(), val.centerOfMass(),
                    frame.frame().fromBodyTensor(val.inertiaTensor())));
        }
    }

    @Inject(method = "getHandle", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$handle(Object body, CallbackInfoReturnable<RigidBodyHandle> cir){
        var handle = FlightControlRequests.handle();
        if(handle != null){ cir.setReturnValue(handle); return; }
        if(body instanceof SableBodyFrameView frame && frame.body() instanceof ServerSubLevel server){
            cir.setReturnValue(RigidBodyHandle.of(server));
        }
    }

    @Inject(method = "getInertiaTensor", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$inertia(Object body, CallbackInfoReturnable<Matrix3d> cir){
        Object view = FlightControlRequests.frame(body);
        if(view != body && view instanceof SableBodyFrameView frame){
            var val = FlightPhysicsController.getBodyStateOverride(frame);
            cir.setReturnValue(val == null ? new Matrix3d(frame.getMassTracker().getInertiaTensor()) : val.inertiaTensor());
        }
    }
}
