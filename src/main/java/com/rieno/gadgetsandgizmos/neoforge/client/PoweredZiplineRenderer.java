package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;

// Draw the Powered Zipline
public class PoweredZiplineRenderer extends SafeBlockEntityRenderer<PoweredZiplineBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the powered zipline
    public PoweredZiplineRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the powered zipline
    @Override
    protected void renderSafe(PoweredZiplineBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        RopeStrandHolderBehavior holder = be.getRopeHolder();
        if (holder instanceof PoweredZiplineClientRopeAccess access) {
            for (ClientRopeStrand strand : access.createthrusters$getZiplineClientStrands()) {
                holder.giveFakeClientStrand(strand);
                RopeStrandRenderer.render(be, holder, partialTicks, ms, buffer);
            }
        }
    }
}
