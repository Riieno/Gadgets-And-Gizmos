package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AlternatorBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Draw the Alternator
public class AlternatorRenderer extends KineticBlockEntityRenderer<AlternatorBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float VU_PIVOT_X = -0.51F / 16.0F;
    private static final float VU_PIVOT_Y = 6.43F / 16.0F;
    private static final float VU_PIVOT_Z = 7.08F / 16.0F;
    private static final float VU_MIN_DEGREES = 128.0F;
    private static final float VU_MAX_DEGREES = 54.0F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the alternator
    public AlternatorRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the alternator
    @Override
    protected void renderSafe(AlternatorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        RenderType bodyType = getRenderType(be, state);
        CTFlywheelVisuals.renderRotatingBufferWhite(be, getRotatedModel(be, state), ms,
                buffer.getBuffer(bodyType), light);

        Direction facing = state.getValue(BlockStateProperties.FACING);
        Direction.Axis axis = facing.getAxis();
        SuperByteBuffer rotor = CachedBuffers.partialFacing(CTPartialModels.ALTERNATOR_ROTOR, state, facing);
        CTFlywheelVisuals.kineticRotationTransformWhite(rotor, be, axis, getAngleForBe(be, be.getBlockPos(), axis), light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        renderVuNeedle(be, ms, buffer, light, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the vu needle
    private void renderVuNeedle(AlternatorBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        float speed = Math.abs(be.getSpeed());
        float progress = Mth.clamp(speed / 256.0F, 0.0F, 1.0F);
        float angle = AngleHelper.rad(Mth.lerp(progress, VU_MIN_DEGREES, VU_MAX_DEGREES));

        ms.pushPose();
        applyBlockstateFacingTransform(ms, facing);

        SuperByteBuffer needle = CachedBuffers.partial(CTPartialModels.ALTERNATOR_VU_NEEDLE, state);
        needle.translate(VU_PIVOT_X, VU_PIVOT_Y, VU_PIVOT_Z)
                .rotate(angle, Direction.WEST)
                .translateBack(VU_PIVOT_X, VU_PIVOT_Y, VU_PIVOT_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    // Apply the blockstate facing transform
    static void applyBlockstateFacingTransform(PoseStack ms, Direction facing) {

        var stack = TransformStack.of(ms).center();
        if (facing.getAxis().isVertical()) {
            stack.rotateYDegrees(90.0F)
                    .rotateXDegrees(facing == Direction.UP ? 270.0F : 90.0F);

            stack.rotateYDegrees(180.0F)
                    .rotateXDegrees(180.0F);
        } else {
            stack.rotateYDegrees(AngleHelper.horizontalAngle(facing));
        }
        stack.uncenter();
    }

    // Get the rotated model
    @Override
    protected SuperByteBuffer getRotatedModel(AlternatorBlockEntity be, BlockState state) {

        return CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF, state);
    }
}
