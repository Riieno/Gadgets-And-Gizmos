package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.client.PropulsionPlumeDyeRenderer;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.ThrusterPlumeSpec;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Retain native adaptive trail sampling while tinting its rendered vertices
@Pseudo
@Mixin(targets = "dev.propulsionteam.propulsionsimulated.client.render.plume.AdaptivePlumeTrailRenderer", remap = false)
public abstract class PropulsionPlumeTrailDyeMixin{
    @WrapMethod(method = "render")
    private static void createthrusters$trailDye(AbstractThrusterBlockEntity owner, float partialTick, PoseStack pose,
                                               MultiBufferSource buffers, ThrusterPlumeSpec spec, Operation<Void> original){
        original.call(owner, partialTick, pose, PropulsionPlumeDyeRenderer.buffers(owner, buffers, partialTick), spec);
    }
}
