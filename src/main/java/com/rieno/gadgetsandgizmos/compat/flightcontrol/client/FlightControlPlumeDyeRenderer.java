package com.rieno.gadgetsandgizmos.compat.flightcontrol.client;

import ace.flight.block.SmartVectorThrusterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectRenderTypes;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectVertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectTarget;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Recolor only Flight Control's exhaust vertices, preserving its nozzle and moving plume geometry
public final class FlightControlPlumeDyeRenderer{
    private FlightControlPlumeDyeRenderer(){}

    public static MultiBufferSource buffers(SmartVectorThrusterBlockEntity owner, Direction facing,
                                           PoseStack pose, MultiBufferSource buffers, Vector3f direction,
                                           Vector3f origin, boolean cone){
        if(!CTConfigs.COMMON.dyeApplicatorFlightControl.get() || !(owner instanceof ColorEffectTarget target)) return buffers;
        var preset = target.colorEffect();
        if(!preset.active() && !preset.additive()) return buffers;
        float[] center = owner.getLocalThrustCenterOffset();
        float[] rotated = SmartVectorThrusterBlockEntity.rotateOffsetByFacing(center, facing);
        float[] offset = cone ? owner.getLocalConeFlameSpawnOffset() : owner.getLocalParticleFlameSpawnOffset();
        var axis = new Vector3f(direction).normalize();
        var start = origin == null ? new Vector3f(.5F + rotated[0], .5F + rotated[1], .5F + rotated[2])
                .fma(offset[2] - center[2], axis) : new Vector3f(origin);
        float length = Math.max(.1F, (owner.isClusterController() ? 10F : 7F) * owner.flameLengthMultiplier);
        double time = owner.getLevel() == null ? 0 : net.createmod.catnip.animation.AnimationTickHolder.getRenderTime(owner.getLevel());
        return type -> {
            var inverse = new Matrix4f(pose.last().pose()).invert();
            var pos = new Vector3f();
            var vertices = buffers.getBuffer(preset.additive() ? ColorEffectRenderTypes.untextured(true) : type);
            return new ColorEffectVertexConsumer(vertices, preset, time, (x, y, z) -> {
                inverse.transformPosition(x, y, z, pos);
                return pos.sub(start).dot(axis) / length;
            }, false);
        };
    }
}
