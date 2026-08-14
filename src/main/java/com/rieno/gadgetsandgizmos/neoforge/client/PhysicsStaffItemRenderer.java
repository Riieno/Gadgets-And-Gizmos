package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3d;

import java.lang.reflect.Field;

// Draw the Physics Staff item
public class PhysicsStaffItemRenderer extends CustomRenderedItemModelRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Matrix4f itemProjMat = new Matrix4f();
    private static final Vector3d legacyFocusPos = accessLegacyVector("focusPos");
    private static final Matrix4f legacyItemProjMat = accessLegacyMatrix("itemProjMat");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the physics staff item
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay) {
        boolean guiRender = ctx == ItemDisplayContext.GUI;
        if (guiRender) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.scale(0.72F, 0.72F, 0.72F);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
        }

        renderer.render(model.getOriginalModel(), light);
        renderer.renderSolidGlowing(CTPartialModels.PHYSICS_STAFF_CORE.get(), 0xF000F0);
        renderer.renderGlowing(CTPartialModels.PHYSICS_STAFF_CORE_GLOW.get(), 0xF000F0);

        float worldTime = AnimationTickHolder.getRenderTime() / 20.0f;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.40625, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(worldTime * 35.0f));
        renderer.render(CTPartialModels.PHYSICS_STAFF_RING.get(), light);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0, 0.5625, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(worldTime * -25.0f));
        renderer.render(CTPartialModels.PHYSICS_STAFF_SIGMA.get(), light);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0, 0.9375, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(worldTime * 50.0f));
        renderer.renderSolidGlowing(CTPartialModels.PHYSICS_STAFF_INNER_CUBE.get(), 0xF000F0);

        if (ctx.firstPerson()) {
            Vector3f focusPoint = new Vector3f();
            poseStack.last().pose().transformPosition(focusPoint);
            itemProjMat.set(RenderSystem.getProjectionMatrix());
            syncLegacyProjectionState(new Vector3d(focusPoint.x, focusPoint.y, focusPoint.z));
        }

        poseStack.scale(1.2f, 1.2f, 1.2f);
        poseStack.mulPose(Axis.XP.rotationDegrees(worldTime * 40.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(worldTime * 32.0f));
        renderer.renderGlowing(CTPartialModels.PHYSICS_STAFF_OUTER_CUBE.get(), 0xF000F0);
        poseStack.popPose();

        if (guiRender) {
            poseStack.popPose();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sync the legacy projection state
    private static void syncLegacyProjectionState(Vector3d focusPos) {
        if (legacyFocusPos != null) {
            legacyFocusPos.set(focusPos);
        }
        if (legacyItemProjMat != null) {
            legacyItemProjMat.set(itemProjMat);
        }
    }

    // Get the access legacy vector
    private static Vector3d accessLegacyVector(String fieldName) {
        try {
            Field field = dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItemRenderer.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Vector3d) field.get(null);
        } catch (ReflectiveOperationException err) {
            return null;
        }
    }

    // Get the access legacy matrix
    private static Matrix4f accessLegacyMatrix(String fieldName) {
        try {
            Field field = dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItemRenderer.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Matrix4f) field.get(null);
        } catch (ReflectiveOperationException err) {
            return null;
        }
    }
}
