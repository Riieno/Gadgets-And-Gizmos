package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

// Draw Player Mannequin skin layers
public class PlayerMannequinSkinLayer extends RenderLayer<PlayerMannequinEntity, PlayerMannequinModel> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin skin layer
    public PlayerMannequinSkinLayer(RenderLayerParent<PlayerMannequinEntity, PlayerMannequinModel> renderer) {
        super(renderer);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the player mannequin skin layer
    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, PlayerMannequinEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }

        PlayerMannequinModel model = getParentModel();
        model.showSkinLayersOnly(entity);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityNoOutline(getTextureLocation(entity)));
        model.renderToBuffer(poseStack, consumer, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
        model.showBaseOnly(entity);
    }
}
