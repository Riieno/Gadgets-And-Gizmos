package com.rieno.gadgetsandgizmos.compat.simulated;

// Expose the Physics Assembler's in-progress disassembly state to addon compatibility code
public interface PhysicsAssemblerAssemblyState {
    // Check whether the assembler is already disassembling
    boolean createthrusters$isAssemblyTransitioning();
}
