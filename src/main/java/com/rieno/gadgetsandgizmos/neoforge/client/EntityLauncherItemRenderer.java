package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.EntityLauncherClawEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

// Draw the Entity Launcher item
public class EntityLauncherItemRenderer extends CustomRenderedItemModelRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Vector3d focusPos = new Vector3d();
    public static final Matrix4f itemProjMat = new Matrix4f();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the entity launcher item
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay) {
        renderer.render(model.getOriginalModel(), light);
        if (hasOwnedActiveClaw()) {
            return;
        }

        poseStack.pushPose();
        if (ctx.firstPerson()) {
            poseStack.translate(0.0, 0.02, -0.34);
        } else if (ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            poseStack.translate(0.0, 0.0, -0.3);
        } else if (ctx == ItemDisplayContext.GUI) {
            poseStack.translate(0.0, 0.0, -0.24);
            poseStack.scale(0.9f, 0.9f, 0.9f);
        } else {
            poseStack.translate(0.0, 0.0, -0.28);
        }
        renderer.render(CTPartialModels.ENTITY_LAUNCHER_CLAW.get(), light);
        if (ctx.firstPerson()) {

            Vector3f focusPoint = new Vector3f();
            poseStack.last().pose().transformPosition(focusPoint);
            itemProjMat.set(RenderSystem.getProjectionMatrix());
            focusPos.set(focusPoint.x, focusPoint.y, focusPoint.z);
        }
        poseStack.popPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this has owned active claw
    private static boolean hasOwnedActiveClaw() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) {
            return false;
        }
        return !player.level().getEntitiesOfClass(EntityLauncherClawEntity.class,
                player.getBoundingBox().inflate(256.0),
                claw -> claw.getOwner() == player && !claw.isRemoved()).isEmpty();
    }
}
