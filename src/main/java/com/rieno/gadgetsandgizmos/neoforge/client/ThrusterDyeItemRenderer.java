package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.ThrusterDyeData;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectSurfaceRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.GradientBakedModel;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderVisibilityGate;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

// Show the confirmed preset on the tilted screen and the stored dye mix inside the barrel
public final class ThrusterDyeItemRenderer extends CustomRenderedItemModelRenderer{
    private static final ResourceLocation LIQUID_TEXTURE = ResourceLocation.parse("createthrusters:item/thruster_dye_applicator_liquid");
    private static final AABB SCREEN_BOUNDS = new AABB(7.05F / 16 - .5F, 7.2518F / 16 - .5F, 12.4F / 16 - .5F,
            8.95F / 16 - .5F, 8.6354F / 16 - .5F, 13.7835F / 16 - .5F);
    private final RenderVisibilityGate<ItemStack> visibility = new RenderVisibilityGate<>();
    private BakedModel baseModel, liquidModel;
    private WeightedColorGradient liquidGradient;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                          int light, int overlay){
        BakedModel base = model.getOriginalModel();
        var gradient = ThrusterDyeData.storage(stack).gradient();
        if(base != baseModel || !gradient.equals(liquidGradient)){
            baseModel = base;
            liquidGradient = gradient;
            liquidModel = new GradientBakedModel(base, LIQUID_TEXTURE, Direction.Axis.Z, 5.1F / 16, 11.9F / 16, gradient);
        }
        renderer.render(liquidModel, light);
        if(context != ItemDisplayContext.GUI && !ViewSceneRenderer.isCapturing()
                && !visibility.shouldRender(stack, context, pose, buffers, SCREEN_BOUNDS)) return;
        var preset = ThrusterDyeData.preset(stack);
        if(!preset.active()) return;
        pose.pushPose();
        try{
            pose.translate(8F / 16 - .5F, 4.63139F / 16 - .5F, 9.28912F / 16 - .5F);
            pose.mulPose(Axis.XP.rotationDegrees(45));
            pose.translate((7.05F - 8) / 16, (9.65734F - 4.63139F) / 16, (8.69179F - 9.28912F) / 16);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            ColorEffectSurfaceRenderer.render(pose, buffers, preset, AnimationTickHolder.getRenderTime(),
                    1.9F / 16, 1.9F / 16);
        }finally{
            pose.popPose();
        }
    }
}
