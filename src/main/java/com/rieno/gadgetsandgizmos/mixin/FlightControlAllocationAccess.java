package com.rieno.gadgetsandgizmos.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

// Clamp external force commands with Flight Control's native cone projection
@Pseudo
@Mixin(targets = "ace.flight.physics.ThrusterAllocation", remap = false)
public interface FlightControlAllocationAccess{
    @Invoker("projectToFeasible")
    static void createThrusters$project(double[] force, double[][] axes, double[] limits,
            double[] cones, int count, double sparsity){
        throw new IllegalStateException("Native allocation accessor was not applied");
    }
}
