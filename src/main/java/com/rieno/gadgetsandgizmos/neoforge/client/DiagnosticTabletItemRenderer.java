package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// Draw the Diagnostic Tablet item
public final class DiagnosticTabletItemRenderer extends CustomRenderedItemModelRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the diagnostic tablet item
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer, ItemDisplayContext ctx,
                          PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay) {
        renderer.render(model.getOriginalModel(), light);
        InteractionHand hand = interactionHand(ctx);
        if (hand != null) {
            DiagnosticTabletGuiProjection.renderItem(hand, stack, poseStack, buffer);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the interaction hand
    private static InteractionHand interactionHand(ItemDisplayContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        boolean rightArm;
        if (ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            rightArm = true;
        } else if (ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            rightArm = false;
        } else {
            return null;
        }
        boolean mainArm = minecraft.player.getMainArm() == HumanoidArm.RIGHT ? rightArm : !rightArm;
        return mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
