package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.navigation.NavigationControllerCommandView;
import ace.flight.navigation.NavigationPortResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests;
import com.rieno.gadgetsandgizmos.lib.physics.SableBodyFrameView;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

// Evaluate native controllers in the configured craft frame and return physical body forces
@Pseudo
@Mixin(targets = {"ace.flight.block.AttitudeHoldBlockEntity", "ace.flight.block.AltitudeHoldBlockEntity",
        "ace.flight.block.VelocityCouplerBlockEntity", "ace.flight.block.AngularVelocityCouplerBlockEntity",
        "ace.flight.block.DistanceCouplerBlockEntity", "ace.flight.block.MouseFlightControllerBlockEntity"}, remap = false)
public abstract class FlightControlWrenchFrameMixin{
    @WrapMethod(method = "contributeWrench")
    private void createThrusters$frame(Object body, RigidBodyHandle handle, double dt, double[] force, double[] torque,
            Operation<Void> original){
        Object view = FlightControlRequests.frame((BlockEntity) (Object) this, body);
        if(!(view instanceof SableBodyFrameView frame)){ original.call(body, handle, dt, force, torque); return; }
        if(FlightControlRequests.inFrame()){ original.call(view, handle, dt, force, torque); return; }
        double[] f = new double[3];
        double[] t = new double[3];
        original.call(view, handle, dt, f, t);
        createThrusters$add(frame, force, f);
        createThrusters$add(frame, torque, t);
    }

    @WrapMethod(method = "contributeNavigationWrench", require = 0)
    private NavigationPortResult createThrusters$navigationFrame(NavigationControllerCommandView command,
            Object body, RigidBodyHandle handle, double dt, double[] force, double[] torque,
            Operation<NavigationPortResult> original){
        if((Object) this instanceof ace.flight.block.MouseFlightControllerBlockEntity mouse
                && command != null && command.effectiveSourceGeneration() == 0){
            var host = FlightControlRequests.host(mouse);
            var runtime = host == null ? null : com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration.runtime(host);
            if(runtime != null && !runtime.mouseRequested(mouse)) return NavigationPortResult.accepted(false);
        }
        Object view = FlightControlRequests.frame((BlockEntity) (Object) this, body);
        if(!(view instanceof SableBodyFrameView frame)) return original.call(command, body, handle, dt, force, torque);
        if(FlightControlRequests.inFrame()) return original.call(command, view, handle, dt, force, torque);
        double[] f = new double[3];
        double[] t = new double[3];
        NavigationPortResult res = original.call(command, view, handle, dt, f, t);
        if(res.accepted()){
            createThrusters$add(frame, force, f);
            createThrusters$add(frame, torque, t);
        }
        return res;
    }

    @Unique
    private static void createThrusters$add(SableBodyFrameView frame, double[] target, double[] val){
        Vec3 physical = frame.frame().toBody(new Vec3(val[0], val[1], val[2]));
        target[0] += physical.x;
        target[1] += physical.y;
        target[2] += physical.z;
    }
}
