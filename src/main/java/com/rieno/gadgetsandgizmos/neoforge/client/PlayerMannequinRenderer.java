package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;


// Draw the Player Mannequin
public class PlayerMannequinRenderer extends LivingEntityRenderer<PlayerMannequinEntity, PlayerMannequinModel> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final PlayerMannequinModel wideModel;
    private final PlayerMannequinModel slimModel;

    // Initialize the player mannequin
    public PlayerMannequinRenderer(EntityRendererProvider.Context ctx) {
        this(ctx,
                new PlayerMannequinModel(ctx.bakeLayer(ModelLayers.PLAYER), false),
                new PlayerMannequinModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));
    }

    // Initialize both player arm model variants
    private PlayerMannequinRenderer(EntityRendererProvider.Context ctx, PlayerMannequinModel wideModel,
                                    PlayerMannequinModel slimModel) {
        super(ctx, wideModel, 0.0F);
        this.wideModel = wideModel;
        this.slimModel = slimModel;
        this.addLayer(new PlayerMannequinSkinLayer(this));
        this.addLayer(
                new HumanoidArmorLayer<>(
                        this,
                        new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                        new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                        ctx.getModelManager()
                )
        );
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, ctx.getModelSet()));
        this.addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the texture location
    @Override
    public ResourceLocation getTextureLocation(PlayerMannequinEntity entity) {
        return PlayerMannequinSkinResolver.texture(entity);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Select the matching model before Minecraft renders the base model and every layer
    @Override
    public void render(PlayerMannequinEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        this.model = entity.usesSlimSkin() ? slimModel : wideModel;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    // Set up the rotations
    @Override
    protected void setupRotations(PlayerMannequinEntity entity, PoseStack poseStack, float bob, float bodyYaw,
                                  float partialTick, float scale) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        float hitTime = (float) (entity.level().getGameTime() - entity.lastHit) + partialTick;
        if (hitTime < 5.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(hitTime / 1.5F * (float) Math.PI) * 3.0F));
        }
    }

    // Scale the player mannequin
    @Override
    protected void scale(PlayerMannequinEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    // Check if this should show name
    @Override
    protected boolean shouldShowName(PlayerMannequinEntity entity) {
        double distance = this.entityRenderDispatcher.distanceToSqr(entity);
        float range = entity.isCrouching() ? 32.0F : 64.0F;
        return distance < (double) (range * range) && entity.isCustomNameVisible();
    }
}
