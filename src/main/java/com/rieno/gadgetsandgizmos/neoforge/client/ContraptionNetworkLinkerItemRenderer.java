package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.LinkedHashSet;
import java.util.Set;

// Draw the Contraption Network Linker item
public class ContraptionNetworkLinkerItemRenderer extends CustomRenderedItemModelRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int COUNT_TEXT_COLOR = 0xFFFFFFFF;

    private static final float SCREEN_LEFT = 4.7f / 16.0f - 0.5f;
    private static final float SCREEN_RIGHT = 10.7f / 16.0f - 0.5f;
    private static final float SCREEN_TOP = 13.0f / 16.0f - 0.5f;
    private static final float SCREEN_BOTTOM = 10.5f / 16.0f - 0.5f;
    private static final float SCREEN_TEXT_Z = 6.9f / 16.0f - 0.5f;
    private static final float SCREEN_TEXT_WIDTH_PAD = 0.9f;
    private static final float SCREEN_TEXT_HEIGHT_PAD = 0.86f;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the contraption network linker item
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay) {
        int linkedBlocks = linkedBlockCount(stack);
        boolean linked = linkedBlocks > 0;

        if (ctx == ItemDisplayContext.GUI) {
            renderer.render(linked
                    ? CTPartialModels.CONTRAPTION_NETWORK_LINKER_LINKED_GUI.get()
                    : CTPartialModels.CONTRAPTION_NETWORK_LINKER_UNLINKED_GUI.get(), light);
            return;
        }

        if (ctx == ItemDisplayContext.GROUND) {
            renderer.render(CTPartialModels.CONTRAPTION_NETWORK_LINKER_ITEM.get(), light);
            return;
        }

        if (isInHand(ctx)) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            renderer.render(CTPartialModels.CONTRAPTION_NETWORK_LINKER_NO_SCREEN.get(), light);
            renderer.render(CTPartialModels.CONTRAPTION_NETWORK_LINKER_SCREEN.get(),
                    linked ? LightTexture.FULL_BRIGHT : light);
            if (linked) {
                renderLinkedBlockCount(String.valueOf(linkedBlocks), poseStack, buffer);
            }
            poseStack.popPose();
            return;
        }

        renderer.render(model.getOriginalModel(), light);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the linked block count
    public static int linkedBlockCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        Set<String> linkedBlocks = new LinkedHashSet<>();
        for (ContraptionNetworkLinkerData.LinkedTarget target : ContraptionNetworkLinkerData.readClientTargets(stack)) {
            String subLevel = target.subLevelId() == null ? "world" : target.subLevelId().toString();
            linkedBlocks.add(subLevel + ":" + target.blockPos().asLong());
        }
        return linkedBlocks.size();
    }

    // Check if this is in the hand
    private static boolean isInHand(ItemDisplayContext ctx) {
        return ctx.firstPerson()
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    // Draw the linked block count
    private static void renderLinkedBlockCount(String text, PoseStack poseStack, MultiBufferSource buffer) {
        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(1, font.width(text));
        float maxWidth = (SCREEN_RIGHT - SCREEN_LEFT) * SCREEN_TEXT_WIDTH_PAD;
        float maxHeight = (SCREEN_TOP - SCREEN_BOTTOM) * SCREEN_TEXT_HEIGHT_PAD;
        float scale = Math.min(maxWidth / textWidth, maxHeight / font.lineHeight);

        poseStack.pushPose();
        poseStack.translate((SCREEN_LEFT + SCREEN_RIGHT) * 0.5f,
                (SCREEN_TOP + SCREEN_BOTTOM) * 0.5f,
                SCREEN_TEXT_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.scale(scale, -scale, scale);

        Matrix4f pose = poseStack.last().pose();
        font.drawInBatch(text, -textWidth / 2.0f, -font.lineHeight / 2.0f, COUNT_TEXT_COLOR, false, pose, buffer,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
