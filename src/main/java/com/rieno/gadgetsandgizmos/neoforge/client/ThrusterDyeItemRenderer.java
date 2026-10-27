package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.ThrusterDyeData;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectSurfaceRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderVisibilityGate;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

// Keep the supplied applicator model and show its confirmed preset on the inset screen
public final class ThrusterDyeItemRenderer extends CustomRenderedItemModelRenderer{
    private static final AABB SCREEN_BOUNDS = new AABB(5.6F / 16 - .5F, 7.02F / 16 - .502F, 3.6F / 16 - .5F,
            10.4F / 16 - .5F, 7.02F / 16 - .498F, 7.4F / 16 - .5F);
    private final RenderVisibilityGate<ItemStack> visibility = new RenderVisibilityGate<>();

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                          int light, int overlay){
        renderer.render(model.getOriginalModel(), light);
        if(context != ItemDisplayContext.GUI && !ViewSceneRenderer.isCapturing()
                && !visibility.shouldRender(stack, context, pose, buffers, SCREEN_BOUNDS)) return;
        var preset = ThrusterDyeData.preset(stack);
        if(!preset.active()) return;
        pose.pushPose();
        try{
            pose.translate(5.6F / 16 - .5F, 7.02F / 16 - .5F, 3.6F / 16 - .5F);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            ColorEffectSurfaceRenderer.render(pose, buffers, preset, AnimationTickHolder.getRenderTime(),
                    4.8F / 16, 3.8F / 16);
        }finally{
            pose.popPose();
        }
    }
}
