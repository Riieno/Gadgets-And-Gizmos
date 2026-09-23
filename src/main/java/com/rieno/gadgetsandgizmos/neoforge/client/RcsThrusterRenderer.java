package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Draw the RCS Thruster
public class RcsThrusterRenderer extends KineticBlockEntityRenderer<RcsThrusterBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float AXIS_OUTWARD_OFFSET = 0.5F / 16.0F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS thruster
    public RcsThrusterRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the RCS thruster
    @Override
    protected void renderSafe(
            RcsThrusterBlockEntity blockEntity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay
    ) {
        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }

        renderPreview(blockEntity, getRenderedBlockState(blockEntity), poseStack, buffer, light);
    }

    // Draw the RCS shaft in an off-screen SCM preview without its static housing.
    static boolean renderPreview(
            BlockEntity entity,
            BlockState state,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light
    ) {
        if (!(entity instanceof RcsThrusterBlockEntity blockEntity) || state == null) {
            return false;
        }
        Direction facing = state.getValue(BlockStateProperties.FACING);
        Direction shaftFacing = facing.getOpposite();
        Direction.Axis axis = shaftFacing.getAxis();
        SuperByteBuffer shaft = CachedBuffers.partialFacing(
                        CTPartialModels.RCS_AXIS, state, facing)
                .translate(
                        shaftFacing.getStepX() * AXIS_OUTWARD_OFFSET,
                        shaftFacing.getStepY() * AXIS_OUTWARD_OFFSET,
                        shaftFacing.getStepZ() * AXIS_OUTWARD_OFFSET);
                CTFlywheelVisuals.kineticRotationTransformWhite(
                        shaft, blockEntity, axis,
                        getAngleForBe(blockEntity, blockEntity.getBlockPos(), axis), light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
        return true;
    }
}
