package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.FlightControlComputerBlockEntity;
import ace.flight.physics.FlightPhysicsController;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests;
import com.rieno.gadgetsandgizmos.lib.physics.SableImpulseCapture;
import com.rieno.gadgetsandgizmos.lib.scm.ScmWrenchSourceRegistry;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

// Let native computers calculate requested forces while SCM applies the sole actuator output
@Pseudo
@Mixin(targets = "ace.flight.block.FlightControlComputerBlockEntity", remap = false)
public abstract class FlightControlComputerRequestMixin{
    @Shadow
    private FlightPhysicsController.BodyStateOverride computeCompositeBodyState(Object body){ throw new AssertionError(); }

    @WrapMethod(method = "sable$physicsTick")
    private void createThrusters$request(ServerSubLevel body, RigidBodyHandle handle, double dt, Operation<Void> original){
        var computer = (FlightControlComputerBlockEntity) (Object) this;
        if(!FlightControlRequests.managed(computer)){ original.call(body, handle, dt); return; }
        boolean dryRun = computer.workDryRun;
        if(!computer.working || computer.mode != 0 || dryRun){
            FlightControlRequests.publish(computer, Vec3.ZERO, Vec3.ZERO, false);
            return;
        }
        try(var scope = FlightControlRequests.suppressActuators()){
            var center = FlightPhysicsController.getMassCenter(body);
            var composite = computeCompositeBodyState(body);
            original.call(body, new SableImpulseCapture(handle, center), dt);
            var force = new org.joml.Vector3d(Double.longBitsToDouble(computer.workTargetFX), Double.longBitsToDouble(computer.workTargetFY), Double.longBitsToDouble(computer.workTargetFZ));
            var compensation = new org.joml.Vector3d();
            boolean active = computer.ctrlActiveCount > 0;
            if(active && computer.considerGravity && Double.isFinite(dt) && dt > 0){
                var environment = ace.flight.physics.SableVelocitySemantics.predictLinearEnvironmentStep(body, handle,
                        composite == null ? FlightPhysicsController.getMass(body) : composite.mass(), dt);
                var impulse = body.logicalPose().orientation().transformInverse(environment.environmentImpulse());
                force = environment.requiredActuatorForce(force, impulse, dt);
                compensation = environment.requiredActuatorForce(new org.joml.Vector3d(), impulse, dt);
            }
            var val = new ScmWrenchSourceRegistry.Wrench(active, new Vec3(force.x, force.y, force.z),
                    new Vec3(Double.longBitsToDouble(computer.workTargetTX), Double.longBitsToDouble(computer.workTargetTY), Double.longBitsToDouble(computer.workTargetTZ)),
                    new Vec3(compensation.x, compensation.y, compensation.z));
            if(composite != null){
                var source = composite.centerOfMass();
                val = val.aboutCenter(new Vec3(source.x, source.y, source.z), new Vec3(center.x, center.y, center.z));
            }
            FlightControlRequests.publish(computer, val.force(), val.torque(), val.compensationForce(), val.active());
        }
    }

    // Navigation samples craft axes while actuator geometry stays in physical body axes
    @WrapMethod(method = "publishNavigationVehicleState")
    private void createThrusters$vehicleFrame(Object body, RigidBodyHandle handle, double dt, long time,
            Operation<Void> original){
        original.call(FlightControlRequests.frame((FlightControlComputerBlockEntity) (Object) this, body), handle, dt, time);
    }
}
