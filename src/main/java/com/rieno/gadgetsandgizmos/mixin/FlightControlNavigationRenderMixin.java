package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.NavigationTerminalBlockEntity;
import ace.flight.navigation.NavigationTerminalVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelProjectionContext;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Compute the native navigation pointer from the ACC anchor and configured craft axes
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.NavigationTerminalBlockEntityRenderer", remap = false)
public abstract class FlightControlNavigationRenderMixin{
    @Inject(method = "renderSafe(Lace/flight/block/NavigationTerminalBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void createThrusters$coordinatedRender(NavigationTerminalBlockEntity terminal, float partialTick,
            PoseStack ms, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci){
        if(FlightControlIntegration.componentHost(terminal) != null && SubLevelProjectionContext.pose(terminal) == null) ci.cancel();
    }

    @Inject(method = "pointerRotation", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$hostedPointer(NavigationTerminalBlockEntity terminal, CallbackInfoReturnable<Float> cir){
        var pose = SubLevelProjectionContext.pose(terminal);
        if(pose == null) return;
        Vector3d target = terminal.getVisualTargetWorld();
        if(target == null){
            cir.setReturnValue(0F);
            return;
        }
        var anchor = terminal.getBlockPos().getCenter();
        Vector3d bearing = pose.transformPositionInverse(target, new Vector3d()).sub(anchor.x, anchor.y, anchor.z);
        cir.setReturnValue((float) NavigationTerminalVisuals.pointerRotation(bearing.x, bearing.z));
    }
}
