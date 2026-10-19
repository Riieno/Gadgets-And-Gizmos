package com.rieno.gadgetsandgizmos.compat.flightcontrol.client;

import ace.flight.block.AttitudeDisplayBlockEntity;
import ace.flight.neoforge.client.AttitudeDisplaySubLevelWorldRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaterniondc;

import java.lang.reflect.Method;
import java.util.List;

// Keep native miniature rendering while anchoring its pivot in the configured craft frame
final class FlightControlAttitudeProjection{
    private static final Method CONNECTED;
    private static final Method TRANSFORM;
    private static final Method RENDER;

    static{
        try{
            // Flight Control 0.7.7 keeps the miniature transform private; cache its native entry points.
            Class<?> transform = Class.forName("ace.flight.neoforge.client.AttitudeDisplaySubLevelWorldRenderer$HologramTransform");
            CONNECTED = AttitudeDisplaySubLevelWorldRenderer.class.getDeclaredMethod("resolveConnectedSubLevels", ClientSubLevel.class);
            TRANSFORM = transform.getDeclaredMethod("create", List.class, double.class);
            RENDER = AttitudeDisplaySubLevelWorldRenderer.class.getDeclaredMethod("renderMiniatureStructure",
                    List.class, transform, float.class, Matrix4f.class, MultiBufferSource.BufferSource.class, Matrix4f.class, double.class);
            CONNECTED.setAccessible(true);
            TRANSFORM.setAccessible(true);
            RENDER.setAccessible(true);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException("Unavailable native attitude projection", err); }
    }

    private FlightControlAttitudeProjection(){}

    // Preserve plot geometry while moving the projection height along the craft's up vector
    static void render(AttitudeDisplayBlockEntity display, ClientSubLevel body, float partialTick,
            Matrix4f matrix, Quaterniondc frame, MultiBufferSource.BufferSource buffers, Matrix4f projection){
        double height = AttitudeDisplayBlockEntity.normalizeDisplayHeight(display.displayHeight);
        Matrix4f model = new Matrix4f(matrix).translate(.5F, (float) height, .5F)
                .rotate(new Quaternionf(frame).conjugate()).translate(-.5F, (float) -height, -.5F);
        try{
            Object levels = CONNECTED.invoke(null, body);
            Object transform = TRANSFORM.invoke(null, levels, AttitudeDisplayBlockEntity.normalizeDisplayScale(display.displayScale));
            if(transform != null) RENDER.invoke(null, levels, transform, partialTick, model, buffers, projection, height);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException("Native attitude projection failed", err); }
    }
}
