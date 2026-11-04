package com.rieno.gadgetsandgizmos.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.client.PropulsionPlumeDyeRenderer;
import dev.propulsionteam.propulsionsimulated.client.render.plume.ThrusterVisualEffects;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Pass a separate effect buffer to the native shader plume renderer
@Pseudo
@Mixin(targets = "dev.propulsionteam.propulsionsimulated.client.render.plume.ThrusterVisualEffects", remap = false)
public abstract class PropulsionPlumeDyeMixin{
    @WrapMethod(method = "renderInternal")
    private static void createthrusters$plumeDye(AbstractThrusterBlockEntity owner, float partialTick, PoseStack pose,
                                               MultiBufferSource buffers, ThrusterVisualEffects.Preset preset,
                                               float intensity, Operation<Void> original){
        original.call(owner, partialTick, pose, PropulsionPlumeDyeRenderer.buffers(owner, buffers, partialTick), preset, intensity);
    }
}
