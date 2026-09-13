
package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.PhysicsStaffAnchorBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

// Draw the animated Physics Staff parts in the placed anchor
public class PhysicsStaffAnchorRenderer extends SafeBlockEntityRenderer<PhysicsStaffAnchorBlockEntity> {
    private static final int FULL_BRIGHT = 0xF000F0;

    // Initialize the placed Physics Staff renderer
    public PhysicsStaffAnchorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    // Draw the placed Physics Staff parts
    @Override
    protected void renderSafe(PhysicsStaffAnchorBlockEntity blockEntity, float partialTicks,
                              PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (blockEntity.getStaff().isEmpty()) return;

        BlockState state = blockEntity.getBlockState();
        float worldTime = AnimationTickHolder.getRenderTime() / 20.0F;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);

        renderSolidPart(CTPartialModels.PHYSICS_STAFF_CORE, state, poseStack, buffer, FULL_BRIGHT);
        renderGlowingPart(CTPartialModels.PHYSICS_STAFF_CORE_GLOW, state, poseStack, buffer);

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.40625D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(worldTime * 35.0F));
        renderPart(CTPartialModels.PHYSICS_STAFF_RING, state, poseStack, buffer, light);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.5625D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(worldTime * -25.0F));
        renderPart(CTPartialModels.PHYSICS_STAFF_SIGMA, state, poseStack, buffer, light);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.9375D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(worldTime * 50.0F));
        renderSolidPart(CTPartialModels.PHYSICS_STAFF_INNER_CUBE, state, poseStack, buffer, FULL_BRIGHT);
        poseStack.scale(1.2F, 1.2F, 1.2F);
        poseStack.mulPose(Axis.XP.rotationDegrees(worldTime * 40.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(worldTime * 32.0F));
        renderGlowingPart(CTPartialModels.PHYSICS_STAFF_OUTER_CUBE, state, poseStack, buffer);
        poseStack.popPose();

        poseStack.popPose();
    }

    // Draw a normal staff part around the item-model center
    private static void renderPart(PartialModel model, BlockState state, PoseStack poseStack,
                                   MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        CachedBuffers.partial(model, state).light(light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
        poseStack.popPose();
    }

    // Draw a full-bright staff part around the item-model center
    private static void renderSolidPart(PartialModel model, BlockState state, PoseStack poseStack,
                                        MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        CachedBuffers.partial(model, state).disableDiffuse().light(light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
        poseStack.popPose();
    }

    // Draw a glowing staff part around the item-model center
    private static void renderGlowingPart(PartialModel model, BlockState state, PoseStack poseStack,
                                          MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        CachedBuffers.partial(model, state).disableDiffuse().light(FULL_BRIGHT)
                .renderInto(poseStack, buffer.getBuffer(RenderType.translucent()));
        poseStack.popPose();
    }
}
