package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.AerodynamicTrailCreatorBlockEntity;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelProjectionContext;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sample bound emitters once in their craft frame instead of mixing two emitter positions
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.sonic.SonicBoomClientEffects", remap = false)
public abstract class FlightControlTrailSamplingMixin{
    @Inject(method = "sampleTrailCreator", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$coordinatedSample(ClientSubLevel body, Pose3dc pose,
            AerodynamicTrailCreatorBlockEntity trail, CallbackInfo ci){
        if(FlightControlIntegration.componentHost(trail) != null && SubLevelProjectionContext.pose(trail) == null) ci.cancel();
    }
}
