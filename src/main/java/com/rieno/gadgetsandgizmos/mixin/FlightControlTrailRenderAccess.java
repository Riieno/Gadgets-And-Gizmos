package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.AerodynamicTrailCreatorBlockEntity;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

// Sample detached trail emitters through the installed mod's native trail lifecycle
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.sonic.SonicBoomClientEffects", remap = false)
public interface FlightControlTrailRenderAccess{
    @Invoker("sampleTrailCreator")
    static void createThrusters$sampleTrail(ClientSubLevel body, Pose3dc pose, AerodynamicTrailCreatorBlockEntity trail){
        throw new AssertionError();
    }
}
