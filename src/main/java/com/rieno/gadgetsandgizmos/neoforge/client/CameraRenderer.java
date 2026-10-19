package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.CameraBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Quaternionf;

// Draw the stand and its independently tilted camera child
public final class CameraRenderer extends SafeBlockEntityRenderer<CameraBlockEntity>{
    // Create the registered partial renderer
    public CameraRenderer(BlockEntityRendererProvider.Context ctx){}
    // Apply mount, stand and camera transformations around their shared hinge
    @Override
    protected void renderSafe(CameraBlockEntity be, float partialTick, PoseStack stack,
                              MultiBufferSource buffers, int light, int overlay){
        var joints = be.joints(partialTick);
        var vertices = buffers.getBuffer(RenderType.cutoutMipped());
        stack.pushPose();
        stack.translate(0.5D, 0.5D, 0.5D);
        stack.mulPose(new Quaternionf(be.mountOrientation()));
        stack.mulPose(new Quaternionf(joints.stand()));
        stack.translate(-0.5D, -0.5D, -0.5D);
        CachedBuffers.partial(CTPartialModels.CAMERA_STAND, be.getBlockState())
                .light(light).renderInto(stack, vertices);
        stack.translate(0.5D, 0.5D, 0.5D);
        stack.mulPose(new Quaternionf(joints.camera()));
        stack.translate(-0.5D, -0.5D, -0.5D);
        CachedBuffers.partial(CTPartialModels.CAMERA_HEAD, be.getBlockState())
                .light(light).renderInto(stack, vertices);
        stack.popPose();
    }
}
