package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedDisplayWidgets;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudCameraSource;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudWidgetRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.WidgetCanvasRenderer;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedRenderer;
import com.rieno.gadgetsandgizmos.lib.display.WidgetCanvasProjection;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Render unbound ACC holograms using the same widget painter as displays and the designer
final class AdvancedControllerHolograms{
    // Keep the block renderer's local projection policy outside the reusable canvas API
    private AdvancedControllerHolograms(){}

    // Allow a visible hologram to survive culling of the smaller controller block
    static boolean hasWidgets(AdvancedContraptionControllerBlockEntity controller){
        return controller.hasClientHologramWidgets() && controller.clientHudNodes().stream().anyMatch(node -> AdvancedDisplayWidgets.isUntargetedHologram(
                node, value(controller, node, "target")) && value(controller, node, "visible").asBoolean());
    }

    // Anchor the bottom of each unbound canvas above the ACC and retain its authored offsets
    static void render(AdvancedContraptionControllerBlockEntity controller, PoseStack poses, MultiBufferSource buffers){
        if(!controller.hasClientHologramWidgets()) return;
        AnalogueContraptionControllerClientHandler.ensureRuntimeBaseline(controller, -1);
        int ppb = AccDisplayBlockEntity.PIXELS_PER_BLOCK;
        var font = Minecraft.getInstance().font;
        for(var node : controller.clientHudNodes()){
            if(!AdvancedDisplayWidgets.isUntargetedHologram(node, value(controller, node, "target"))
                    || !value(controller, node, "visible").asBoolean()) continue;
            var canvas = WidgetCanvasProjection.layout(true, WidgetCanvasProjection.canvasWidth(node.data()),
                    WidgetCanvasProjection.canvasHeight(node.data()), 1, 1,
                    value(controller, node, "size_x").asNumber(), value(controller, node, "size_y").asNumber(),
                    value(controller, node, "offset_x").asNumber(), value(controller, node, "offset_y").asNumber(),
                    value(controller, node, "offset_z").asNumber(), value(controller, node, "projection_rotation").asNumber(), ppb);
            poses.pushPose();
            try{
                poses.translate(.5, 1.25 + canvas.height() / ppb, .5);
                poses.mulPose(Axis.YP.rotationDegrees(-controller.getBlockState()
                        .getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING).toYRot()));
                poses.scale(1F / ppb, -1F / ppb, 1F / ppb);
                poses.translate(-canvas.width() / 2, 0, 0);
                WidgetCanvasRenderer.render(poses, canvas, node.data().getList("WidgetElements", Tag.TAG_COMPOUND), false,
                        elm -> AdvancedHudElementBinding.resolvedCopy(elm, port -> value(controller, node, port)), (elm, w, h) -> {
                            var surface = HudWidgetRenderer.surface(poses, buffers, font, -.01F, -.014F, -.02F, LightTexture.FULL_BRIGHT);
                            if("image".equals(elm.getString("Type"))){
                                var texture = AdvancedHudImageClient.resolveTexture(AdvancedHudWidgetRenderer.imageSource(
                                        elm, port -> value(controller, node, port)));
                                if(texture == null) HudWidgetRenderer.placeholder(surface, font, "IMG", w, h);
                                else surface.draw(new GuiTextureRegion(texture, 1, 1, 0, 0, 1, 1), 0, 0, w, h);
                            }else if(AdvancedHudCameraSource.TYPE.equals(elm.getString("Type"))){
                                ViewReference ref = ViewReference.fromTag(value(controller, node, AdvancedHudCameraSource.runtimePort(elm)).payload());
                                ViewFeedRenderer.drawSurface(surface, font, ref, w, h, -.01F, poses, buffers);
                            }else AdvancedHudWidgetRenderer.draw(surface, font, elm, w, h,
                                    port -> value(controller, node, port), false);
                        });
            }finally{ poses.popPose(); }
        }
    }

    // Read complete streamed inputs before falling back to the element's authored defaults
    private static AdvancedGraphDocument.Value value(AdvancedContraptionControllerBlockEntity controller,
                                                       AdvancedGraphDocument.Node node, String port){
        var live = controller.getGraphLiveInput(node.id(), port);
        if(live == null) live = controller.getGraphLiveOutput(node.id(), port);
        if(live != null && live.value() != null) return live.value();
        CompoundTag defaults = node.data().getCompound("Defaults");
        if(defaults.contains(port, Tag.TAG_COMPOUND)) return AdvancedGraphDocument.Value.fromTag(defaults.getCompound(port));
        return AdvancedGraphDocument.Value.number(0);
    }
}
