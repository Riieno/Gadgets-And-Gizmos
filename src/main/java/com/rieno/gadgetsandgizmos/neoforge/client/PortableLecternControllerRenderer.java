package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Draw the Portable Lectern Controller
public class PortableLecternControllerRenderer implements BlockEntityRenderer<LecternBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float LECTERN_SURFACE_TILT = 22.5F;
    private static final float LECTERN_MODEL_CLOCKWISE_ROTATION = 270.0F;
    private static final float LECTERN_CONTROLLER_SCALE = 0.72F;
    private static final double LECTERN_CONTROLLER_VERTICAL_OFFSET = 1.0D / 16.0D;
    private static final double LECTERN_CONTROLLER_FRONT_OFFSET = 2.0D / 16.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Vanilla renderer
    private final LecternRenderer vanillaRenderer;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable lectern controller
    public PortableLecternControllerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.vanillaRenderer = new LecternRenderer(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the portable lectern controller
    @Override
    public void render(LecternBlockEntity lectern, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack stack = lectern.getBook();
        if (!(stack.getItem() instanceof PortableContraptionControllerItem)) {
            vanillaRenderer.render(lectern, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Direction facing = lecternFacing(lectern.getBlockState());
        BakedModel model = minecraft.getItemRenderer().getModel(stack, lectern.getLevel(), null, 0);
        if (model instanceof CustomRenderedItemModel customModel) {
            model = customModel.getOriginalModel();
        }

        poseStack.pushPose();
        applyLecternCtrlTransform(poseStack, facing);
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
        poseStack.popPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the lectern ctrl transform
    private static void applyLecternCtrlTransform(PoseStack poseStack, Direction facing) {
        poseStack.translate(
                0.5D + facing.getStepX() * LECTERN_CONTROLLER_FRONT_OFFSET,
                1.0625D + LECTERN_CONTROLLER_VERTICAL_OFFSET,
                0.5D + facing.getStepZ() * LECTERN_CONTROLLER_FRONT_OFFSET);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.getClockWise().toYRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-LECTERN_SURFACE_TILT));
        poseStack.translate(0.0D, -0.125D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(LECTERN_MODEL_CLOCKWISE_ROTATION));
        poseStack.scale(LECTERN_CONTROLLER_SCALE, LECTERN_CONTROLLER_SCALE, LECTERN_CONTROLLER_SCALE);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
    }

    // Get the lectern facing
    private static Direction lecternFacing(BlockState state) {
        if (state != null && state.hasProperty(LecternBlock.FACING)) {
            return state.getValue(LecternBlock.FACING);
        }
        return Direction.SOUTH;
    }

}
