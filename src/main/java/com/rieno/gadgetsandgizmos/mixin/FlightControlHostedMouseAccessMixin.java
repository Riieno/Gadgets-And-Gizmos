package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.MouseFlightControllerBlockEntity;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Let the native mouse lease authenticate an ACC interaction instead of a Create seat
@Pseudo
@Mixin(targets = "ace.flight.neoforge.MouseFlightSeatAccess", remap = false)
public abstract class FlightControlHostedMouseAccessMixin{
    @Inject(method = "isSeatedPlayer", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$accInteraction(MouseFlightControllerBlockEntity component, Player player,
            CallbackInfoReturnable<Boolean> cir){
        if(com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration.componentHost(component) instanceof AdvancedContraptionControllerBlockEntity host){
            cir.setReturnValue(FlightControlIntegration.isInteractionAuthorized(host, player));
        }
    }
}
