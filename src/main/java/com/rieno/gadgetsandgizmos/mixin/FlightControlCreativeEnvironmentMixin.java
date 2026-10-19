package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Track native environmental support separately so SCM lift compensation is applied once
@Pseudo
@Mixin(targets = "ace.flight.block.CreativeFlightControlComputerBlockEntity", remap = false)
public abstract class FlightControlCreativeEnvironmentMixin{
    @WrapMethod(method = "compensateLinearEnvironment")
    private void createThrusters$compensation(Object body, RigidBodyHandle handle, double dt,
            Vector3d force, Vector3d torque, Operation<Void> original){
        Vector3d prev = new Vector3d(force);
        original.call(body, handle, dt, force, torque);
        FlightControlRequests.recordCompensation(new Vec3(force.x - prev.x, force.y - prev.y, force.z - prev.z));
    }
}
