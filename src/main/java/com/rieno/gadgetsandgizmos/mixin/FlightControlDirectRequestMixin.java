package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests;
import com.rieno.gadgetsandgizmos.lib.physics.SableImpulseCapture;
import com.rieno.gadgetsandgizmos.lib.physics.SableBodyFrameHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Capture native direct control as a request instead of applying a second impulse
@Pseudo
@Mixin(targets = {"ace.flight.block.CreativeFlightControlComputerBlockEntity", "ace.flight.block.AttitudeHoldBlockEntity",
        "ace.flight.block.AltitudeHoldBlockEntity", "ace.flight.block.VelocityCouplerBlockEntity",
        "ace.flight.block.AngularVelocityCouplerBlockEntity", "ace.flight.block.DistanceCouplerBlockEntity",
        "ace.flight.block.MouseFlightControllerBlockEntity"}, remap = false)
public abstract class FlightControlDirectRequestMixin{
    @WrapMethod(method = "sable$physicsTick", require = 0)
    private void createThrusters$request(ServerSubLevel body, RigidBodyHandle handle, double dt, Operation<Void> original){
        BlockEntity component = (BlockEntity) (Object) this;
        var host = FlightControlRequests.host(component);
        if(host == null){ original.call(body, handle, dt); return; }
        var center = ace.flight.physics.FlightPhysicsController.getMassCenter(body);
        var capture = new SableImpulseCapture(handle, center);
        var frameHandle = new SableBodyFrameHandle(FlightControlRequests.managed(component) ? capture : handle,
                com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration.orientation(host));
        Vec3 compensation;
        try(var scope = FlightControlRequests.enter(component, frameHandle)){
            original.call(body, frameHandle, dt);
            compensation = com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration.orientation(host)
                    .toBody(FlightControlRequests.compensation());
        }
        if(!FlightControlRequests.managed(component) || !Double.isFinite(dt) || dt <= 0) return;
        var val = capture.snapshot(dt);
        FlightControlRequests.publish(component, val.force(), val.torque(), compensation,
                val.force().lengthSqr() > 1.0E-12D || val.torque().lengthSqr() > 1.0E-12D);
    }
}
