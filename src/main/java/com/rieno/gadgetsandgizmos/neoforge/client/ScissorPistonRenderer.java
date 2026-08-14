package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Draw the Scissor Piston
public class ScissorPistonRenderer extends KineticBlockEntityRenderer<ScissorPistonBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston
    public ScissorPistonRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the scissor piston
    @Override
    protected void renderSafe(ScissorPistonBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            renderShaft(be, ms, buffer, light);
        }

        Direction facing = be.getPistonFacing();
        BlockState state = be.getBlockState();
        Quaternionf baseRotation = rotationFromUp(facing);

        if (!be.isMountedAssemblyPresent()) {
            SuperByteBuffer head = CachedBuffers.partial(CTPartialModels.SCISSOR_PISTON_HEAD, state);
            head.rotateCentered(new Quaternionf(baseRotation));
            head.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the shaft
    private void renderShaft(ScissorPistonBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        Direction shaftDirection = be.getPistonFacing().getOpposite();
        Direction.Axis axis = shaftDirection.getAxis();
        SuperByteBuffer shaft = CachedBuffers.partialFacing(CTPartialModels.SCISSOR_PISTON_SHAFT,
                be.getBlockState(), shaftDirection);
        float angle = getAngleForBe(be, be.getBlockPos(), axis);
        CTFlywheelVisuals.kineticRotationTransformWhite(shaft, be, axis, angle, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    // Get the rotation from up
    private static Quaternionf rotationFromUp(Direction dir) {
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), directionVector(dir));
    }

    // Get the direction vector
    private static Vector3f directionVector(Direction dir) {
        return new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }
}
