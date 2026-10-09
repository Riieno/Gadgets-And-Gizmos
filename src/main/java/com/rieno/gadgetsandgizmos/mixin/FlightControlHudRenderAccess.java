package com.rieno.gadgetsandgizmos.mixin;

import ace.flight.block.HudProjectorBlockEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

// Keep native HUD layout and telemetry while supplying the hosted projector's Sable transform
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.HudProjectorBlockEntityRenderer", remap = false)
public interface FlightControlHudRenderAccess{
    @Invoker("projectorMatrix")
    static Matrix4f createThrusters$projectorMatrix(Matrix4f matrix, HudProjectorBlockEntity hud){ throw new AssertionError(); }

    @Invoker("renderHud")
    static void createThrusters$renderHud(Matrix4f matrix, VertexConsumer buffer, boolean hasData,
                                         double pitch, double yaw, double roll, double speed){ throw new AssertionError(); }
}
