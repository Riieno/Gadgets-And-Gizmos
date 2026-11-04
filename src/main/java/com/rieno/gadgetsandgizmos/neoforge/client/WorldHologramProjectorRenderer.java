package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.WorldHologramProjectorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

// Let the library render within the block renderer's Sable-aware mounting transform
public final class WorldHologramProjectorRenderer implements BlockEntityRenderer<WorldHologramProjectorBlockEntity>{
    public WorldHologramProjectorRenderer(BlockEntityRendererProvider.Context ctx){}

    @Override
    public void render(WorldHologramProjectorBlockEntity projector, float partialTicks, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay){
        WorldHologramClient.render(projector, poses, buffers, partialTicks);
    }

    @Override public boolean shouldRenderOffScreen(WorldHologramProjectorBlockEntity projector){ return true; }
    @Override public boolean shouldRender(WorldHologramProjectorBlockEntity projector, Vec3 camera){
        return projector.canView(net.minecraft.client.Minecraft.getInstance().player, 64);
    }
}
