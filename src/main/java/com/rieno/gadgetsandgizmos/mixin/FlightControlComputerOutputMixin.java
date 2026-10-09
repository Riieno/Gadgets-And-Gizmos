package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.api.IFCCThruster;
import ace.flight.api.IFCCFlapBearing;
import ace.flight.api.IFCCSwivelMount;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Native request evaluation must leave external actuators and swivel constraints under SCM ownership
@Pseudo
@Mixin(targets = "ace.flight.block.FlightControlComputerBlockEntity", remap = false)
public abstract class FlightControlComputerOutputMixin{
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lace/flight/api/IFCCThruster;setFCCAllocatedForce([D)V"), require = 0)
    private void createThrusters$force(IFCCThruster target, double[] force){
        if(!FlightControlRequests.computing()) target.setFCCAllocatedForce(force);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lace/flight/api/IFCCThruster;setFCCControlActive(Z)V"), require = 0)
    private void createThrusters$active(IFCCThruster target, boolean active){
        if(!FlightControlRequests.computing()) target.setFCCControlActive(active);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lace/flight/api/IFCCThruster;setFCCThrottle(F)V"), require = 0)
    private void createThrusters$throttle(IFCCThruster target, float val){
        if(!FlightControlRequests.computing()) target.setFCCThrottle(val);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lace/flight/api/IFCCFlapBearing;setFCCFlapTargetAngleDeg(D)V"), require = 0)
    private void createThrusters$flap(IFCCFlapBearing target, double angle){
        if(!FlightControlRequests.computing()) target.setFCCFlapTargetAngleDeg(angle);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lace/flight/api/IFCCFlapBearing;setFCCFlapControlActive(Z)V"), require = 0)
    private void createThrusters$flapActive(IFCCFlapBearing target, boolean active){
        if(!FlightControlRequests.computing()) target.setFCCFlapControlActive(active);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lace/flight/api/IFCCSwivelMount;enforceFCCKinematicLock(Ljava/lang/Object;D)V"), require = 0)
    private void createThrusters$mount(IFCCSwivelMount target, Object body, double dt){
        if(!FlightControlRequests.computing()) target.enforceFCCKinematicLock(body, dt);
    }
}
