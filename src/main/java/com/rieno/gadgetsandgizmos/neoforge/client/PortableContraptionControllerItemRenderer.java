package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Quaternionf;

// Draw the Portable Contraption Controller item
public class PortableContraptionControllerItemRenderer extends CustomRenderedItemModelRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float TRANSITION_EPSILON = 0.01F;
    private static final float HELD_VERTICAL_POSITION = -0.46F;
    private static final float HELD_DEPTH = -0.72F;
    private static final float CONTROLLER_SCALE = 0.9F;

    private static final LerpedFloat EQUIP_PROGRESS = LerpedFloat.linear().startWithValue(0.0D);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the portable contraption controller item
    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        EQUIP_PROGRESS.chase(
                AnalogueContraptionControllerClientHandler.isPortableInteractModeActive() ? 1.0D : 0.0D,
                0.2D,
                LerpedFloat.Chaser.EXP);
        EQUIP_PROGRESS.tickChaser();
    }

    // Handle the render hand event
    static void onRenderHand(RenderHandEvent evt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        InteractionHand controllerHand = AnalogueContraptionControllerClientHandler.portableInteractionHand();
        ItemStack controller = minecraft.player.getItemInHand(controllerHand);
        float transition = Mth.clamp(
                EQUIP_PROGRESS.getValue(evt.getPartialTick()),
                0.0F,
                1.0F);
        boolean interactMode = AnalogueContraptionControllerClientHandler.isPortableInteractModeActive();
        if ((!interactMode && transition <= TRANSITION_EPSILON)
                || !(controller.getItem() instanceof PortableContraptionControllerItem)) {
            return;
        }

        evt.setCanceled(true);
        if (evt.getHand() != controllerHand) {
            return;
        }

        renderTwoHandedCtrl(evt, controller, transition);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the two handed ctrl
    private static void renderTwoHandedCtrl(RenderHandEvent evt, ItemStack controller, float transition) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        HumanoidArm controllerArm = evt.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();

        PoseStack poseStack = evt.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0F, HELD_VERTICAL_POSITION, HELD_DEPTH);
        renderControllerHands(
                poseStack,
                evt.getMultiBufferSource(),
                evt.getPackedLight(),
                player);
        poseStack.popPose();

        renderFirstPersonCtrl(
                evt,
                controller,
                player,
                controllerArm,
                transition);
    }

    // Draw the controller hands
    private static void renderControllerHands(PoseStack poseStack, MultiBufferSource bufferSource,
                                              int packedLight, AbstractClientPlayer player) {
        if (player.isInvisible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        PlayerRenderer playerRenderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher()
                .<AbstractClientPlayer>getRenderer(player);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        renderControllerHand(poseStack, bufferSource, packedLight, player, playerRenderer, HumanoidArm.RIGHT);
        renderControllerHand(poseStack, bufferSource, packedLight, player, playerRenderer, HumanoidArm.LEFT);
        poseStack.popPose();
    }

    // Draw the controller hand
    private static void renderControllerHand(PoseStack poseStack, MultiBufferSource bufferSource,
                                             int packedLight, AbstractClientPlayer player,
                                             PlayerRenderer playerRenderer, HumanoidArm arm) {
        poseStack.pushPose();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -41.0F));
        poseStack.translate(side * 0.3F, -1.1F, 0.45F);
        if (arm == HumanoidArm.RIGHT) {
            playerRenderer.renderRightHand(poseStack, bufferSource, packedLight, player);
        } else {
            playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, player);
        }
        poseStack.popPose();
    }

    // Draw the first person ctrl
    private static void renderFirstPersonCtrl(RenderHandEvent evt, ItemStack controller,
                                                    AbstractClientPlayer player, HumanoidArm controllerArm,
                                                    float transition) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(
                controller,
                minecraft.level,
                player,
                0);
        boolean leftHand = controllerArm == HumanoidArm.LEFT;
        ItemDisplayContext ctx = leftHand
                ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        ItemTransform handTransform = model.getTransforms().getTransform(ctx);
        if (model instanceof CustomRenderedItemModel customModel) {
            model = customModel.getOriginalModel();
        }

        float side = leftHand ? -1.0F : 1.0F;
        float handTranslationX = side * handTransform.translation.x();
        float handRotationY = leftHand ? -handTransform.rotation.y() : handTransform.rotation.y();
        float handRotationZ = leftHand ? -handTransform.rotation.z() : handTransform.rotation.z();
        float normalX = side * 0.56F + handTranslationX;
        float normalY = -0.52F - evt.getEquipProgress() * 0.6F + handTransform.translation.y();
        float normalZ = -0.72F + handTransform.translation.z();

        PoseStack poseStack = evt.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                Mth.lerp(transition, normalX, 0.0F),
                Mth.lerp(transition, normalY, HELD_VERTICAL_POSITION - 0.05F),
                Mth.lerp(transition, normalZ, HELD_DEPTH + 0.05F));
        poseStack.mulPose(new Quaternionf().rotationXYZ(
                handTransform.rotation.x() * Mth.DEG_TO_RAD,
                handRotationY * Mth.DEG_TO_RAD,
                handRotationZ * Mth.DEG_TO_RAD));
        poseStack.scale(
                Mth.lerp(transition, handTransform.scale.x(), CONTROLLER_SCALE),
                Mth.lerp(transition, handTransform.scale.y(), CONTROLLER_SCALE),
                Mth.lerp(transition, handTransform.scale.z(), CONTROLLER_SCALE));
        poseStack.translate(
                -0.5F,
                Mth.lerp(transition, -0.5F, -0.125F),
                Mth.lerp(transition, -0.5F, -0.5625F));
        renderBakedControllerModel(
                poseStack,
                evt.getMultiBufferSource(),
                evt.getPackedLight(),
                model);
        poseStack.popPose();
    }

    // Draw the baked controller model
    static void renderBakedControllerModel(PoseStack poseStack, MultiBufferSource bufferSource,
                                           int packedLight, BakedModel model) {
        Minecraft minecraft = Minecraft.getInstance();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutoutMipped());
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                Blocks.AIR.defaultBlockState(),
                model,
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                OverlayTexture.NO_OVERLAY);
    }

    // Get the controller model
    static BakedModel controllerModel(ItemStack controller, AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(
                controller,
                minecraft.level,
                player,
                0);
        if (model instanceof CustomRenderedItemModel customModel) {
            return customModel.getOriginalModel();
        }
        return model;
    }

    // Check if the third person pose is active
    public static boolean isThirdPersonPoseActive(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity instanceof AbstractClientPlayer player)
                || player != minecraft.player
                || minecraft.options.getCameraType().isFirstPerson()
                || !AnalogueContraptionControllerClientHandler.isPortableInteractModeActive()) {
            return false;
        }
        return player.getItemInHand(AnalogueContraptionControllerClientHandler.portableInteractionHand())
                .getItem() instanceof PortableContraptionControllerItem;
    }

    // Get the active third person controller
    static ItemStack activeThirdPersonController(AbstractClientPlayer player) {
        if (!isThirdPersonPoseActive(player)) {
            return ItemStack.EMPTY;
        }
        return player.getItemInHand(AnalogueContraptionControllerClientHandler.portableInteractionHand());
    }

    // Check if this is an active third person controller
    private static boolean isActiveThirdPersonCtrl(ItemDisplayContext ctx, ItemStack stack) {
        if (ctx != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && ctx != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (player == null
                || !isThirdPersonPoseActive(player)
                || !(stack.getItem() instanceof PortableContraptionControllerItem)) {
            return false;
        }

        HumanoidArm controllerArm = AnalogueContraptionControllerClientHandler.portableInteractionHand()
                == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        return ctx == (controllerArm == HumanoidArm.RIGHT
                ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                : ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
    }

    // Draw the portable contraption controller item
    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffer,
                          int light, int overlay) {
        if (isActiveThirdPersonCtrl(ctx, stack)) {
            return;
        }
        renderer.render(model.getOriginalModel(), light);
    }
}
