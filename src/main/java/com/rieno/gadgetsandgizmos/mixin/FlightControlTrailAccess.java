package com.rieno.gadgetsandgizmos.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

// Use the trail module's native normalization and synchronization setters
@Pseudo
@Mixin(targets = "ace.flight.block.AerodynamicTrailCreatorBlockEntity", remap = false)
public interface FlightControlTrailAccess{
    @Invoker("setModuleColor") void createThrusters$moduleColor(int color);
    @Invoker("setLengthTenths") void createThrusters$length(int length);
    @Invoker("setThicknessTenths") void createThrusters$thickness(int thickness);
    @Invoker("setMinSpeedTenths") void createThrusters$minSpeed(int speed);
    @Invoker("setDensityPercent") void createThrusters$density(int density);
    @Invoker("setDefaultEnabled") void createThrusters$enabled(boolean enabled);
    @Invoker("setTrailColor") void createThrusters$color(int red, int green, int blue, int alpha);
}
