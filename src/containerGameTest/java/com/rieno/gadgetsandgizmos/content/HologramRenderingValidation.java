package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramProjectionRenderer;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

// Inspect actual GPU buffer ownership only in the isolated development client
final class HologramRenderingValidation{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    static final BlockPos SOURCE = new BlockPos(4, -59, 4);
    private static HologramProjectionRenderer renderer;
    private static HologramChunk original;
    private static VertexBuffer retained;
    private static long started;
    private static int modelReads;
    private static final ModelProperty<Integer> PROPERTY = new ModelProperty<>();
    private static ModelData probeData = ModelData.EMPTY;
    private static HologramProjectionRenderer hybrid;
    private static VertexBuffer hybridBuffer;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    static void begin(){
        var mc = Minecraft.getInstance();
        BlockState state = mc.level.getBlockState(SOURCE);
        require(state.is(Blocks.BARREL), "The model-data probe barrel was not loaded");
        original = surface(state, Map.of());
        withProbe(() -> {
            try(var live = new HologramProjectionRenderer()){
                draw(live, original, 0);
                require(modelReads > 0, "The compatibility renderer did not reach the model-data probe");
            }
            modelReads = 0;
            renderer = HologramProjectionRenderer.baseModels();
            draw(renderer, original, 0);
            retained = buffer(renderer);
            require(retained != null && !retained.isInvalid(), "Base block model produced no GPU mesh");
            require(modelReads == 0, "Base models requested live block entity model data");
            hybrid = HologramProjectionRenderer.retainedModels(null);
            draw(hybrid, original, 0);
            hybridBuffer = buffer(hybrid);
            require(hybridBuffer != null && !hybridBuffer.isInvalid(), "Retained model data produced no GPU mesh");
        });
        started = mc.level.getGameTime();
    }

    static boolean finish(){
        if(Minecraft.getInstance().level.getGameTime() - started < 25) return false;
        try{
            withProbe(() -> {
                modelReads = 0;
                draw(renderer, original, 0);
                require(buffer(renderer) == retained, "Base block entities triggered a periodic mesh rebuild");
                draw(renderer, surface(original.blocks().getFirst().state(), Map.of(SOURCE, 128F)), 0);
                require(buffer(renderer) == retained, "Kinetic animation rebuilt the base mesh");
                require(modelReads == 0, "Retained base models read live block entity data");
                draw(renderer, surface(Blocks.STONE.defaultBlockState(), Map.of()), 0);
                require(retained.isInvalid(), "Block replacement retained the previous GPU mesh");
                retained = buffer(renderer);
                require(retained != null && !retained.isInvalid(), "Block replacement lost the new mesh");
                draw(renderer, original, 1);
                require(retained.isInvalid(), "Resource generation retained the previous GPU mesh");
                retained = buffer(renderer);
                require(retained != null && !retained.isInvalid(), "Resource generation did not rebuild geometry");
                draw(renderer, null, 1);
                require(retained.isInvalid() && buffer(renderer) == null, "Removed chunks retained GPU geometry");
                draw(renderer, original, 1);
                retained = buffer(renderer);
                renderer.close();
                require(retained != null && retained.isInvalid(), "Owner cleanup retained GPU geometry");
                draw(hybrid, original, 0);
                require(buffer(hybrid) == hybridBuffer, "Unchanged model data triggered a periodic mesh rebuild");
                probeData = ModelData.builder().with(PROPERTY, 1).build();
                Minecraft.getInstance().level.getBlockEntity(SOURCE).requestModelDataUpdate();
                draw(hybrid, original, 0);
                require(hybridBuffer.isInvalid(), "Requested model-data update retained stale GPU geometry");
                hybridBuffer = buffer(hybrid);
                require(hybridBuffer != null && !hybridBuffer.isInvalid(), "Requested model-data update lost the new mesh");
                draw(hybrid, surface(original.blocks().getFirst().state(), Map.of(SOURCE, 128F)), 0);
                require(buffer(hybrid) == hybridBuffer, "Kinetic metadata rebuilt retained model data");
                hybrid.close();
                require(hybridBuffer.isInvalid(), "Retained model-data cleanup leaked a GPU buffer");
            });
        }finally{ if(renderer != null) renderer.close(); if(hybrid != null) hybrid.close(); }
        return true;
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static HologramChunk surface(BlockState state, Map<BlockPos, Float> speeds){
        return new HologramChunk(new ChunkPos(SOURCE), 0x88AA55, 0x77AA44, 0x3355DD,
                List.of(new HologramChunk.SurfaceBlock(SOURCE, state, 63)), speeds);
    }

    private static void withProbe(Runnable action){
        var mc = Minecraft.getInstance();
        var entities = mc.level.getChunkAt(SOURCE).getBlockEntities();
        var probe = new BarrelBlockEntity(SOURCE, mc.level.getBlockState(SOURCE)){
            @Override public ModelData getModelData(){ modelReads++; return probeData; }
        };
        probe.setLevel(mc.level);
        var prev = entities.put(SOURCE, probe);
        try{ action.run(); }
        finally{
            if(prev == null) entities.remove(SOURCE);
            else{ entities.put(SOURCE, prev); prev.requestModelDataUpdate(); }
        }
    }

    private static void draw(HologramProjectionRenderer target, HologramChunk chunk, long generation){
        var prevProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var prevSorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try{
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(60), 1, .1F, 256), VertexSorting.DISTANCE_TO_ORIGIN);
            var poses = new PoseStack();
            poses.translate(-8, 59, -40);
            target.render(poses, Vec3.ZERO, chunk == null ? List.of() : List.of(chunk), generation);
        }finally{
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(prevProjection, prevSorting);
        }
    }

    private static VertexBuffer buffer(HologramProjectionRenderer target){
        try{
            var field = HologramProjectionRenderer.class.getDeclaredField("meshes");
            field.setAccessible(true);
            var meshes = (Map<?, ?>)field.get(target);
            if(meshes.isEmpty()) return null;
            Object mesh = meshes.values().iterator().next();
            var buffer = mesh.getClass().getDeclaredMethod("buffer");
            buffer.setAccessible(true);
            return (VertexBuffer)buffer.invoke(mesh);
        }catch(ReflectiveOperationException err){ throw new AssertionError("Cannot inspect GPU resource ownership", err); }
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
}
