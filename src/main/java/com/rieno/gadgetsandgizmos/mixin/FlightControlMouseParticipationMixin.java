package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.MouseFlightControllerBlockEntity;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// An idle HUD mouse terminal must not request attitude stabilization
@Pseudo
@Mixin(targets = "ace.flight.block.AttitudeHoldBlockEntity", remap = false)
public abstract class FlightControlMouseParticipationMixin{
    @Inject(method = "isContributing", at = @At("HEAD"), cancellable = true)
    private void createThrusters$mouseParticipation(CallbackInfoReturnable<Boolean> cir){
        if(!((Object) this instanceof MouseFlightControllerBlockEntity mouse) || mouse.getLevel() == null || mouse.getLevel().isClientSide) return;
        if(FlightControlIntegration.componentHost(mouse) instanceof AdvancedContraptionControllerBlockEntity host){
            var runtime = FlightControlIntegration.runtime(host);
            if(runtime == null || !runtime.mouseRequested(mouse)) cir.setReturnValue(false);
        }
    }
}
