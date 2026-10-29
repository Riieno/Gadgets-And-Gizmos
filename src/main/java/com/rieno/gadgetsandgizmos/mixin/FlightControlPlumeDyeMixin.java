package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.SmartVectorThrusterBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.client.FlightControlPlumeDyeRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// Recolor the plume buffer once across native overloads, including aperture-controlled full-size thrusters
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.SmartVectorThrusterBlockEntityRenderer", remap = false)
public abstract class FlightControlPlumeDyeMixin{
    @WrapOperation(method = "renderConeFame*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer createthrusters$coneDye(MultiBufferSource buffers, RenderType renderType, Operation<VertexConsumer> original,
                                                 SmartVectorThrusterBlockEntity owner, Direction facing, PoseStack pose,
                                                 MultiBufferSource source, float power, Vector3f direction, Vector3f origin){
        return FlightControlPlumeDyeRenderer.buffers(owner, facing, pose, type -> original.call(buffers, type), direction, origin, true)
                .getBuffer(renderType);
    }

    @WrapOperation(method = "renderExhaustParticles*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer createthrusters$particleDye(MultiBufferSource buffers, RenderType renderType, Operation<VertexConsumer> original,
                                                     SmartVectorThrusterBlockEntity owner, Direction facing, PoseStack pose,
                                                     MultiBufferSource source, float power, Vector3f direction, Vector3f origin){
        return FlightControlPlumeDyeRenderer.buffers(owner, facing, pose, type -> original.call(buffers, type), direction, origin, false)
                .getBuffer(renderType);
    }
}
