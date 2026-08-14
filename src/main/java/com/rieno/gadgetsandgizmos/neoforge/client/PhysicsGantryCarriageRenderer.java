package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.AllPartialModels;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

// Draw the Physics Gantry Carriage
public class PhysicsGantryCarriageRenderer extends KineticBlockEntityRenderer<PhysicsGantryCarriageBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float OUTPUT_SHAFT_PROTRUSION = 0.0625f;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry carriage
    public PhysicsGantryCarriageRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the physics gantry carriage
    @Override
    protected void renderSafe(PhysicsGantryCarriageBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }
        BlockState renderedState = getRenderedBlockState(be);
        CTFlywheelVisuals.renderRotatingBufferWhite(be, getRotatedModel(be, renderedState), ms,
                buffer.getBuffer(getRenderType(be, renderedState)), light);

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(PhysicsGantryCarriageBlock.FACING);
        boolean alongFirst = state.getValue(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);
        Direction.Axis rotationAxis = getRotationAxisOf(be);

        BlockPos visualPos = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? be.getBlockPos()
                : be.getBlockPos().relative(facing.getOpposite());

        float angleForBE = getAngleForBE(be, visualPos, rotationAxis);

        Direction.Axis gantryAxis = Direction.Axis.X;
        for (Direction.Axis axis : Iterate.axes) {
            if (axis == rotationAxis || axis == facing.getAxis()) {
                continue;
            }
            gantryAxis = axis;
        }

        if (gantryAxis == Direction.Axis.X && facing == Direction.UP) {
            angleForBE *= -1.0f;
        }
        if (gantryAxis == Direction.Axis.Y && (facing == Direction.NORTH || facing == Direction.EAST)) {
            angleForBE *= -1.0f;
        }

        SuperByteBuffer cogs = CachedBuffers.partial((PartialModel) CTPartialModels.PHYSICS_GANTRY_COGS, state);
        cogs.center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(facing == Direction.UP ? 0.0f : (facing == Direction.DOWN ? 180.0f : 90.0f))
                .rotateYDegrees(alongFirst ^ facing.getAxis() == Direction.Axis.X ? 0.0f : 90.0f)
                .translate(0.0f, -0.5625f, 0.0f)
                .rotateXDegrees(-angleForBE)
                .translate(0.0f, 0.5625f, 0.0f)
                .uncenter();

        cogs.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));

        Direction mountedDir = PhysicsGantryCarriageBlockEntity.getMountedPayloadDirection(state);
        Direction.Axis mountedAxis = mountedDir.getAxis();
        SuperByteBuffer outputShaft = CachedBuffers.partialFacing(
                (PartialModel) AllPartialModels.SHAFT_HALF, state, mountedDir);

        outputShaft.translate(
            mountedDir.getStepX() * OUTPUT_SHAFT_PROTRUSION,
            mountedDir.getStepY() * OUTPUT_SHAFT_PROTRUSION,
            mountedDir.getStepZ() * OUTPUT_SHAFT_PROTRUSION);
        float shaftAngle = getAngleForBE(be, be.getBlockPos(), mountedAxis) / 180.0f * (float) Math.PI;
        CTFlywheelVisuals.kineticRotationTransformWhite(outputShaft, be, mountedAxis, shaftAngle, light);
        outputShaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the rendered angle for a block entity
    public static float getAngleForBE(KineticBlockEntity be, BlockPos pos, Direction.Axis axis) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, pos, axis);
        return (time * be.getSpeed() * 3.0f / 20.0f + offset) % 360.0f;
    }

    // Get the rendered block state
    @Override
    protected BlockState getRenderedBlockState(PhysicsGantryCarriageBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}
