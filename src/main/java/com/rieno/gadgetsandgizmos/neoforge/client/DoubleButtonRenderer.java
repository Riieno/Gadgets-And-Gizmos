package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.content.DoubleButtonFrequencySlot;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

// Draw the Double Button
public class DoubleButtonRenderer extends SafeBlockEntityRenderer<DoubleButtonBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final DoubleButtonFrequencySlot TOP_FIRST =
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.TOP, true);
    private static final DoubleButtonFrequencySlot TOP_SECOND =
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.TOP, false);
    private static final DoubleButtonFrequencySlot BOTTOM_FIRST =
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.BOTTOM, true);
    private static final DoubleButtonFrequencySlot BOTTOM_SECOND =
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.BOTTOM, false);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button
    public DoubleButtonRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the double button
    @Override
    protected void renderSafe(DoubleButtonBlockEntity be, float partialTicks, PoseStack poseStack,
                              MultiBufferSource bufferSource, int light, int overlay) {
        if (!be.isLinkHardwareVisible()) {
            return;
        }
        BlockState state = be.getBlockState();
        renderFrequency(be, state, TOP_FIRST, poseStack, bufferSource, light, overlay);
        renderFrequency(be, state, TOP_SECOND, poseStack, bufferSource, light, overlay);
        renderFrequency(be, state, BOTTOM_FIRST, poseStack, bufferSource, light, overlay);
        renderFrequency(be, state, BOTTOM_SECOND, poseStack, bufferSource, light, overlay);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the frequency
    private static void renderFrequency(DoubleButtonBlockEntity be, BlockState state, DoubleButtonFrequencySlot slot,
                                        PoseStack poseStack, MultiBufferSource bufferSource,
                                        int light, int overlay) {
        ItemStack stack = be.getFrequency(slot.button(), slot.firstFrequency());
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        slot.transform(be.getLevel(), be.getBlockPos(), state, poseStack);
        ValueBoxRenderer.renderItemIntoValueBox(stack, poseStack, bufferSource, light, overlay);
        poseStack.popPose();
    }
}
