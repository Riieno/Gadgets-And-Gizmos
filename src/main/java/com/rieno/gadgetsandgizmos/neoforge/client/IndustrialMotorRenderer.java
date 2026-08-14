package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
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

// Draw the Industrial Motor
public class IndustrialMotorRenderer extends WhiteKineticBlockEntityRenderer<IndustrialMotorBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float STRESS_PIVOT_X = 15.39F / 16.0F;
    private static final float STRESS_PIVOT_Y = 5.77F / 16.0F;
    private static final float STRESS_PIVOT_Z = 7.30F / 16.0F;
    private static final float STRESS_MIN_DEGREES = 54.0F;
    private static final float STRESS_MAX_DEGREES = -54.0F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the industrial motor
    public IndustrialMotorRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the industrial motor
    @Override
    protected void renderSafe(IndustrialMotorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = getRenderedBlockState(be);
        RenderType type = getRenderType(be, state);
        CTFlywheelVisuals.renderRotatingBufferWhite(be, getRotatedModel(be, state), ms, buffer.getBuffer(type), light);

        Direction facing = state.getValue(BlockStateProperties.FACING);
        Direction.Axis axis = facing.getAxis();
        SuperByteBuffer rotor = CachedBuffers.partialFacing(CTPartialModels.ALTERNATOR_ROTOR, state, facing);
        CTFlywheelVisuals.kineticRotationTransformWhite(rotor, be, axis, getAngleForBe(be, be.getBlockPos(), axis), light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        renderStressNeedle(be, ms, buffer, light, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the stress needle
    private void renderStressNeedle(IndustrialMotorBlockEntity be, PoseStack ms, MultiBufferSource buffer,
                                    int light, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        float progress = Mth.clamp(be.getStressRatio(), 0.0F, 1.125F);
        float angle = AngleHelper.rad(Mth.lerp(progress / 1.125F, STRESS_MIN_DEGREES, STRESS_MAX_DEGREES));

        ms.pushPose();
        AlternatorRenderer.applyBlockstateFacingTransform(ms, facing);

        SuperByteBuffer needle = CachedBuffers.partial(CTPartialModels.INDUSTRIAL_MOTOR_STRESS_NEEDLE, state);
        needle.translate(STRESS_PIVOT_X, STRESS_PIVOT_Y, STRESS_PIVOT_Z)
                .rotate(angle, Direction.EAST)
                .translateBack(STRESS_PIVOT_X, STRESS_PIVOT_Y, STRESS_PIVOT_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    // Get the rotated model
    @Override
    protected SuperByteBuffer getRotatedModel(IndustrialMotorBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF, state);
    }
}
