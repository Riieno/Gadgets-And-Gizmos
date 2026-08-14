package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// Draw the portable controller in the player's hands and body layer
public class PortableContraptionControllerPlayerLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float CONTROLLER_SCALE = 0.72F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable contraption controller player layer
    public PortableContraptionControllerPlayerLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    // Register the portable contraption controller player layer
    static void register(EntityRenderersEvent.AddLayers evt) {
        for (PlayerSkin.Model skin : evt.getSkins()) {
            PlayerRenderer renderer = evt.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new PortableContraptionControllerPlayerLayer(renderer));
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the portable contraption controller player layer
    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack controller = PortableContraptionControllerItemRenderer.activeThirdPersonController(player);
        if (controller.isEmpty()) {
            return;
        }

        BakedModel model = PortableContraptionControllerItemRenderer.controllerModel(controller, player);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.43F, -0.58F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-152.0F));
        poseStack.scale(CONTROLLER_SCALE, CONTROLLER_SCALE, CONTROLLER_SCALE);
        poseStack.translate(-0.5F, -0.125F, -0.5625F);
        PortableContraptionControllerItemRenderer.renderBakedControllerModel(
                poseStack,
                bufferSource,
                packedLight,
                model);
        poseStack.popPose();
    }
}
