package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.RopeKnotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// Draw the Rope Knot
public class RopeKnotRenderer implements BlockEntityRenderer<RopeKnotBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the rope knot
    public RopeKnotRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the rope knot
    @Override
    public void render(RopeKnotBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Vec3 offset = blockEntity.getAttachmentOffset();
        poseStack.pushPose();
        poseStack.translate(offset.x - 0.5D, offset.y - 0.5D, offset.z - 0.5D);
        CachedBuffers.partial(SimPartialModels.ROPE_KNOT, Blocks.AIR.defaultBlockState())
                .light(packedLight)
                .renderInto(poseStack, bufferSource.getBuffer(RenderType.solid()));
        poseStack.popPose();
    }
}
