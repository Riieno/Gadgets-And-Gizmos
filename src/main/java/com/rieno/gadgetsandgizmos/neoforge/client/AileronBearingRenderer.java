package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

// Draw the Aileron Bearing
public class AileronBearingRenderer extends KineticBlockEntityRenderer<AileronBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing
    public AileronBearingRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the aileron bearing
    @Override
    protected void renderSafe(AileronBearingBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            renderShaft(be, ms, buffer, light);
        }
        renderHeads(be, ms, buffer, light);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the SCM preview partials
    static boolean renderPreview(BlockEntity entity, BlockState state, PoseStack ms,
                                 MultiBufferSource buffer, int light) {
        if (!(entity instanceof AileronBearingBlockEntity bearing)) {
            return false;
        }
        renderShaft(bearing, ms, buffer, light);
        renderHeads(bearing, ms, buffer, light);
        return true;
    }

    // Draw the shaft
    private static void renderShaft(AileronBearingBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        Direction.Axis axis = be.getShaftAxis();
        renderShaftHalf(be, ms, buffer, light, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
        renderShaftHalf(be, ms, buffer, light, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE));
    }

    // Draw the shaft half
    private static void renderShaftHalf(AileronBearingBlockEntity be, PoseStack ms, MultiBufferSource buffer,
                                        int light, Direction dir) {
        BlockState state = be.getBlockState();
        SuperByteBuffer shaft = CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF, state, dir);
        float angle = getAngleForBe(be, be.getBlockPos(), dir.getAxis());
        CTFlywheelVisuals.kineticRotationTransformWhite(shaft, be, dir.getAxis(), angle, light);
        shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    // Draw the bearing heads
    private static void renderHeads(AileronBearingBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        renderHead(be, ms, buffer, light, BearingHead.PRIMARY);
        renderHead(be, ms, buffer, light, BearingHead.SECONDARY);
    }

    // Draw the head
    private static void renderHead(AileronBearingBlockEntity be, PoseStack ms,
                                   MultiBufferSource buffer, int light, BearingHead head) {
        if (be.isMountedAssemblyPresent(head)) {
            return;
        }
        Direction headDirection = be.getHeadDirection(head);
        PartialModel headModel = head == BearingHead.PRIMARY
                ? CTPartialModels.AILERON_BEARING_HEAD_CYAN
                : CTPartialModels.AILERON_BEARING_HEAD_ORANGE;
        SuperByteBuffer plate = CachedBuffers.partial(headModel, be.getBlockState());

        plate.rotateCentered(headRotation(headDirection, 0.0F));
        plate.light(light);
        plate.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    // Get the head rotation
    private static Quaternionf headRotation(Direction dir, float angleRadians) {
        float signedAngle = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? angleRadians
                : -angleRadians;
        Quaternionf orientation = switch (dir) {
            case WEST -> new Quaternionf();
            case EAST -> new Quaternionf().rotateY((float) Math.PI);
            case UP -> new Quaternionf().rotateZ((float) -Math.PI / 2.0F);
            case DOWN -> new Quaternionf().rotateZ((float) Math.PI / 2.0F);
            case NORTH -> new Quaternionf().rotateY((float) -Math.PI / 2.0F);
            case SOUTH -> new Quaternionf().rotateY((float) Math.PI / 2.0F);
        };
        return orientation.mul(new Quaternionf().rotationAxis(signedAngle, -1.0F, 0.0F, 0.0F));
    }
}
