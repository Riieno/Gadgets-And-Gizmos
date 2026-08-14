package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

// Draw the Diagnostic Tablet
public class DiagnosticTabletRenderer extends SmartBlockEntityRenderer<DiagnosticTabletBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet
    public DiagnosticTabletRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the diagnostic tablet
    @Override
    protected void renderSafe(DiagnosticTabletBlockEntity tablet, float partialTicks,
                              PoseStack poseStack, MultiBufferSource buffer,
                              int light, int overlay) {
        DiagnosticTabletGuiProjection.renderBlock(tablet, partialTicks, poseStack, buffer);
    }
}
