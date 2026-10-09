package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.MouseFlightControllerBlockEntity;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.client.FlightControlClient;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Select the ACC's hosted mouse controller before the native seated-controller search
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.MouseFlightControllerClient", remap = false)
public abstract class FlightControlHostedMouseClientMixin{
    @Shadow private static MouseFlightControllerBlockEntity active;

    // Scale only player mouse deltas, leaving Sable camera following and target rebasing intact
    @ModifyVariable(method = {"cameraAngles", "trackWorldSpaceUnlockedCamera", "trackFixedShoulderCamera"},
            at = @At("STORE"), name = "mouseYawDelta")
    private static float createThrusters$mouseYawSensitivity(float val){ return createThrusters$scaleMouse(val); }

    @ModifyVariable(method = {"cameraAngles", "trackWorldSpaceUnlockedCamera", "trackFixedShoulderCamera"},
            at = @At("STORE"), name = "mousePitchDelta")
    private static float createThrusters$mousePitchSensitivity(float val){ return createThrusters$scaleMouse(val); }

    @org.spongepowered.asm.mixin.Unique
    private static float createThrusters$scaleMouse(float val){
        return active != null && FlightControlIntegration.componentHost(active) != null
                ? (float) (val * Math.clamp(active.mouseSensitivity / 0.12D, 0.1D, 8.0D)) : val;
    }

    @Inject(method = "selectController", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$selectAcc(Minecraft minecraft,
            CallbackInfoReturnable<MouseFlightControllerBlockEntity> cir){
        MouseFlightControllerBlockEntity component = FlightControlClient.activeMouse();
        if(component != null) cir.setReturnValue(component);
    }

    // Anchor native aiming at the ACC while retaining detached identities for packets and links
    @Redirect(method = "resolveAircraftFrame", at = @At(value = "INVOKE", target =
            "Lace/flight/block/MouseFlightControllerBlockEntity;getBlockPos()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos createThrusters$aimOrigin(MouseFlightControllerBlockEntity component){
        var host = FlightControlIntegration.componentHost(component);
        return host == null ? component.getBlockPos() : host.getBlockPos();
    }
}
