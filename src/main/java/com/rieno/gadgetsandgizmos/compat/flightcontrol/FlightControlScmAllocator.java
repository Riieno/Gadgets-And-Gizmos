package com.rieno.gadgetsandgizmos.compat.flightcontrol;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import ace.flight.block.SmartVectorThrusterBlockEntity;
import ace.flight.physics.AllocationResult;
import ace.flight.physics.ThrusterAllocation;
import com.rieno.gadgetsandgizmos.lib.control.VectorThrustReceiver;
import com.rieno.gadgetsandgizmos.lib.scm.ScmVectorAllocationRegistry;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Use Flight Control's production wrench-priority solver for SCM vector engines
public final class FlightControlScmAllocator implements ScmVectorAllocationRegistry.Allocator{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Select fleets containing native vector engines and representable force actuators
    @Override
    public boolean supports(ScmVectorAllocationRegistry.Request request){
        return supportsBatch(List.of(request));
    }

    @Override
    public boolean supportsBatch(List<ScmVectorAllocationRegistry.Request> requests){
        boolean found = false;
        Set<Object> engines = new HashSet<>();
        for(var request : requests){
            for(var unit : request.units()){
                if(unit.fullForce().lengthSqr() < 1.0E-12D && unit.fullTorque().lengthSqr() > 1.0E-12D) return false;
                if(unit.blockEntity() instanceof SmartVectorThrusterBlockEntity engine){
                    if(unit.fullForce().lengthSqr() < 1.0E-12D) continue;
                    if(!(engine instanceof VectorThrustReceiver) || !engines.add(engine)) return false;
                    if((engine.getLinkedComputerPos() != null || engine.getLinkedSwivelMountPos() != null)
                            && (request.host() == null || FlightControlIntegration.engineHost(engine) != request.host())) return false;
                    found = true;
                }
            }
        }
        return found;
    }

    // Preserve native cone, capacity, nozzle, fuel and multi-block engine constraints
    @Override
    public ScmVectorAllocationRegistry.Result allocate(ScmVectorAllocationRegistry.Request request){
        int count = request.units().size();
        double[][] arms = new double[count][3];
        double[][] directions = new double[count][3];
        double[] maximums = new double[count];
        double[] cones = new double[count];
        for(int idx = 0; idx < count; idx++){
            var unit = request.units().get(idx);
            arms[idx] = vector(unit.momentArm());
            directions[idx] = vector(unit.fullForce().normalize());
            maximums[idx] = unit.fullForce().length();
            if(unit.blockEntity() instanceof SmartVectorThrusterBlockEntity engine){
                cones[idx] = engine.halfAngleDeg;
                maximums[idx] = engine.active && engine.hasNozzle && !engine.isClusterMember()
                        && engine.hasPropellantForThrust() && maximums[idx] > 0
                        ? Math.min(maximums[idx], Math.max(0, engine.getEffectiveMaxThrustForComputer())) : 0;
            }
        }
        AllocationResult res = ThrusterAllocation.solveWrenchPriority(arms, directions, maximums, cones,
                vector(request.force()), vector(request.torque()),
                ThrusterAllocation.DEFAULT_LAMBDA, ThrusterAllocation.DEFAULT_FUEL_SPARSITY);
        if(request.prioritizeTorque()){
            res = ace.flight.physics.TorquePriorityAllocation.solve(java.util.Collections.nCopies(count, null), arms, directions, maximums, cones,
                    vector(request.force()), vector(request.torque()), res, ThrusterAllocation.MAX_ADMM_ITERATIONS);
        }
        List<Vec3> forces = new ArrayList<>(count);
        for(double[] force : res.thrusterForces()) forces.add(new Vec3(force[0], force[1], force[2]));
        double residual = fromVector(res.actualForce()).subtract(request.force()).lengthSqr()
                + fromVector(res.actualTorque()).subtract(request.torque()).lengthSqr();
        return new ScmVectorAllocationRegistry.Result(forces, Math.sqrt(residual));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static double[] vector(Vec3 val){ return new double[]{val.x, val.y, val.z}; }
    private static Vec3 fromVector(double[] val){ return new Vec3(val[0], val[1], val[2]); }
}
