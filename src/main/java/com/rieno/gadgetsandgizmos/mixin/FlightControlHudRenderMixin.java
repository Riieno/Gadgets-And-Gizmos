package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.HudProjectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelProjectionContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Bound HUDs use the live craft projection pass without retaining a second native cache
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.HudProjectorBlockEntityRenderer", remap = false)
public abstract class FlightControlHudRenderMixin{
    @Inject(method = "render(Lace/flight/block/HudProjectorBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void createThrusters$coordinatedRender(HudProjectorBlockEntity hud, float partialTick,
            PoseStack ms, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci){
        if(FlightControlIntegration.componentHost(hud) != null) ci.cancel();
    }

    @Inject(method = "facingYaw", at = @At("HEAD"), cancellable = true)
    private static void createThrusters$craftFacing(BlockState state, CallbackInfoReturnable<Float> cir){
        if(SubLevelProjectionContext.pose() != null) cir.setReturnValue(0F);
    }
}
