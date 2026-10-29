package com.rieno.gadgetsandgizmos.content;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramProjectionRenderer;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Exercise real textured and averaged GPU meshes while switching a live projection's quality
final class HologramResolutionValidation{
    static void run(){
        var cells = new ArrayList<HologramChunk.SurfaceBlock>();
        for(int x = 0; x < 16; x++) for(int z = 0; z < 16; z++) cells.add(new HologramChunk.SurfaceBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState(), 2));
        var wall = new HologramChunk(new ChunkPos(0, 0), 0x88AA55, 0x77AA44, 0x3355DD, cells);
        try(var renderer = HologramProjectionRenderer.staticModels(null)){
            draw(renderer, wall, HologramResolution.FULL, 0);
            var full = (VertexBuffer)get(mesh(renderer), "buffer");
            int original = (int)get(full, "indexCount");
            require(original == 256 * 6, "Full quality changed the original block faces");
            draw(renderer, wall, HologramResolution.LOW, 0);
            var coarse = mesh(renderer);
            var colors = (VertexBuffer)get(coarse, "buffer");
            require(full.isInvalid() && colors != null && (int)get(colors, "indexCount") == 6 && (int)get(coarse, "colorQuads") == 1,
                    "Low quality did not merge the flat surface or release the old mesh");
            checkPixels(colors);
            for(int idx = 0; idx < 20; idx++) draw(renderer, wall, HologramResolution.LOW, 0);
            require(get(mesh(renderer), "buffer") == colors, "Stable LOD rebuilt its GPU mesh");
            draw(renderer, wall, HologramResolution.FULL, 0);
            require(colors.isInvalid() && (int)get(get(mesh(renderer), "buffer"), "indexCount") == original,
                    "Changing back to Full did not restore the original geometry");
            var mixed = new ArrayList<>(cells);
            mixed.add(new HologramChunk.SurfaceBlock(new BlockPos(4, 2, 4), Blocks.GLASS.defaultBlockState(), 63));
            mixed.add(new HologramChunk.SurfaceBlock(new BlockPos(5, 2, 4), Blocks.IRON_BARS.defaultBlockState(), 63));
            mixed.add(new HologramChunk.SurfaceBlock(new BlockPos(6, 2, 4), Blocks.STONE_SLAB.defaultBlockState(), 63));
            var detail = new HologramChunk(wall.pos(), wall.grassColor(), wall.foliageColor(), wall.waterColor(), mixed);
            draw(renderer, detail, HologramResolution.LOW, 0);
            var fine = (VertexBuffer)get(mesh(renderer), "buffer");
            int averaged = (int)get(mesh(renderer), "colorQuads");
            require(fine != null && averaged > 0 && (int)get(fine, "indexCount") > averaged * 6,
                    "Glass or thin models were replaced by opaque LOD cells");
            HologramProjectionRenderer.clearColorCache();
            draw(renderer, detail, HologramResolution.LOW, 1);
            require(fine.isInvalid(), "Resource generation retained old LOD buffers");
            fine = (VertexBuffer)get(mesh(renderer), "buffer");
            renderer.close();
            require(fine.isInvalid(), "LOD cleanup leaked GPU buffers");
        }
        checkGrass();
    }

    private static void checkGrass(){
        var cells = new ArrayList<HologramChunk.SurfaceBlock>();
        for(int x = 0; x < 16; x++) for(int z = 0; z < 16; z++) cells.add(new HologramChunk.SurfaceBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2));
        var grass = new HologramChunk(new ChunkPos(0, 0), 0x88AA55, 0x77AA44, 0x3355DD, cells);
        try(var renderer = HologramProjectionRenderer.staticModels(null)){
            draw(renderer, grass, HologramResolution.FULL, 0);
            int fullVariation = checkPixels((VertexBuffer)get(mesh(renderer), "buffer"));
            draw(renderer, grass, HologramResolution.LOW, 0);
            var low = mesh(renderer);
            require((int)get(low, "colorQuads") == 1 && (int)get(get(low, "buffer"), "indexCount") == 6,
                    "Layered grass fell back to its full textured model");
            int lowVariation = checkPixels((VertexBuffer)get(low, "buffer"));
            require(fullVariation > lowVariation + 4, "Low and Full produced the same grass texture detail");
            var prev = new Matrix4f(RenderSystem.getProjectionMatrix()); var sorting = RenderSystem.getVertexSorting();
            var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
            try{
                modelView.identity(); RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(70), 1, .1F, 256), VertexSorting.DISTANCE_TO_ORIGIN);
                var poses = new PoseStack(); poses.translate(-8, -1.2, -8);
                renderer.render(poses, null, net.minecraft.world.phys.Vec3.ZERO, List.of(grass), 0, HologramResolution.FULL);
                require((int)get(mesh(renderer), "level") == 0, "Close-up Full no longer kept its texture");
                renderer.render(poses, null, net.minecraft.world.phys.Vec3.ZERO, List.of(grass), 0, HologramResolution.LOW);
                require((int)get(mesh(renderer), "level") >= 2 && (int)get(mesh(renderer), "colorQuads") == 1,
                        "Camera-intersecting chunks disabled Low resolution");
            }finally{
                modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(prev, sorting);
            }
        }
    }

    private static int checkPixels(VertexBuffer colors){
        Runnable restore = com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneEnvironment.save();
        var prev = new Matrix4f(RenderSystem.getProjectionMatrix()); var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(64, 64, true, Minecraft.ON_OSX);
        var type = com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderTypes.terrain();
        int variation = 0;
        try{
            modelView.identity(); RenderSystem.applyModelViewMatrix();
            var whiteId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gadgetsngizmos", "block/hologram_white");
            var white = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(whiteId);
            require(white.contents().name().equals(whiteId) && white.getPixelRGBA(0, 0, 0) == -1, "LOD neutral atlas sprite was not loaded");
            for(boolean front : new boolean[]{true, false}){
                target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                var projection = new Matrix4f().ortho(-1, 17, front ? -17 : -1, front ? 1 : 17, -10, 10);
                RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
                type.setupRenderState();
                try{
                    colors.bind(); colors.drawWithShader(new Matrix4f().rotateX((float)(front ? Math.PI / 2 : -Math.PI / 2)), projection, RenderSystem.getShader());
                }finally{ VertexBuffer.unbind(); type.clearRenderState(); }
                var pixels = org.lwjgl.BufferUtils.createByteBuffer(64 * 64 * 4);
                org.lwjgl.opengl.GL11.glReadPixels(0, 0, 64, 64, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixels);
                require((Byte.toUnsignedInt(pixels.get((32 * 64 + 32) * 4 + 3)) > 0) == front, "LOD faces disappeared or had reversed winding");
                if(front){
                    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                    for(int x = 12; x < 52; x++) for(int y = 12; y < 52; y++){
                        int offset = (y * 64 + x) * 4;
                        int sum = Byte.toUnsignedInt(pixels.get(offset)) + Byte.toUnsignedInt(pixels.get(offset + 1)) + Byte.toUnsignedInt(pixels.get(offset + 2));
                        min = Math.min(min, sum); max = Math.max(max, sum);
                    }
                    variation = max - min;
                }
            }
        }finally{
            target.destroyBuffers(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(prev, sorting); restore.run();
        }
        return variation;
    }

    private static void draw(HologramProjectionRenderer renderer, HologramChunk chunk, HologramResolution quality, long generation){
        var prev = new Matrix4f(RenderSystem.getProjectionMatrix()); var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try{
            modelView.identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().ortho(-512, 512, -512, 512, -512, 512), VertexSorting.ORTHOGRAPHIC_Z);
            renderer.render(new PoseStack(), null, net.minecraft.world.phys.Vec3.ZERO, List.of(chunk), generation, quality);
        }finally{
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(prev, sorting);
        }
    }

    private static Object mesh(HologramProjectionRenderer renderer){ return ((Map<?, ?>)get(renderer, "meshes")).values().iterator().next(); }
    private static Object get(Object owner, String name){
        try{ Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch(ReflectiveOperationException err){ throw new AssertionError("Cannot inspect hologram LOD resources", err); }
    }
    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
}
