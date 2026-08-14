package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.lang.reflect.Method;

// Draw the Claw Depth Highlight
public final class ClawDepthHighlightRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared callback hits
    private static int callbackHits;
    // Shared draw attempts
    private static int drawAttempts;
    // Shared probe draws
    private static int probeDraws;
    // Last marker count
    private static int lastMarkerCount;
    // Last status
    private static String lastStatus = "idle";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw depth highlight
    private ClawDepthHighlightRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the render world event
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        callbackHits++;

        ClawMarkerRenderState.clearAll();
        lastMarkerCount = 0;
        lastStatus = "disabled";
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the debug fullscreen probe
    private static void drawDebugFullscreenProbe(Minecraft minecraft) {
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (shader == null) {
            return;
        }

        Matrix4f identity = new Matrix4f();
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, identity, identity, minecraft.getWindow());
        RenderSystem.setShader(() -> shader);
        shader.apply();
        probeDraws++;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(-1.0f, -1.0f, 0.0f).setColor(1.0f, 0.0f, 1.0f, 0.18f);
        buffer.addVertex(1.0f, -1.0f, 0.0f).setColor(1.0f, 0.0f, 1.0f, 0.18f);
        buffer.addVertex(1.0f, 1.0f, 0.0f).setColor(1.0f, 0.0f, 1.0f, 0.18f);
        buffer.addVertex(-1.0f, 1.0f, 0.0f).setColor(1.0f, 0.0f, 1.0f, 0.18f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        shader.clear();
    }

    // Get the callback hits
    public static int getCallbackHits() {
        return callbackHits;
    }

    // Get the draw attempts
    public static int getDrawAttempts() {
        return drawAttempts;
    }

    // Get the probe draws
    public static int getProbeDraws() {
        return probeDraws;
    }

    // Get the last marker count
    public static int getLastMarkerCount() {
        return lastMarkerCount;
    }

    // Get the last status
    public static String getLastStatus() {
        return lastStatus;
    }

    // Draw the fullscreen quad
    private static void drawFullscreenQuad() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0f, -1.0f, 0.0f);
        buffer.addVertex(1.0f, -1.0f, 0.0f);
        buffer.addVertex(1.0f, 1.0f, 0.0f);
        buffer.addVertex(-1.0f, 1.0f, 0.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    // Set a two-value shader uniform
    private static void setFloat2(ShaderInstance shader, String name, float x, float y) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y);
        }
    }

    // Set a four-value shader uniform
    private static void setFloat4(ShaderInstance shader, String name, float x, float y, float z, float w) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z, w);
        }
    }

    // Set a matrix shader uniform
    private static void setMatrix4(ShaderInstance shader, String name, Matrix4f matrix) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(matrix);
        }
    }

    // Get the event matrix
    private static Matrix4f getEventMatrix(RenderLevelStageEvent evt, String getterName, Matrix4f fallback) {
        try {
            Method method = evt.getClass().getMethod(getterName);
            Object res = method.invoke(evt);
            if (res instanceof Matrix4f matrix) {
                return new Matrix4f(matrix);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return new Matrix4f(fallback);
    }

    // Get the target width
    private static float getTargetWidth(RenderTarget target, Minecraft minecraft) {
        try {
            Method method = target.getClass().getMethod("getWidth");
            Object val = method.invoke(target);
            if (val instanceof Integer i && i > 0) {
                return i;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            java.lang.reflect.Field field = target.getClass().getField("width");
            Object val = field.get(target);
            if (val instanceof Integer i && i > 0) {
                return i;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return (float) minecraft.getWindow().getWidth();
    }

    // Get the target height
    private static float getTargetHeight(RenderTarget target, Minecraft minecraft) {
        try {
            Method method = target.getClass().getMethod("getHeight");
            Object val = method.invoke(target);
            if (val instanceof Integer i && i > 0) {
                return i;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            java.lang.reflect.Field field = target.getClass().getField("height");
            Object val = field.get(target);
            if (val instanceof Integer i && i > 0) {
                return i;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return (float) minecraft.getWindow().getHeight();
    }
}
