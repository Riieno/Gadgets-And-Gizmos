package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.AttitudeDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelProjectionContext;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Hosted miniatures are drawn from live Sable poses instead of the native block visibility cache
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.AttitudeDisplayBlockEntityRenderer", remap = false)
public abstract class FlightControlAttitudeRenderMixin{
    @Inject(method = "cacheSubLevel", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$hostedCache(AttitudeDisplayBlockEntity display, PoseStack ms, CallbackInfo ci){
        if(FlightControlIntegration.componentHost(display) != null) ci.cancel();
    }

    @Inject(method = "render(Lace/flight/block/AttitudeDisplayBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void createThrusters$hostedRender(AttitudeDisplayBlockEntity display, float partialTick,
            PoseStack ms, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci){
        if(FlightControlIntegration.componentHost(display) != null && SubLevelProjectionContext.pose(display) == null) ci.cancel();
    }

    @Inject(method = "resolveOrientation", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$renderOrientation(AttitudeDisplayBlockEntity display, CallbackInfoReturnable<Quaterniond> cir){
        var pose = SubLevelProjectionContext.pose(display);
        if(pose != null) cir.setReturnValue(new Quaterniond(pose.orientation()));
    }
}
