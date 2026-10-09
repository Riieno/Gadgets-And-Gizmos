package com.rieno.gadgetsandgizmos.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

// Preserve the native target session when graph settings change mouse view lock
@Pseudo
@Mixin(targets = "ace.flight.block.MouseFlightControllerBlockEntity", remap = false)
public interface FlightControlMouseAccess{
    @Invoker("setMouseViewLockEnabled") void createThrusters$viewLock(boolean enabled);
}
