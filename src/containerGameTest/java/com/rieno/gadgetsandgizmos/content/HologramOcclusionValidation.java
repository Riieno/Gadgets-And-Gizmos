package com.rieno.gadgetsandgizmos.content;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramProjectionRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneEnvironment;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.util.List;

// Verify real hologram fragments against scene depth without relying on delayed visibility probes
final class HologramOcclusionValidation{
    static void run(){
        Runnable restore = ViewSceneEnvironment.save();
        var prev = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        var target = new TextureTarget(64, 64, true, Minecraft.ON_OSX);
        view.pushMatrix();
        try(var renderer = HologramProjectionRenderer.baseModels()){
            view.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().ortho(-.25F, 1.25F, -.25F, 1.25F, -10, 10),
                    VertexSorting.ORTHOGRAPHIC_Z);
            var chunk = new HologramChunk(new ChunkPos(0, 0), 0x88AA55, 0x77AA44, 0x3355DD,
                    List.of(new HologramChunk.SurfaceBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 63)));
            for(var quality : List.of(HologramResolution.FULL, HologramResolution.LOW)){
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                RenderSystem.disableCull();
                wall(0, .5F, 2, 255, 0, 0);
                renderer.render(new PoseStack(), null, Vec3.ZERO, List.of(chunk), 0, quality);
                var pixels = pixels();
                int left = (32 * 64 + 20) * 4, right = (32 * 64 + 44) * 4;
                require(Byte.toUnsignedInt(pixels.get(left)) == 255 && pixels.get(left + 1) == 0 && pixels.get(left + 2) == 0,
                        quality + " hologram fragments appeared through a solid wall");
                require(Byte.toUnsignedInt(pixels.get(right + 3)) > 0,
                        quality + " hologram disappeared from the clear half of the view");
                require(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "Hologram rendering disabled the world's depth test");
                require(GL11.glGetInteger(GL11.GL_DEPTH_FUNC) == GL11.GL_LEQUAL
                                && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Hologram rendering changed the world's depth contract");
                wall(0, 1, -2, 0, 0, 255);
                pixels = pixels();
                require(pixels.get(left + 2) == 0 && Byte.toUnsignedInt(pixels.get(left)) == 255,
                        "Following world geometry ignored the occluding wall");
                RenderSystem.disableDepthTest();
                renderer.render(new PoseStack(), null, Vec3.ZERO, List.of(chunk), 0, quality);
                require(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "Hologram rendering did not restore disabled caller depth testing");
            }
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Hologram occlusion left an OpenGL error");
        }finally{
            target.destroyBuffers();
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(prev, sorting);
            restore.run();
        }
    }

    private static java.nio.ByteBuffer pixels(){
        var pixels = BufferUtils.createByteBuffer(64 * 64 * 4);
        GL11.glReadPixels(0, 0, 64, 64, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void wall(float minX, float maxX, float z, int red, int green, int blue){
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try(var bytes = new ByteBufferBuilder(256)){
            var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            builder.addVertex(minX, 0, z).setColor(red, green, blue, 255);
            builder.addVertex(maxX, 0, z).setColor(red, green, blue, 255);
            builder.addVertex(maxX, 1, z).setColor(red, green, blue, 255);
            builder.addVertex(minX, 1, z).setColor(red, green, blue, 255);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
}
